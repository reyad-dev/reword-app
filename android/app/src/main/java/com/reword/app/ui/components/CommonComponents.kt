package com.reword.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reword.app.model.Account
import com.reword.app.model.Site
import com.reword.app.ui.theme.*

@Composable
fun ReWordTopBar(
    title: String,
    syncStatus: String = "ok", // "syncing", "ok", "error"
    onSettingsClick: (() -> Unit)? = null,
    onSyncClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgDark)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(PrimaryViolet, AccentCyan)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ReWord",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SyncIndicatorDot(status = syncStatus)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onSyncClick != null) {
                IconButton(onClick = onSyncClick) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync",
                        tint = AccentCyan
                    )
                }
            }
            if (onSettingsClick != null) {
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun SyncIndicatorDot(status: String) {
    val color = when (status) {
        "syncing" -> WarningAmber
        "ok" -> SuccessEmerald
        "error" -> ErrorRose
        else -> TextMuted
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun SiteCard(site: Site) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(14.dp))
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (site.name.firstOrNull() ?: site.url.firstOrNull() ?: 'W').uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = site.name.ifBlank { site.url },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${site.accounts.size} حساب",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    HorizontalDivider(color = BorderDark, thickness = 1.dp)
                    site.accounts.forEach { acc ->
                        AccountItem(acc)
                    }
                }
            }
        }
    }
}

@Composable
fun AccountItem(account: Account) {
    val clipboardManager = LocalClipboardManager.current
    var showPass by remember { mutableStateOf(false) }
    var copiedUser by remember { mutableStateOf(false) }
    var copiedPass by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(SurfaceDark, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        // Username Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "اسم المستخدم", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text(text = account.user, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            }
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(account.user))
                    copiedUser = true
                }
            ) {
                Icon(
                    imageVector = if (copiedUser) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "نسخ اسم المستخدم",
                    tint = if (copiedUser) SuccessEmerald else AccentCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Password Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "كلمة المرور", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text(
                    text = if (showPass) account.pass else "••••••••••••",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
            }
            Row {
                IconButton(onClick = { showPass = !showPass }) {
                    Icon(
                        imageVector = if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "عرض كلمة المرور",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(account.pass))
                        copiedPass = true
                    }
                ) {
                    Icon(
                        imageVector = if (copiedPass) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "نسخ كلمة المرور",
                        tint = if (copiedPass) SuccessEmerald else PrimaryViolet,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
