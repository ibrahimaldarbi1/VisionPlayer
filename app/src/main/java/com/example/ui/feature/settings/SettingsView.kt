package com.example.ui.feature.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.config.ProviderConfigRegistry
import com.example.config.ProviderProfile
import com.example.data.*

@Composable
fun SettingsView(
    profile: ProviderProfile,
    onProfileSelected: (ProviderProfile) -> Unit,
    uiState: SettingsUiState,
    onRefreshCache: () -> Unit,
    onSetSubScreen: (String?) -> Unit,
    onShowProfileDialog: (Boolean) -> Unit,
    onSelectTab: (String) -> Unit,
    onReorderCategories: (List<String>) -> Unit,
    onSetCategoryPinned: (String, Boolean) -> Unit,
    onSetCategoryHidden: (String, Boolean) -> Unit,
    onResetCategoryCustomization: () -> Unit,
    onNavigateToSupport: (() -> Unit)?,
    onNavigateToParental: (() -> Unit)?,
    onNavigateToSearch: () -> Unit,
    onLogout: () -> Unit,
    isTv: Boolean,
    selectedFootballCompetitionCount: Int,
    onConfigureFootball: () -> Unit = {},
    onDismissCategoryError: () -> Unit = {}
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("iptv_settings", Context.MODE_PRIVATE) }
    var streamFormat by remember { mutableStateOf(sharedPrefs.getString("stream_format", "TS") ?: "TS") }

    val redactedServer = remember(uiState.activeSession) {
        val url = uiState.activeSession?.serverUrl
        com.example.core.redaction.AccountHostHelper.formatRedactedHost(url)
    }

    if (uiState.subScreen == "CATEGORY_MANAGEMENT") {
        CategoryManagementView(
            profile = profile,
            uiState = uiState,
            onSelectTab = onSelectTab,
            onReorderCategories = onReorderCategories,
            onSetCategoryPinned = onSetCategoryPinned,
            onSetCategoryHidden = onSetCategoryHidden,
            onResetCategoryCustomization = onResetCategoryCustomization,
            isTv = isTv,
            onBack = { onSetSubScreen(null) },
            onDismissCategoryError = onDismissCategoryError
        )
    } else {
        if (com.example.config.DemoPolicy.isDemoModeAllowed && uiState.showProfileDialog) {
            AlertDialog(
                onDismissRequest = { onShowProfileDialog(false) },
                title = { Text("Provider Switcher (Demo Only)") },
                text = {
                    Column {
                        Text("Select a custom provider profile to instantly reconstruct and customize the player UI and hide disabled features.")
                        Spacer(modifier = Modifier.height(16.dp))
                        ProviderConfigRegistry.ALL_PROFILES.forEach { prof ->
                            Button(
                                onClick = {
                                    onProfileSelected(prof)
                                    onShowProfileDialog(false)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (prof.id == profile.id) Color(profile.branding.primaryColor) else Color.DarkGray
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(prof.name, color = Color.White)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { onShowProfileDialog(false) }) { Text("Close") }
                }
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    text = "SYSTEM SETTINGS",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                // Account Details
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = Color(0xFF334155).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(20.dp)
                    )
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, "Account", tint = Color(profile.branding.primaryColor), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Active Account Connection", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Server: $redactedServer", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (com.example.config.DemoPolicy.isDemoModeAllowed) {
                item {
                    // Branded Profile Switcher (White Labeling test showcase)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .clickable { onShowProfileDialog(true) }
                            .border(
                                width = 1.dp,
                                color = Color(0xFF334155).copy(alpha = 0.3f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .testTag("provider_switcher_card")
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Layers, "White-Label", tint = Color(profile.branding.primaryColor), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("White-Label Provider Profile", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Currently Active: ${profile.name} (Tap to change profiles and see feature-hiding in action)", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            if (!isTv && profile.features.searchEnabled) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .clickable(onClick = onNavigateToSearch)
                            .border(
                                width = 1.dp,
                                color = Color(0xFF334155).copy(alpha = 0.3f),
                                shape = RoundedCornerShape(20.dp)
                            )
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, "Search", tint = Color(profile.branding.primaryColor), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Search", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Find channels, movies, and series", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // Category Management Section (Visible if at least one category type is enabled)
            if (profile.features.liveTvEnabled || profile.features.moviesEnabled || profile.features.seriesEnabled) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .clickable { onSetSubScreen("CATEGORY_MANAGEMENT") }
                            .border(
                                width = 1.dp,
                                color = Color(0xFF334155).copy(alpha = 0.3f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .testTag("category_management_card")
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Settings, "Category Management", tint = Color(profile.branding.primaryColor), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Category Management", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Hide, reorder, or pin Live, Movie, and Series categories", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            if (profile.features.multiViewEnabled) {
                item {
                    val activityManager = remember { context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager }
                    val isLowMemoryDevice = remember { activityManager?.isLowRamDevice == true }
                    var maxStreams by remember { mutableStateOf(sharedPrefs.getInt("multi_view_max_streams", if (isLowMemoryDevice) 2 else 4)) }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = Color(0xFF334155).copy(alpha = 0.3f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .testTag("multiview_settings_card")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.GridView, "Multi-view Settings", tint = Color(profile.branding.primaryColor), modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Multi-view Maximum Streams", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("Configure how many parallel feeds you can stream simultaneously (Max 4)", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf(2, 3, 4).forEach { option ->
                                    FilterChip(
                                        selected = maxStreams == option,
                                        onClick = {
                                            maxStreams = option
                                            sharedPrefs.edit().putInt("multi_view_max_streams", option).apply()
                                        },
                                        label = { Text("$option Streams") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(profile.branding.primaryColor),
                                            selectedLabelColor = Color.White
                                        ),
                                        modifier = Modifier.testTag("max_streams_chip_$option")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (profile.features.parentalControlEnabled && onNavigateToParental != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .clickable(onClick = onNavigateToParental)
                            .border(
                                width = 1.dp,
                                color = Color(0xFF334155).copy(alpha = 0.3f),
                                shape = RoundedCornerShape(20.dp)
                            )
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, "Parental Control", tint = Color(profile.branding.primaryColor), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Parental Controls", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Lock/Unlock adult categories and customize PIN codes", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            if (profile.features.footballScheduleEnabled) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .clickable(onClick = onConfigureFootball)
                            .border(
                                width = 1.dp,
                                color = Color(0xFF334155).copy(alpha = 0.3f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .testTag("football_settings_card")
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SportsSoccer,
                                contentDescription = "Football Schedule",
                                tint = Color(profile.branding.primaryColor),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Football Schedule Settings", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                                val subtitleText = when {
                                    selectedFootballCompetitionCount == 0 -> "No competitions selected"
                                    selectedFootballCompetitionCount == 1 -> "1 competition selected"
                                    else -> "$selectedFootballCompetitionCount competitions selected"
                                }
                                Text(subtitleText, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = Color(0xFF334155).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(20.dp)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Settings, "Stream Format", tint = Color(profile.branding.primaryColor), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Live Stream Format", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Switch format if Live streams fail to play (TS vs HLS/M3U8)", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("TS", "M3U8").forEach { format ->
                                val isSelected = streamFormat == format
                                Button(
                                    onClick = {
                                        streamFormat = format
                                        sharedPrefs.edit().putString("stream_format", format).apply()
                                        onRefreshCache()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) Color(profile.branding.primaryColor) else Color.DarkGray
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = if (format == "TS") "TS (.ts) (Default)" else "HLS (.m3u8)",
                                        color = Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (profile.features.liveTvEnabled && profile.features.epgEnabled) {
                item {
                    Card(
                        colors = CardColors(
                            containerColor = Color(profile.branding.surfaceColor),
                            contentColor = Color.White,
                            disabledContainerColor = Color(profile.branding.surfaceColor).copy(alpha = 0.5f),
                            disabledContentColor = Color.Gray
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .clickable(enabled = !uiState.isRefreshingCache, onClick = onRefreshCache)
                            .border(
                                width = 1.dp,
                                color = Color(0xFF334155).copy(alpha = 0.3f),
                                shape = RoundedCornerShape(20.dp)
                            )
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (uiState.isRefreshingCache) {
                                CircularProgressIndicator(color = Color(profile.branding.primaryColor), modifier = Modifier.size(36.dp))
                            } else {
                                Icon(Icons.Default.Sync, "Refresh", tint = Color(profile.branding.primaryColor), modifier = Modifier.size(36.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Refresh EPG", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (uiState.isRefreshingCache) "Synchronizing XMLTV guide..." else "Synchronize XMLTV EPG database",
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            if (profile.features.supportPageEnabled && onNavigateToSupport != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .clickable(onClick = onNavigateToSupport)
                            .border(
                                width = 1.dp,
                                color = Color(0xFF334155).copy(alpha = 0.3f),
                                shape = RoundedCornerShape(20.dp)
                            )
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HelpCenter, "Support", tint = Color(profile.branding.primaryColor), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Support & Technical FAQ", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Submit bug reports and locate provider contact info", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("logout_button")
                ) {
                    Icon(Icons.Default.ExitToApp, "Logout")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LOGOUT SESSION", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CategoryManagementView(
    profile: ProviderProfile,
    uiState: SettingsUiState,
    onSelectTab: (String) -> Unit,
    onReorderCategories: (List<String>) -> Unit,
    onSetCategoryPinned: (String, Boolean) -> Unit,
    onSetCategoryHidden: (String, Boolean) -> Unit,
    onResetCategoryCustomization: () -> Unit,
    isTv: Boolean,
    onBack: () -> Unit,
    onDismissCategoryError: () -> Unit
) {
    // Determine enabled tabs
    val tabs = remember(profile) {
        buildList {
            if (profile.features.liveTvEnabled) add("LIVE")
            if (profile.features.moviesEnabled) add("MOVIE")
            if (profile.features.seriesEnabled) add("SERIES")
        }
    }

    val selectedTab = uiState.selectedTab
    val categories = uiState.categories

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("category_management_back_button")
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "CATEGORY MANAGEMENT",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onResetCategoryCustomization,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("reset_categories_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset to Default", style = MaterialTheme.typography.bodyMedium)
            }
        }

        uiState.categoryError?.let { err ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .testTag("category_error_banner")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismissCategoryError,
                        modifier = Modifier.size(24.dp).testTag("dismiss_category_error_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss Error",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        if (tabs.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0),
                containerColor = Color.Transparent,
                contentColor = Color(profile.branding.primaryColor),
                edgePadding = 0.dp,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                tabs.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { onSelectTab(tab) },
                        text = {
                            Text(
                                text = when (tab) {
                                    "LIVE" -> "LIVE TV"
                                    "MOVIE" -> "MOVIES"
                                    "SERIES" -> "SERIES"
                                    else -> tab
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }
            }
        }

        if (categories.isEmpty() && uiState.isCategoryOperating) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(profile.branding.primaryColor))
            }
        } else {
            Column(modifier = Modifier.weight(1f)) {
                if (uiState.isCategoryOperating) {
                    LinearProgressIndicator(
                        color = Color(profile.branding.primaryColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("category_operation_progress")
                    )
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                items(categories.size, key = { categories[it].id }) { index ->
                    val item = categories[index]
                    var isFocused by remember { mutableStateOf(false) }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isFocused) Color.White.copy(alpha = 0.15f) else Color(profile.branding.surfaceColor)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable()
                            .border(
                                width = 1.dp,
                                color = if (isFocused) Color.White else Color(0xFF334155).copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .testTag("category_item_${item.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (item.pinned) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = "Pinned",
                                        tint = Color(profile.branding.primaryColor),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .padding(end = 4.dp)
                                    )
                                }
                                Text(
                                    text = item.name,
                                    color = if (item.hidden) Color.Gray else Color.White,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textDecoration = if (item.hidden) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                    fontWeight = if (item.pinned) FontWeight.Bold else FontWeight.Normal
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val listIds = categories.map { it.id }.toMutableList()
                                        val temp = listIds[index]
                                        listIds[index] = listIds[index - 1]
                                        listIds[index - 1] = temp
                                        onReorderCategories(listIds)
                                    }
                                },
                                enabled = index > 0,
                                modifier = Modifier.testTag("move_up_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Move Up",
                                    tint = if (index > 0) Color.White else Color.Gray
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (index < categories.size - 1) {
                                        val listIds = categories.map { it.id }.toMutableList()
                                        val temp = listIds[index]
                                        listIds[index] = listIds[index + 1]
                                        listIds[index + 1] = temp
                                        onReorderCategories(listIds)
                                    }
                                },
                                enabled = index < categories.size - 1,
                                modifier = Modifier.testTag("move_down_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Move Down",
                                    tint = if (index < categories.size - 1) Color.White else Color.Gray
                                )
                            }

                            IconButton(
                                onClick = {
                                    onSetCategoryPinned(item.id, !item.pinned)
                                },
                                modifier = Modifier.testTag("pin_button_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = if (item.pinned) "Unpin" else "Pin",
                                    tint = if (item.pinned) Color(profile.branding.primaryColor) else Color.White.copy(alpha = 0.5f)
                                )
                            }

                            Switch(
                                checked = !item.hidden,
                                onCheckedChange = { visible ->
                                    onSetCategoryHidden(item.id, !visible)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(profile.branding.primaryColor),
                                    checkedTrackColor = Color(profile.branding.primaryColor).copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.testTag("hide_switch_${item.id}")
                            )
                        }
                    }
                }
            }
            }
        }
    }
}
