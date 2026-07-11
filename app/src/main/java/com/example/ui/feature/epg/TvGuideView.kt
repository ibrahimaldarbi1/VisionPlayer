package com.example.ui.feature.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.config.ProviderProfile
import com.example.data.LiveChannel
import com.example.ui.feature.live.LiveParentalPinDialog
import com.example.ui.feature.live.LiveParentalPresentationPolicy

private fun formatTimestamp(timeMs: Long): String {
    val date = java.util.Date(timeMs)
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(date)
}

@Composable
fun TvGuideView(
    uiState: EpgUiState,
    onSelectChannel: (String) -> Unit,
    onRequestPlay: (LiveChannel) -> Unit,
    onRetryPrograms: () -> Unit,
    onDismissProgramsError: () -> Unit,
    parentalControlsEnabled: Boolean,
    parentalLoading: Boolean,
    parentalLoadError: String?,
    pinDialogVisible: Boolean,
    pinVerificationLoading: Boolean,
    pinVerificationError: String?,
    lockedLiveCategoryIds: Set<String>,
    onSubmitParentalPin: (String) -> Unit,
    onCancelParentalDialog: () -> Unit,
    onRetryParentalStatus: () -> Unit,
    onDismissParentalLoadError: () -> Unit,
    isTv: Boolean,
    profile: ProviderProfile
) {
    LiveParentalPinDialog(
        visible = parentalControlsEnabled && pinDialogVisible,
        verificationLoading = pinVerificationLoading,
        verificationError = pinVerificationError,
        profile = profile,
        onSubmit = onSubmitParentalPin,
        onCancel = onCancelParentalDialog
    )

    Column(modifier = Modifier.fillMaxSize()) {
        if (parentalControlsEnabled && parentalLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.DarkGray)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Checking parental controls…", color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (parentalLoadError != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = parentalLoadError,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = onRetryParentalStatus) { Text("Retry") }
                TextButton(onClick = onDismissParentalLoadError) { Text("Dismiss") }
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            // Vertical Channel Picker
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.4f)
                    .background(Color.White.copy(alpha = 0.03f))
                    .padding(8.dp)
            ) {
                Text(
                    text = "EPG CHANNELS",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (uiState.channels.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No Live channels are currently available for the guide.", color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(items = uiState.channels, key = { it.id }) { channel ->
                            var isFocused by remember { mutableStateOf(false) }
                            val isSelected = uiState.selectedChannelId == channel.id
                            val channelLocked = LiveParentalPresentationPolicy.channelShowsLock(
                                parentalControlsEnabled = parentalControlsEnabled,
                                channel = channel,
                                lockedCategoryIds = lockedLiveCategoryIds
                            )

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .focusable()
                                    .clickable { onSelectChannel(channel.id) }
                                    .border(
                                        width = 2.dp,
                                        color = if (isSelected) Color(profile.branding.primaryColor) else if (isFocused) Color.White.copy(alpha = 0.5f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(profile.branding.primaryColor).copy(alpha = 0.2f) else Color(profile.branding.surfaceColor)
                                )
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = channel.logoUrl,
                                        contentDescription = channel.name,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = channel.name,
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (channelLocked) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Locked",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            
            // Program detail blocks
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.6f)
            ) {
                uiState.selectedChannel?.let { channel ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = "Logo",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = channel.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = channel.categoryName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.LightGray
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = { onRequestPlay(channel) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(profile.branding.primaryColor))
                        ) {
                            Icon(Icons.Default.PlayArrow, "Play")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Watch Channel")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.programsError != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.programsError,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = onRetryPrograms) { Text("Retry") }
                        TextButton(onClick = onDismissProgramsError) { Text("Dismiss") }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (uiState.programsLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(profile.branding.primaryColor))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Loading guide information…", color = Color.Gray)
                        }
                    }
                } else {
                    if (uiState.programsRefreshing) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(profile.branding.primaryColor), strokeWidth = 2.dp)
                        }
                    }

                    if (uiState.programs.isEmpty() && uiState.programsError == null && uiState.selectedChannel != null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No upcoming EPG information found for this channel.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(
                                items = uiState.programs,
                                key = { program ->
                                    program.epgId.ifBlank { "${program.channelId}:${program.startTime}" }
                                }
                            ) { prog ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = prog.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${formatTimestamp(prog.startTime)} - ${formatTimestamp(prog.endTime)}",
                                            color = Color(profile.branding.primaryColor),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = prog.description,
                                            color = Color.LightGray,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
