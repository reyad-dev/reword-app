package com.reword.app.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.reword.app.model.Account
import com.reword.app.model.Site
import com.reword.app.ui.theme.*
import java.net.URI

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
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
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
                    modifier = Modifier.size(22.dp)
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

/**
 * Extracts a clean domain from site URL or name for Google Favicon API
 */
fun extractFaviconDomain(url: String, name: String): String {
    val raw = if (url.isNotBlank()) {
        url.split(",", ";", " ").firstOrNull()?.trim().orEmpty()
    } else {
        name.trim()
    }
    return try {
        val formatted = if (!raw.contains("://")) "https://$raw" else raw
        val host = URI(formatted).host ?: raw
        host.removePrefix("www.")
    } catch (_: Exception) {
        raw.removePrefix("www.")
    }
}

@Composable
fun SiteFavicon(
    url: String,
    name: String,
    modifier: Modifier = Modifier,
    sizeDp: Int = 44
) {
    val domain = remember(url, name) { extractFaviconDomain(url, name) }
    val faviconUrl = remember(domain) {
        if (domain.isNotBlank()) "https://www.google.com/s2/favicons?domain=${domain}&sz=128" else null
    }

    val fallbackInitial = remember(name, domain) {
        (name.firstOrNull() ?: domain.firstOrNull() ?: 'W').uppercase()
    }

    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderDark, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (!faviconUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(faviconUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = name,
                modifier = Modifier
                    .size((sizeDp - 14).dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Fit,
                error = {
                    Text(
                        text = fallbackInitial,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        fontSize = (sizeDp * 0.42).sp
                    )
                },
                loading = {
                    Text(
                        text = fallbackInitial,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan.copy(alpha = 0.5f),
                        fontSize = (sizeDp * 0.42).sp
                    )
                }
            )
        } else {
            Text(
                text = fallbackInitial,
                fontWeight = FontWeight.Bold,
                color = AccentCyan,
                fontSize = (sizeDp * 0.42).sp
            )
        }
    }
}

@Composable
fun SiteCard(site: Site) {
    var expanded by remember { mutableStateOf(false) }
    val rotationState by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ChevronRotation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = if (expanded) PrimaryViolet.copy(alpha = 0.4f) else BorderDark,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SiteFavicon(url = site.url, name = site.name)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = site.name.ifBlank { site.url },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        if (site.url.isNotBlank()) {
                            // Ensure URL text direction is LTR so domains read naturally
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

                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Account count pill badge matching Web App (.site-count)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(PrimaryViolet.copy(alpha = 0.15f))
                            .border(1.dp, PrimaryViolet.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${site.accounts.size} حساب",
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryViolet,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "طي" else "توسيع",
                        tint = TextSecondary,
                        modifier = Modifier.rotate(rotationState)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = BorderDark, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(6.dp))
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
    val context = LocalContext.current
    var showPass by remember { mutableStateOf(false) }
    var copiedUser by remember { mutableStateOf(false) }
    var copiedPass by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(SurfaceDark, RoundedCornerShape(10.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(10.dp))
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
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = account.user.ifBlank { "—" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(account.user))
                    copiedUser = true
                    Toast.makeText(context, "تم نسخ اسم المستخدم ✓", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(
                    imageVector = if (copiedUser) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "نسخ اسم المستخدم",
                    tint = if (copiedUser) SuccessEmerald else AccentCyan,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = BorderDark.copy(alpha = 0.5f), thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(4.dp))

        // Password Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "كلمة المرور", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = if (showPass) account.pass else "••••••••••••",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Row {
                IconButton(onClick = { showPass = !showPass }) {
                    Icon(
                        imageVector = if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "عرض كلمة المرور",
                        tint = TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(account.pass))
                        copiedPass = true
                        Toast.makeText(context, "تم نسخ كلمة المرور ✓", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = if (copiedPass) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "نسخ كلمة المرور",
                        tint = if (copiedPass) SuccessEmerald else PrimaryViolet,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}
