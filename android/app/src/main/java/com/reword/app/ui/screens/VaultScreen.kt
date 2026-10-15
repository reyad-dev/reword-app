package com.reword.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reword.app.model.Vault
import com.reword.app.ui.components.ReWordTopBar
import com.reword.app.ui.components.SiteCard
import com.reword.app.ui.theme.*

@Composable
fun VaultScreen(
    vault: Vault,
    syncStatus: String = "ok",
    onSyncClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredSites = remember(vault, searchQuery) {
        if (searchQuery.isBlank()) {
            vault.sites
        } else {
            val q = searchQuery.trim().lowercase()
            vault.sites.filter { site ->
                site.name.lowercase().contains(q) ||
                        site.url.lowercase().contains(q) ||
                        site.accounts.any { it.user.lowercase().contains(q) }
            }
        }
    }

    val totalAccounts = remember(vault) {
        vault.sites.sumOf { it.accounts.size }
    }

    Scaffold(
        topBar = {
            ReWordTopBar(
                title = "${vault.sites.size} موقع • $totalAccounts حساب",
                syncStatus = syncStatus,
                onSettingsClick = onSettingsClick,
                onSyncClick = onSyncClick
            )
        },
        containerColor = BgDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
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

            Spacer(modifier = Modifier.height(14.dp))

            if (filteredSites.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "الخزينة فارغة حالياً." else "لا توجد نتائج مطابقة لـ \"$searchQuery\"",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredSites, key = { it.id.ifBlank { it.url } }) { site ->
                        SiteCard(site = site)
                    }
                }
            }
        }
    }
}
