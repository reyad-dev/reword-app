package com.reword.app.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.gson.Gson
import com.reword.app.crypto.CryptoEngine
import com.reword.app.crypto.KeystoreHelper
import com.reword.app.crypto.SecureStorage
import com.reword.app.model.Account
import com.reword.app.model.Site
import com.reword.app.model.Vault
import com.reword.app.ui.components.SiteFavicon
import com.reword.app.ui.components.extractFaviconDomain
import com.reword.app.ui.theme.*

class FloatingPickerActivity : FragmentActivity() {

    private lateinit var storage: SecureStorage
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = SecureStorage(this)

        setContent {
            ReWordTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.65f))
                            .clickable { finish() },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.85f)
                                .clickable(enabled = false) {}, // Prevent dismiss when tapping inside
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                            color = BgDark,
                            border = BorderStroke(1.dp, BorderDark)
                        ) {
                            FloatingPickerContent()
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun FloatingPickerContent() {
        var vault by remember { mutableStateOf(SecureStorage.activeVault) }
        var isUnlocked by remember { mutableStateOf(vault != null) }
        var searchQuery by remember { mutableStateOf("") }
        var masterPassInput by remember { mutableStateOf("") }
        var authError by remember { mutableStateOf<String?>(null) }

        // If vault is locked, try biometric unlock immediately
        LaunchedEffect(Unit) {
            if (!isUnlocked && storage.isBiometricEnabled && storage.getBiometricIv() != null) {
                attemptBiometricUnlock { unlockedVault ->
                    vault = unlockedVault
                    isUnlocked = true
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(44.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BorderDark)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(listOf(PrimaryViolet, AccentCyan))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "ReWord - الملء السريع",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "انقر لنسخ اسم المستخدم أو كلمة المرور",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                IconButton(onClick = { finish() }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isUnlocked) {
                // Locked Vault State
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, BorderDark, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "الخزينة مقفلة",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "أدخل كلمة المرور الرئيسية أو استخدم البصمة لفك القفل",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                            )

                            OutlinedTextField(
                                value = masterPassInput,
                                onValueChange = { masterPassInput = it; authError = null },
                                placeholder = { Text("كلمة المرور الرئيسية...", color = TextMuted) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentCyan,
                                    unfocusedBorderColor = BorderDark,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedContainerColor = SurfaceDark,
                                    unfocusedContainerColor = SurfaceDark
                                )
                            )

                            if (authError != null) {
                                Text(
                                    text = authError.orEmpty(),
                                    color = ErrorRose,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (storage.isBiometricEnabled) {
                                    OutlinedButton(
                                        onClick = {
                                            attemptBiometricUnlock { unlockedVault ->
                                                vault = unlockedVault
                                                isUnlocked = true
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, AccentCyan)
                                    ) {
                                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = AccentCyan)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("بصمة", color = AccentCyan)
                                    }
                                }

                                Button(
                                    onClick = {
                                        val blob = storage.vaultBlob
                                        if (blob == null) {
                                            authError = "الخزينة فارغة، يرجى المزامنة أولاً"
                                            return@Button
                                        }
                                        try {
                                            val json = CryptoEngine.decrypt(blob, masterPassInput)
                                            val parsedVault = gson.fromJson(json, Vault::class.java)
                                            SecureStorage.setSession(parsedVault, masterPassInput)
                                            vault = parsedVault
                                            isUnlocked = true
                                        } catch (_: Exception) {
                                            authError = "كلمة المرور الرئيسية غير صحيحة"
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryViolet)
                                ) {
                                    Text("فتح الخزينة", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                // Unlocked Vault Picker
                val safeVault = vault ?: Vault()
                val currentPackage = remember { ReWordAccessibilityService.currentPackage?.lowercase() }

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("بحث في المواقع والحسابات...", color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AccentCyan) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "مسح", tint = TextMuted)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceCard,
                        unfocusedContainerColor = SurfaceCard
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                val filteredSites = remember(safeVault, searchQuery, currentPackage) {
                    if (searchQuery.isNotBlank()) {
                        val q = searchQuery.trim().lowercase()
                        safeVault.sites.filter { site ->
                            site.name.lowercase().contains(q) ||
                                    site.url.lowercase().contains(q) ||
                                    site.accounts.any { it.user.lowercase().contains(q) }
                        }
                    } else if (!currentPackage.isNullOrBlank()) {
                        // Priority 1: Match package name or domain
                        val detected = safeVault.sites.filter { site ->
                            val host = extractFaviconDomain(site.url, site.name).lowercase()
                            val name = site.name.lowercase()
                            currentPackage.contains(name) || (host.isNotEmpty() && currentPackage.contains(host.split(".").firstOrNull().orEmpty()))
                        }
                        if (detected.isNotEmpty()) detected else safeVault.sites
                    } else {
                        safeVault.sites
                    }
                }

                if (filteredSites.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "الخزينة لا تحتوي على حسابات." else "لا توجد نتائج مطابقة لـ \"$searchQuery\"",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredSites, key = { it.id.ifBlank { it.url + it.name } }) { site ->
                            PickerSiteCard(
                                site = site,
                                onCopy = { type, value ->
                                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("ReWord", value))
                                    Toast.makeText(this@FloatingPickerActivity, "تم نسخ $type بنجاح ✓", Toast.LENGTH_SHORT).show()
                                },
                                onDirectFill = { user, pass ->
                                    val filledUser = if (user.isNotBlank()) ReWordAccessibilityService.fillTextIntoFocusedNode(user) else false
                                    if (filledUser) {
                                        Toast.makeText(this@FloatingPickerActivity, "تمت التعبئة التلقائية في الحقل ✓", Toast.LENGTH_SHORT).show()
                                        finish()
                                    } else {
                                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("ReWord Password", pass))
                                        Toast.makeText(this@FloatingPickerActivity, "تم نسخ كلمة المرور للحافظة ✓", Toast.LENGTH_SHORT).show()
                                        finish()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun attemptBiometricUnlock(onSuccess: (Vault) -> Unit) {
        val iv = storage.getBiometricIv() ?: return
        val encryptedPassBytes = storage.getBiometricEncryptedPassword() ?: return

        val executor = ContextCompat.getMainExecutor(this)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("إلغاء قفل ReWord")
            .setSubtitle("المس مستشعر البصمة للوصول السريع إلى الحسابات")
            .setNegativeButtonText("إلغاء")
            .build()

        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                try {
                    val cipher = result.cryptoObject?.cipher ?: return
                    val decryptedBytes = cipher.doFinal(encryptedPassBytes)
                    val masterPass = String(decryptedBytes, Charsets.UTF_8)
                    val blob = storage.vaultBlob ?: return
                    val json = CryptoEngine.decrypt(blob, masterPass)
                    val v = gson.fromJson(json, Vault::class.java)
                    SecureStorage.setSession(v, masterPass)
                    onSuccess(v)
                } catch (_: Exception) {
                    Toast.makeText(this@FloatingPickerActivity, "فشل فك التشفير بالبصمة", Toast.LENGTH_SHORT).show()
                }
            }
        })

        try {
            val cryptoObj = KeystoreHelper.createDecryptCryptoObject(iv)
            prompt.authenticate(promptInfo, cryptoObj)
        } catch (_: Exception) {
            // Biometric not ready
        }
    }
}

@Composable
fun PickerSiteCard(
    site: Site,
    onCopy: (type: String, value: String) -> Unit,
    onDirectFill: (user: String, pass: String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Site Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SiteFavicon(url = site.url, name = site.name, sizeDp = 36)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = site.name.ifBlank { site.url },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (site.url.isNotBlank()) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = site.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Accounts inside
            site.accounts.forEach { acc ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(SurfaceDark, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = acc.user.ifBlank { "••••••••" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (ReWordAccessibilityService.isRunning()) {
                            IconButton(
                                onClick = { onDirectFill(acc.user, acc.pass) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FlashOn,
                                    contentDescription = "ملء مباشر",
                                    tint = SuccessEmerald,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { onCopy("اسم المستخدم", acc.user) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "نسخ المستخدم",
                                tint = AccentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { onCopy("كلمة المرور", acc.pass) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "نسخ كلمة المرور",
                                tint = PrimaryViolet,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
