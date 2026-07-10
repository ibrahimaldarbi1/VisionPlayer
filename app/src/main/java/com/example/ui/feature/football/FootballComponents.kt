package com.example.ui.feature.football

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.*
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.CancellationException

@Composable
fun FootballMatchCard(
    watchMatch: FootballWatchMatch,
    primaryColor: Color,
    surfaceColor: Color,
    onWatchClick: (LiveChannel) -> Unit,
    onAddToMultiView: ((LiveChannel) -> Unit)? = null
) {
    val match = watchMatch.match
    val kickoffMillis = FootballMatchUtils.parseUtcToMillis(match.kickoffUtc)
    val localTime = if (kickoffMillis > 0) {
        val sdf = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
        sdf.format(java.util.Date(kickoffMillis))
    } else {
        "Unknown Time"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .width(280.dp)
            .border(
                width = 1.dp,
                color = Color(0xFF334155).copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("football_match_card_${match.matchId}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top row: Competition details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (!match.competitionEmblemUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = match.competitionEmblemUrl,
                            contentDescription = match.competitionName,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sports,
                            contentDescription = "Competition",
                            tint = primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = match.competitionName ?: "Football",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Status Badge
                val statusText = match.status ?: "SCHEDULED"
                Box(
                    modifier = Modifier
                        .background(
                            color = when (statusText) {
                                "LIVE", "IN_PLAY", "PAUSED" -> Color.Red.copy(alpha = 0.15f)
                                "FINISHED" -> Color.Gray.copy(alpha = 0.15f)
                                else -> Color.White.copy(alpha = 0.08f)
                            },
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = statusText,
                        color = when (statusText) {
                            "LIVE", "IN_PLAY", "PAUSED" -> Color.Red
                            "FINISHED" -> Color.Gray
                            else -> Color.LightGray
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Teams & Score
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Home Team
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (!match.homeTeamCrestUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = match.homeTeamCrestUrl,
                                contentDescription = match.homeTeamName,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(Color.DarkGray, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                val text = match.homeTeamName?.take(1) ?: "H"
                                Text(
                                    text = text,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = match.homeTeamName ?: "Home Team",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = match.homeScore?.toString() ?: "-",
                        color = if (match.homeScore != null) Color.White else Color.Gray,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Away Team
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (!match.awayTeamCrestUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = match.awayTeamCrestUrl,
                                contentDescription = match.awayTeamName,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(Color.DarkGray, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                val text = match.awayTeamName?.take(1) ?: "A"
                                Text(
                                    text = text,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = match.awayTeamName ?: "Away Team",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = match.awayScore?.toString() ?: "-",
                        color = if (match.awayScore != null) Color.White else Color.Gray,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))

            // Time and EPG/Watch area
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kickoff",
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = localTime,
                        color = Color.LightGray,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                val matchedChannel = watchMatch.matchedChannel
                if (matchedChannel != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onAddToMultiView != null) {
                            IconButton(
                                onClick = { onAddToMultiView(matchedChannel) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(primaryColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .testTag("football_add_multiview_button_${match.matchId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = "Add to Multi-view",
                                    tint = primaryColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Button(
                            onClick = { onWatchClick(matchedChannel) },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("football_watch_button_${match.matchId}")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Watch",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Watch",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    val targetChName = watchMatch.matchedProgramName
                    if (!targetChName.isNullOrBlank()) {
                        Text(
                            text = "On $targetChName",
                            color = Color.Gray,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    } else {
                        Text(
                            text = "Broadcast channel not available yet",
                            color = Color.Gray,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FootballConfigDialog(
    onDismiss: () -> Unit,
    profile: com.example.config.ProviderProfile,
    competitions: List<FootballCompetitionPreference>,
    selectedKeys: Set<String>,
    isLoading: Boolean,
    errorMessage: String?,
    onToggleCompetition: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    hasSeenSetup: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Choose football competitions",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
            ) {
                Text(
                    text = "Select the leagues and cups you want to see on your Home screen.",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(profile.branding.primaryColor))
                    }
                } else if (errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = errorMessage,
                                color = Color.Red,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(profile.branding.primaryColor))
                            ) {
                                Text("Retry", color = Color.White)
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            TextButton(
                                onClick = onSelectAll,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Select All", color = Color(profile.branding.primaryColor), fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = onClearAll,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Clear All", color = Color(profile.branding.primaryColor), fontWeight = FontWeight.Bold)
                            }
                        }

                        Text(
                            text = "${selectedKeys.size} selected",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(competitions) { comp ->
                                val isChecked = selectedKeys.contains(comp.competitionKey)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onToggleCompetition(comp.competitionKey)
                                        }
                                        .padding(vertical = 6.dp)
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = null
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = comp.name ?: "",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val isEnabled = selectedKeys.isNotEmpty() && !isLoading && errorMessage == null
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = Color(profile.branding.primaryColor)),
                enabled = isEnabled
            ) {
                Text("Save", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(
                    text = if (!hasSeenSetup) "Not Now" else "Cancel",
                    color = Color.Gray
                )
            }
        },
        containerColor = Color(profile.branding.surfaceColor)
    )
}
