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
        val targetUser = parsed.usernameFields.firstOrNull()
        val targetPass = parsed.passwordFields.firstOrNull()

        // ONLY trigger if at least one targetUser (email/username) or targetPass (password) is found
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
        val candidateSites = when {
            matchingSites.isNotEmpty() -> matchingSites
            targetPass != null -> activeVault.sites.take(5) // Only suggest vault accounts when a password field is present
            else -> emptyList()
        }

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
        if (targetIds.isEmpty()) return null

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

        // Heuristic fallback ONLY when an actual password field exists on the screen:
        // In standard login forms, any valid non-password editable field before the password is the username/email.
        if (parsed.passwordFields.isNotEmpty() && parsed.usernameFields.isEmpty()) {
            val firstPasswordId = parsed.passwordFields.first()
            val passIndex = parsed.allEditableFields.indexOf(firstPasswordId)
            if (passIndex > 0) {
                parsed.usernameFields.add(parsed.allEditableFields[passIndex - 1])
            }
        }

        // Solitary editable fields without credentials are never treated as usernames (prevents WhatsApp/chat popups).
        return parsed
    }

    private fun traverseNode(node: AssistStructure.ViewNode, parsed: ParsedStructure) {
        // Extract web domain from browser view node
        node.webDomain?.let {
            if (parsed.webDomain == null) parsed.webDomain = it
        }

        val autofillId = node.autofillId
        val hints = node.autofillHints?.map { it.lowercase().trim() } ?: emptyList()
        val idEntry = node.idEntry?.lowercase().orEmpty()
        val hintText = (node.hint?.toString() ?: "").lowercase()
        val text = (node.text?.toString() ?: "").lowercase()
        val contentDesc = (node.contentDescription?.toString() ?: "").lowercase()
        val className = node.className.orEmpty()
        val inputType = node.inputType

        val classType = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val isMultiLine = (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0

        val combinedText = "$idEntry $hintText $text $contentDesc"

        // Chat / messaging / search exclusions (WhatsApp, Telegram, Notes, Search bars, etc.)
        val isMessageVariation = variation in listOf(
            InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
            InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE,
            InputType.TYPE_TEXT_VARIATION_FILTER
        )

        val hasNegativeKeyword = listOf(
            "message", "chat", "msg", "comment", "search", "query", "filter", "compose", "tweet", "post",
            "رسالة", "رساله", "دردشة", "دردشه", "بحث", "تعليق", "منشور", "تغريدة"
        ).any { combinedText.contains(it) }

        // 1. Password detection
        val isTextPasswordVariation = classType == InputType.TYPE_CLASS_TEXT && variation in listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
        val isNumericPassword = classType == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD

        val hasPasswordHint = hints.any { h ->
            h.contains("password") || h.contains("pwd")
        } || listOf("password", "passwd", "pwd", "passcode", "secret", "pin", "كلمة المرور", "كلمه المرور", "كلمة_المرور", "السر", "رمز المرور", "رمز_المرور").any { combinedText.contains(it) }

        val isPassword = isTextPasswordVariation || isNumericPassword || hasPasswordHint

        // 2. Email detection
        val isEmailVariation = classType == InputType.TYPE_CLASS_TEXT && variation in listOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        )
        val hasEmailHint = hints.any { h ->
            h.contains("email") || h.contains("emailaddress")
        } || listOf("email", "e-mail", "mail", "بريد", "إلكتروني", "الكتروني", "البريد").any { combinedText.contains(it) }

        val isEmail = (isEmailVariation || hasEmailHint) && !isMultiLine && !isMessageVariation && !hasNegativeKeyword

        // 3. Username detection
        val hasUsernameHint = hints.any { h ->
            h.contains("username") || h.contains("login")
        } || listOf("username", "user_name", "user-name", "userid", "user_id", "login", "اسم المستخدم", "اسم_المستخدم", "اسم مستخدم", "المستخدم").any { combinedText.contains(it) }

        val isUsername = hasUsernameHint && !isMultiLine && !isMessageVariation && !hasNegativeKeyword

        // General single-line editable candidate for login form heuristic (strictly non-message / non-chat)
        val isCandidateField = (autofillId != null) && (
            className.contains("EditText", ignoreCase = true) ||
            className.contains("TextInput", ignoreCase = true) ||
            classType == InputType.TYPE_CLASS_TEXT
        ) && !isMultiLine && !isMessageVariation && !hasNegativeKeyword

        if (autofillId != null) {
            if (isPassword) {
                if (!parsed.passwordFields.contains(autofillId)) {
                    parsed.passwordFields.add(autofillId)
                }
                if (!parsed.allEditableFields.contains(autofillId)) {
                    parsed.allEditableFields.add(autofillId)
                }
            } else if (isEmail || isUsername) {
                if (!parsed.usernameFields.contains(autofillId)) {
                    parsed.usernameFields.add(autofillId)
                }
                if (!parsed.allEditableFields.contains(autofillId)) {
                    parsed.allEditableFields.add(autofillId)
                }
            } else if (isCandidateField) {
                if (!parsed.allEditableFields.contains(autofillId)) {
                    parsed.allEditableFields.add(autofillId)
                }
            }
        }

        for (i in 0 until node.childCount) {
            traverseNode(node.getChildAt(i), parsed)
        }
    }
}
