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
        val targetUser = parsed.usernameFields.firstOrNull() ?: parsed.allEditableFields.firstOrNull()
        val targetPass = parsed.passwordFields.firstOrNull() ?: parsed.allEditableFields.getOrNull(1)

        if (targetUser == null && targetPass == null) {
            callback.onSuccess(null)
            return
        }

        val storage = SecureStorage(applicationContext)
        val activeVault = SecureStorage.activeVault

        // If vault is locked, prompt user to unlock via Biometric Authentication
        if (activeVault == null) {
            val response = buildAuthResponse(parsed, storage, request)
            callback.onSuccess(response)
            return
        }

        // Vault is unlocked: find matching site & accounts
        val matchingSites = findMatchingSites(activeVault, parsed.webDomain, parsed.packageName)
        val candidateSites = if (matchingSites.isNotEmpty()) matchingSites else activeVault.sites.take(10)

        if (candidateSites.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val responseBuilder = FillResponse.Builder()
        var hasDatasets = false
        for (site in candidateSites) {
            for (acc in site.accounts.take(3)) {
                val dataset = buildDataset(site, acc, targetUser, targetPass, request)
                if (dataset != null) {
                    responseBuilder.addDataset(dataset)
                    hasDatasets = true
                }
            }
        }

        // Enable save prompt if password field exists
        val saveIds = mutableListOf<AutofillId>()
        targetPass?.let { saveIds.add(it) }
        targetUser?.let { saveIds.add(it) }
        if (saveIds.isNotEmpty()) {
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
        passwordId: AutofillId?,
        request: FillRequest
    ): Dataset? {
        if (usernameId == null && passwordId == null) return null

        val presentation = RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
            setTextViewText(R.id.autofill_title, site.name.ifBlank { site.url })
            setTextViewText(R.id.autofill_subtitle, acc.user.ifBlank { "••••••••" })
        }

        val inlinePresentation = createInlinePresentation(
            request,
            site.name.ifBlank { site.url },
            acc.user.ifBlank { "حساب ReWord" }
        )

        val builder = Dataset.Builder(presentation)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlinePresentation != null) {
            builder.setInlinePresentation(inlinePresentation)
            usernameId?.let {
                builder.setValue(it, AutofillValue.forText(acc.user), presentation, inlinePresentation)
            }
            passwordId?.let {
                builder.setValue(it, AutofillValue.forText(acc.pass), presentation, inlinePresentation)
            }
        } else {
            usernameId?.let {
                builder.setValue(it, AutofillValue.forText(acc.user), presentation)
            }
            passwordId?.let {
                builder.setValue(it, AutofillValue.forText(acc.pass), presentation)
            }
        }

        return builder.build()
    }

    private fun createInlinePresentation(
        request: FillRequest,
        title: String,
        subtitle: String
    ): android.service.autofill.InlinePresentation? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val inlineRequest = request.inlineSuggestionsRequest ?: return null
        val spec = inlineRequest.inlinePresentationSpecs.firstOrNull() ?: return null

        return try {
            val emptyIntent = Intent()
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val pendingIntent = PendingIntent.getActivity(this, 0, emptyIntent, flags)

            val slice = androidx.autofill.inline.v1.InlineSuggestionUi.newContentBuilder(pendingIntent)
                .setContentDescription("$title - $subtitle")
                .setTitle(title)
                .setSubtitle(subtitle)
                .build()
                .slice
            android.service.autofill.InlinePresentation(slice, spec, false)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Builds response that triggers Biometric Auth before revealing credentials.
     */
    private fun buildAuthResponse(
        parsed: ParsedStructure,
        storage: SecureStorage,
        request: FillRequest
    ): FillResponse? {
        val targetIds = mutableListOf<AutofillId>()
        parsed.usernameFields.firstOrNull()?.let { targetIds.add(it) }
        parsed.passwordFields.firstOrNull()?.let { targetIds.add(it) }
        if (targetIds.isEmpty()) {
            parsed.allEditableFields.firstOrNull()?.let { targetIds.add(it) }
        }
        if (targetIds.isEmpty()) return null

        val intent = Intent(this, AutofillAuthActivity::class.java).apply {
            putExtra(AutofillAuthActivity.EXTRA_DOMAIN, parsed.webDomain)
            putExtra(AutofillAuthActivity.EXTRA_PACKAGE, parsed.packageName)
            putExtra(AutofillAuthActivity.EXTRA_USER_FIELD, parsed.usernameFields.firstOrNull() ?: parsed.allEditableFields.firstOrNull())
            putExtra(AutofillAuthActivity.EXTRA_PASS_FIELD, parsed.passwordFields.firstOrNull() ?: parsed.allEditableFields.getOrNull(1))
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

        val responseBuilder = FillResponse.Builder()
        val inlinePresentation = createInlinePresentation(
            request,
            "إلغاء قفل ReWord",
            "المصادقة للملء التلقائي"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlinePresentation != null) {
            responseBuilder.setAuthentication(targetIds.toTypedArray(), intentSender, presentation, inlinePresentation)
        } else {
            responseBuilder.setAuthentication(targetIds.toTypedArray(), intentSender, presentation)
        }

        return responseBuilder.build()
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
                pkg != null && siteName.isNotEmpty() && (pkg.contains(siteName) || siteName.contains(pkg)) -> true
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
        val passwordFields: MutableList<AutofillId> = mutableListOf(),
        val allEditableFields: MutableList<AutofillId> = mutableListOf()
    )

    private fun parseStructure(structure: AssistStructure): ParsedStructure {
        val parsed = ParsedStructure()
        parsed.packageName = structure.activityComponent?.packageName

        for (i in 0 until structure.windowNodeCount) {
            val node = structure.getWindowNodeAt(i).rootViewNode
            traverseNode(node, parsed)
        }

        // Heuristic fallback:
        // If password is found, but username wasn't explicitly labeled:
        // Any editable field before the password is the username/email field!
        if (parsed.passwordFields.isNotEmpty() && parsed.usernameFields.isEmpty()) {
            val firstPasswordId = parsed.passwordFields.first()
            val passIndex = parsed.allEditableFields.indexOf(firstPasswordId)
            if (passIndex > 0) {
                parsed.usernameFields.add(parsed.allEditableFields[passIndex - 1])
            } else if (parsed.allEditableFields.size > 1) {
                val nonPass = parsed.allEditableFields.firstOrNull { it != firstPasswordId }
                if (nonPass != null) parsed.usernameFields.add(nonPass)
            }
        }

        // If only 1 editable field on the screen and neither was flagged:
        if (parsed.usernameFields.isEmpty() && parsed.passwordFields.isEmpty() && parsed.allEditableFields.isNotEmpty()) {
            parsed.usernameFields.add(parsed.allEditableFields.first())
        }

        return parsed
    }

    private fun traverseNode(node: AssistStructure.ViewNode, parsed: ParsedStructure) {
        // Extract web domain from browser view node
        node.webDomain?.let {
            if (parsed.webDomain == null) parsed.webDomain = it
        }

        val autofillId = node.autofillId
        val hints = node.autofillHints?.map { it.lowercase() } ?: emptyList()
        val idEntry = node.idEntry?.lowercase().orEmpty()
        val hintText = (node.hint?.toString() ?: "").lowercase()
        val text = (node.text?.toString() ?: "").lowercase()
        val contentDesc = (node.contentDescription?.toString() ?: "").lowercase()
        val className = node.className.orEmpty()
        val inputType = node.inputType

        val combinedText = "$idEntry $hintText $text $contentDesc"

        val isEditable = (autofillId != null) && (
            className.contains("EditText", ignoreCase = true) ||
            className.contains("TextInput", ignoreCase = true) ||
            (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT ||
            node.isFocused
        )

        if (isEditable && autofillId != null) {
            if (!parsed.allEditableFields.contains(autofillId)) {
                parsed.allEditableFields.add(autofillId)
            }
        }

        // Password detection
        val isPasswordClass = (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
        val isPasswordVariation = (inputType and InputType.TYPE_MASK_VARIATION) in listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
        val isNumericPassword = (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER &&
            (inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_NUMBER_VARIATION_PASSWORD

        val hasPasswordHint = hints.any { it.contains("password") } ||
            listOf("password", "pass", "pwd", "pin", "مرور", "السر", "سر").any { combinedText.contains(it) }

        val isPassword = (isPasswordClass && isPasswordVariation) || isNumericPassword || hasPasswordHint

        // Username / Email detection
        val isEmailVariation = (inputType and InputType.TYPE_MASK_VARIATION) in listOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        )
        val isPersonNameVariation = (inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PERSON_NAME

        val hasUsernameHint = hints.any { it.contains("user") || it.contains("email") || it.contains("login") } ||
            listOf(
                "user", "username", "email", "mail", "login", "account", "phone",
                "مستخدم", "اسم المستخدم", "بريد", "إلكتروني", "الكتروني", "حساب", "تسجيل", "هاتف", "جوال"
            ).any { combinedText.contains(it) }

        val isUsername = isEmailVariation || isPersonNameVariation || hasUsernameHint

        if (autofillId != null) {
            if (isPassword) {
                if (!parsed.passwordFields.contains(autofillId)) {
                    parsed.passwordFields.add(autofillId)
                }
            } else if (isUsername) {
                if (!parsed.usernameFields.contains(autofillId)) {
                    parsed.usernameFields.add(autofillId)
                }
            }
        }

        for (i in 0 until node.childCount) {
            traverseNode(node.getChildAt(i), parsed)
        }
    }
}
