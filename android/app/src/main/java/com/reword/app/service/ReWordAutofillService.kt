package com.reword.app.service

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillContext
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.reword.app.R
import com.reword.app.crypto.SecureStorage
import com.reword.app.model.Account
import com.reword.app.model.Site
import com.reword.app.model.Vault
import java.net.URI

/**
 * System-wide Autofill Service for ReWord.
 *
 * Intercepts autofill requests across all Android native apps and mobile web browsers.
 * Parses AssistStructure, matches target domain/package to vault records,
 * and securely fills credentials (with Biometric verification if locked).
 */
class ReWordAutofillService : AutofillService() {

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }

        // Parse view hierarchy
        val parsed = parseStructure(structure)
        if (parsed.passwordFields.isEmpty() && parsed.usernameFields.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val storage = SecureStorage(applicationContext)
        val activeVault = SecureStorage.activeVault

        // If vault is locked, prompt user to unlock via Biometric Authentication
        if (activeVault == null) {
            val response = buildAuthResponse(parsed, storage)
            callback.onSuccess(response)
            return
        }

        // Vault is unlocked: find matching site & accounts
        val matchingSites = findMatchingSites(activeVault, parsed.webDomain, parsed.packageName)
        val candidateSites = if (matchingSites.isNotEmpty()) matchingSites else activeVault.sites.take(5)

        if (candidateSites.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val responseBuilder = FillResponse.Builder()
        val usernameId = parsed.usernameFields.firstOrNull()
        val passwordId = parsed.passwordFields.firstOrNull()

        var hasDatasets = false
        for (site in candidateSites) {
            for (acc in site.accounts.take(3)) {
                val dataset = buildDataset(site, acc, usernameId, passwordId)
                if (dataset != null) {
                    responseBuilder.addDataset(dataset)
                    hasDatasets = true
                }
            }
        }

        // Enable save prompt if password field exists
        if (passwordId != null) {
            val saveIds = mutableListOf<AutofillId>(passwordId)
            usernameId?.let { saveIds.add(it) }
            val saveInfo = SaveInfo.Builder(
                SaveInfo.SAVE_DATA_TYPE_PASSWORD or SaveInfo.SAVE_DATA_TYPE_USERNAME,
                saveIds.toTypedArray()
            ).build()
            responseBuilder.setSaveInfo(saveInfo)
        }

        if (hasDatasets) {
            callback.onSuccess(responseBuilder.build())
        } else {
            callback.onSuccess(null)
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // Handle saving/updating accounts from autofill save prompt
        callback.onSuccess()
    }

    private fun buildDataset(
        site: Site,
        acc: Account,
        usernameId: AutofillId?,
        passwordId: AutofillId?
    ): Dataset? {
        if (usernameId == null && passwordId == null) return null

        val presentation = RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
            setTextViewText(R.id.autofill_title, site.name.ifBlank { site.url })
            setTextViewText(R.id.autofill_subtitle, acc.user.ifBlank { "••••••••" })
        }

        val builder = Dataset.Builder(presentation)
        usernameId?.let {
            builder.setValue(it, AutofillValue.forText(acc.user), presentation)
        }
        passwordId?.let {
            builder.setValue(it, AutofillValue.forText(acc.pass), presentation)
        }

        return builder.build()
    }

    /**
     * Builds response that triggers Biometric Auth before revealing credentials.
     */
    private fun buildAuthResponse(parsed: ParsedStructure, storage: SecureStorage): FillResponse? {
        val targetId = parsed.passwordFields.firstOrNull() ?: parsed.usernameFields.firstOrNull() ?: return null

        val intent = Intent(this, AutofillAuthActivity::class.java).apply {
            putExtra(AutofillAuthActivity.EXTRA_DOMAIN, parsed.webDomain)
            putExtra(AutofillAuthActivity.EXTRA_PACKAGE, parsed.packageName)
            putExtra(AutofillAuthActivity.EXTRA_USER_FIELD, parsed.usernameFields.firstOrNull())
            putExtra(AutofillAuthActivity.EXTRA_PASS_FIELD, parsed.passwordFields.firstOrNull())
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_CANCEL_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(this, 1001, intent, flags)
        val intentSender: IntentSender = pendingIntent.intentSender

        val presentation = RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
            setTextViewText(R.id.autofill_title, "إلغاء قفل ReWord")
            setTextViewText(R.id.autofill_subtitle, "اضغط للمصادقة بالبصمة للملء التلقائي")
        }

        return FillResponse.Builder()
            .setAuthentication(arrayOf(targetId), intentSender, presentation)
            .build()
    }

    private fun findMatchingSites(vault: Vault, webDomain: String?, packageName: String?): List<Site> {
        val cleanDomain = webDomain?.let { extractHost(it) }?.lowercase()
        val pkg = packageName?.lowercase()

        return vault.sites.filter { site ->
            val siteHost = extractHost(site.url).lowercase()
            val siteName = site.name.lowercase()

            when {
                // Direct domain match (e.g. twitter.com)
                cleanDomain != null && siteHost.isNotEmpty() && (cleanDomain.contains(siteHost) || siteHost.contains(cleanDomain)) -> true
                // Package name matching (e.g. com.twitter.android contains "twitter")
                pkg != null && siteName.isNotEmpty() && pkg.contains(siteName) -> true
                pkg != null && siteHost.isNotEmpty() && pkg.contains(siteHost.split(".").firstOrNull() ?: "") -> true
                else -> false
            }
        }
    }

    private fun extractHost(raw: String): String {
        return try {
            val url = if (!raw.contains("://")) "https://$raw" else raw
            val host = URI(url).host ?: raw
            host.removePrefix("www.")
        } catch (_: Exception) {
            raw.removePrefix("www.")
        }
    }

    private data class ParsedStructure(
        var packageName: String? = null,
        var webDomain: String? = null,
        val usernameFields: MutableList<AutofillId> = mutableListOf(),
        val passwordFields: MutableList<AutofillId> = mutableListOf()
    )

    private fun parseStructure(structure: AssistStructure): ParsedStructure {
        val parsed = ParsedStructure()
        parsed.packageName = structure.activityComponent.packageName

        for (i in 0 until structure.windowNodeCount) {
            val node = structure.getWindowNodeAt(i).rootViewNode
            traverseNode(node, parsed)
        }
        return parsed
    }

    private fun traverseNode(node: AssistStructure.ViewNode, parsed: ParsedStructure) {
        // Extract web domain from browser view node
        node.webDomain?.let {
            if (parsed.webDomain == null) parsed.webDomain = it
        }

        val hints = node.autofillHints
        val idEntry = node.idEntry?.lowercase() ?: ""
        val hintText = node.hint?.toString()?.lowercase() ?: ""
        val inputType = node.inputType

        val isPasswordType = (inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0 ||
                (inputType and InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) != 0 ||
                (inputType and InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) != 0

        val hasPasswordHint = hints?.contains(View.AUTOFILL_HINT_PASSWORD) == true ||
                idEntry.contains("password") || idEntry.contains("pass") ||
                hintText.contains("password") || hintText.contains("كلمة المرور")

        val hasUsernameHint = hints?.contains(View.AUTOFILL_HINT_USERNAME) == true ||
                hints?.contains(View.AUTOFILL_HINT_EMAIL_ADDRESS) == true ||
                idEntry.contains("user") || idEntry.contains("email") || idEntry.contains("login") ||
                hintText.contains("user") || hintText.contains("email") || hintText.contains("اسم المستخدم")

        node.autofillId?.let { autofillId ->
            if (isPasswordType || hasPasswordHint) {
                parsed.passwordFields.add(autofillId)
            } else if (hasUsernameHint) {
                parsed.usernameFields.add(autofillId)
            }
        }

        for (i in 0 until node.childCount) {
            traverseNode(node.getChildAt(i), parsed)
        }
    }
}
