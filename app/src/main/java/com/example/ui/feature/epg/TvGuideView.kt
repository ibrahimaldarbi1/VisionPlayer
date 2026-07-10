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
import com.example.data.*

private fun formatTimestamp(timeMs: Long): String {
    val date = java.util.Date(timeMs)
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(date)
}

@Composable
fun TvGuideView(
    channels: List<LiveChannel>,
    repository: IptvRepository,
    onPlayLive: (LiveChannel) -> Unit,
    isTv: Boolean,
    profile: com.example.config.ProviderProfile
) {
    var selectedChannel by remember { mutableStateOf<LiveChannel?>(channels.firstOrNull()) }
    var programList by remember { mutableStateOf<List<EpgProgramEntity>>(emptyList()) }

    LaunchedEffect(selectedChannel) {
        selectedChannel?.let { channel ->
            repository.getEpgForChannel(channel.id).collect { list ->
                programList = list
            }
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
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(channels) { channel ->
                    var isFocused by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable()
                            .clickable { selectedChannel = channel }
                            .border(
                                width = 2.dp,
                                color = if (selectedChannel?.id == channel.id) Color(profile.branding.primaryColor) else if (isFocused) Color.White.copy(alpha = 0.5f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedChannel?.id == channel.id) Color(profile.branding.primaryColor).copy(alpha = 0.2f) else Color(profile.branding.surfaceColor)
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
                                overflow = TextOverflow.Ellipsis
                            )
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
            selectedChannel?.let { channel ->
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
                        onClick = { onPlayLive(channel) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(profile.branding.primaryColor))
                    ) {
                        Icon(Icons.Default.PlayArrow, "Play")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Watch Channel")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (programList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No EPG information found for this channel.", color = Color.Gray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(programList) { prog ->
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
