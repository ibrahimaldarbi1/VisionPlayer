package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.config.ProviderConfigRegistry

@Composable
fun SupportScreen(
    onBack: () -> Unit
) {
    val profile = ProviderConfigRegistry.currentProfile
    val scrollState = rememberScrollState()

    var activeTicketType by remember { mutableStateOf("Channel Not Working") }
    var ticketDetails by remember { mutableStateOf("") }
    var showConfirmation by remember { mutableStateOf(false) }

    val ticketTypes = listOf(
        "Channel Not Working",
        "Movie Playback Freeze",
        "Wrong EPG information",
        "Login or Account Issue",
        "App Crash / Performance",
        "General Feedback"
    )

    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text("Support Ticket Submitted") },
            text = { Text("Thank you! Your technical ticket has been filed securely with ${profile.name} support. Our diagnostics team will review your system logs shortly.") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmation = false
                        ticketDetails = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(profile.branding.primaryColor))
                ) {
                    Text("OK")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(profile.branding.backgroundColor))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(scrollState)
        ) {
            // Header Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .background(Color(profile.branding.surfaceColor), RoundedCornerShape(12.dp))
                        .testTag("support_back_button")
                ) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "PROVIDER SUPPORT",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Quick Support Links Cards Grid
            Text(
                text = "Direct Contact Methods",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.Send, "Telegram", tint = Color(profile.branding.primaryColor))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Telegram Chat", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(profile.support.telegram, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.Phone, "WhatsApp", tint = Color(profile.branding.primaryColor))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("WhatsApp Support", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(profile.support.whatsapp, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Tech Diagnostic Diagnostics
            Text(
                text = "Diagnostics & System Info",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiagnosticItem(label = "Application Version", value = "v1.1.4 (WhiteLabel Build)")
                    DiagnosticItem(label = "Active Provider ID", value = profile.id)
                    DiagnosticItem(label = "Android Version", value = "SDK ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
                    DiagnosticItem(label = "Hardware Model", value = "${Build.MANUFACTURER} ${Build.MODEL}")
                    DiagnosticItem(label = "Official Website", value = profile.support.website)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Problem report form
            Text(
                text = "Report active streaming issues",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select Ticket Category", style = MaterialTheme.typography.titleSmall, color = Color.LightGray)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Simple select category tabs
                    Box(modifier = Modifier.fillMaxWidth()) {
                        var expanded by remember { mutableStateOf(false) }
                        Button(
                            onClick = { expanded = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(activeTicketType, color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.ArrowDropDown, "Expand")
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(Color(profile.branding.surfaceColor))
                        ) {
                            ticketTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type, color = Color.White) },
                                    onClick = {
                                        activeTicketType = type
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Description Details", style = MaterialTheme.typography.titleSmall, color = Color.LightGray)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ticketDetails,
                        onValueChange = { ticketDetails = it },
                        placeholder = { Text("Specify channel names, movies, or diagnostic details here...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .testTag("support_details_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(profile.branding.primaryColor),
                            focusedLabelColor = Color(profile.branding.primaryColor)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showConfirmation = true },
                        enabled = ticketDetails.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(profile.branding.primaryColor)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("submit_ticket_button")
                    ) {
                        Icon(Icons.Default.Send, "Submit")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SUBMIT BUG REPORT", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.LightGray, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}
