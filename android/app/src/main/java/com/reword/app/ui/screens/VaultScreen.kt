package com.reword.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    var selectedTag by remember { mutableStateOf<String?>(null) }

    val allTags = remember(vault.sites) {
        vault.sites
            .flatMap { site ->
                site.tags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
            }
            .distinct()
            .sorted()
    }

    val filteredSites = remember(vault, searchQuery, selectedTag) {
        var list = vault.sites
        if (!selectedTag.isNullOrBlank()) {
            val tagLower = selectedTag!!.lowercase()
            list = list.filter { site ->
                site.tags?.split(",")?.any { it.trim().equals(tagLower, ignoreCase = true) } == true
            }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter { site ->
                site.name.lowercase().contains(q) ||
                        site.url.lowercase().contains(q) ||
                        site.accounts.any { it.user.lowercase().contains(q) } ||
                        (site.tags?.lowercase()?.contains(q) == true)
            }
        }
        list
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
                .navigationBarsPadding()
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

            // Groups (Tags) Filter Chips
            if (allTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        val isAllSelected = selectedTag == null
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isAllSelected) PrimaryViolet else SurfaceCard)
                                .border(
                                    1.dp,
                                    if (isAllSelected) PrimaryViolet else BorderDark,
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { selectedTag = null }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "الكل",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isAllSelected) Color.White else TextSecondary
                            )
                        }
                    }
                    items(allTags) { tag ->
                        val isSelected = selectedTag.equals(tag, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) PrimaryViolet else SurfaceCard)
                                .border(
                                    1.dp,
                                    if (isSelected) PrimaryViolet else BorderDark,
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    selectedTag = if (isSelected) null else tag
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else TextSecondary
                            )
                        }
                    }
                }
            }

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
