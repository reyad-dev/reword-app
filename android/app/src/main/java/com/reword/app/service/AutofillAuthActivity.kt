package com.reword.app.service

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.gson.Gson
import com.reword.app.R
import com.reword.app.crypto.CryptoEngine
import com.reword.app.crypto.KeystoreHelper
import com.reword.app.crypto.SecureStorage
import com.reword.app.model.Account
import com.reword.app.model.Site
import com.reword.app.model.Vault
import java.net.URI

/**
 * Trampoline Activity to prompt for Biometric Authentication or Master Password
 * when an Autofill request is triggered while the vault is locked.
 */
class AutofillAuthActivity : FragmentActivity() {

    companion object {
        const val EXTRA_DOMAIN = "extra_domain"
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_USER_FIELD = "extra_user_field"
        const val EXTRA_PASS_FIELD = "extra_pass_field"
    }

    private lateinit var storage: SecureStorage
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = SecureStorage(this)

        if (storage.isBiometricEnabled && storage.getBiometricIv() != null) {
            triggerBiometricPrompt()
        } else {
            showMasterPasswordDialog()
        }
    }

    private fun triggerBiometricPrompt() {
        val iv = storage.getBiometricIv()
        val encryptedPassBytes = storage.getBiometricEncryptedPassword()

        if (iv == null || encryptedPassBytes == null) {
            showMasterPasswordDialog()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setNegativeButtonText(getString(R.string.biometric_prompt_cancel))
            .build()

        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                try {
                    val cipher = result.cryptoObject?.cipher
                    val decryptedPassBytes = cipher?.doFinal(encryptedPassBytes)
                    val masterPassword = decryptedPassBytes?.let { String(it, Charsets.UTF_8) }

                    if (!masterPassword.isNullOrBlank()) {
                        unlockAndReturnDataset(masterPassword)
                    } else {
                        showMasterPasswordDialog()
                    }
                } catch (_: Exception) {
                    showMasterPasswordDialog()
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                } else {
                    showMasterPasswordDialog()
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Toast.makeText(this@AutofillAuthActivity, "فشل التحقق من البصمة", Toast.LENGTH_SHORT).show()
            }
        })

        try {
            val cryptoObject = KeystoreHelper.createDecryptCryptoObject(iv)
            biometricPrompt.authenticate(promptInfo, cryptoObject)
        } catch (_: Exception) {
            showMasterPasswordDialog()
        }
    }

    private fun showMasterPasswordDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.master_password_label)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val container = FrameLayout(this).apply {
            val paddingPx = (18 * resources.displayMetrics.density).toInt()
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage("أدخل كلمة المرور الرئيسية لإلغاء القفل")
            .setView(container)
            .setPositiveButton("فتح") { _, _ ->
                val pwd = input.text?.toString().orEmpty()
                if (pwd.isNotBlank()) {
                    unlockAndReturnDataset(pwd)
                } else {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
            .setNegativeButton(R.string.biometric_prompt_cancel) { _, _ ->
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            .setOnCancelListener {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            .show()
    }

    private fun unlockAndReturnDataset(password: String) {
        val blob = storage.vaultBlob
        if (blob == null) {
            Toast.makeText(this, "الخزينة فارغة", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        try {
            val json = CryptoEngine.decrypt(blob, password)
            val vault = gson.fromJson(json, Vault::class.java)
            SecureStorage.setSession(vault, password)

            val domain = intent.getStringExtra(EXTRA_DOMAIN)
            val pkg = intent.getStringExtra(EXTRA_PACKAGE)

            val userField: AutofillId? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_USER_FIELD, AutofillId::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_USER_FIELD) as? AutofillId
            }

            val passField: AutofillId? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_PASS_FIELD, AutofillId::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_PASS_FIELD) as? AutofillId
            }

            val match = findBestAccount(vault, domain, pkg)
            if (match != null && (userField != null || passField != null)) {
                returnDataset(match.first, match.second, userField, passField)
            } else if (vault.sites.isNotEmpty()) {
                showAccountChooser(vault, userField, passField)
            } else {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "كلمة المرور غير صحيحة", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    private fun showAccountChooser(
        vault: Vault,
        userField: AutofillId?,
        passField: AutofillId?
    ) {
        val items = mutableListOf<Pair<Site, Account>>()
        val displayLabels = mutableListOf<String>()

        for (site in vault.sites) {
            for (acc in site.accounts) {
                items.add(Pair(site, acc))
                val title = site.name.ifBlank { site.url }
                displayLabels.add("$title (${acc.user})")
            }
        }

        if (items.isEmpty()) {
            Toast.makeText(this, "لا توجد حسابات مسجلة في الخزينة", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("اختر الحساب للملء التلقائي")
            .setItems(displayLabels.toTypedArray()) { _, which ->
                val selected = items[which]
                returnDataset(selected.first, selected.second, userField, passField)
            }
            .setNegativeButton(R.string.biometric_prompt_cancel) { _, _ ->
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            .setOnCancelListener {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            .show()
    }

    private fun returnDataset(
        site: Site,
        acc: Account,
        userField: AutofillId?,
        passField: AutofillId?
    ) {
        val presentation = RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
            setTextViewText(R.id.autofill_title, site.name.ifBlank { site.url })
            setTextViewText(R.id.autofill_subtitle, acc.user)
        }

        val builder = Dataset.Builder(presentation)
        userField?.let { builder.setValue(it, AutofillValue.forText(acc.user), presentation) }
        passField?.let { builder.setValue(it, AutofillValue.forText(acc.pass), presentation) }

        val replyIntent = Intent().apply {
            putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, builder.build())
        }
        setResult(Activity.RESULT_OK, replyIntent)
        finish()
    }

    private fun findBestAccount(vault: Vault, domain: String?, pkg: String?): Pair<Site, Account>? {
        val cleanDomain = domain?.let { extractHost(it) }?.lowercase()
        val cleanPkg = pkg?.lowercase()

        for (site in vault.sites) {
            val siteHost = extractHost(site.url).lowercase()
            val siteName = site.name.lowercase()

            val matchesDomain = cleanDomain != null && siteHost.isNotEmpty() &&
                    (cleanDomain.contains(siteHost) || siteHost.contains(cleanDomain))
            val matchesPkg = cleanPkg != null &&
                    ((siteName.isNotEmpty() && cleanPkg.contains(siteName)) ||
                            (siteHost.isNotEmpty() && cleanPkg.contains(siteHost.split(".").firstOrNull() ?: "")))

            if (matchesDomain || matchesPkg) {
                val acc = site.accounts.firstOrNull()
                if (acc != null) return Pair(site, acc)
            }
        }
        return null
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
}
