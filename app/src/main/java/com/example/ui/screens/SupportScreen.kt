package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.config.ProviderConfigRegistry
import com.example.data.IptvRepository

@Composable
fun SupportScreen(
    repository: IptvRepository,
    onBack: () -> Unit
) {
    val profile = ProviderConfigRegistry.currentProfile
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val activeSession by repository.activeSession.collectAsState(initial = null)

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
            title = { Text("Support Ticket Form Opened") },
            text = { Text("The bug report has been pre-composed with technical diagnostic logs and passed to your email app. Please send the email to finish submitting the ticket to ${profile.name} support.") },
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
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val telegramUrl = if (profile.support.telegram.startsWith("http")) {
                                profile.support.telegram
                            } else {
                                "https://${profile.support.telegram}"
                            }
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(telegramUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "No Telegram app or web browser found.", Toast.LENGTH_SHORT).show()
                            }
                        },
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
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val cleanPhone = profile.support.whatsapp.replace("+", "").replace(" ", "").replace("-", "")
                            val whatsappUrl = "https://wa.me/$cleanPhone"
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(whatsappUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "No WhatsApp app or web browser found.", Toast.LENGTH_SHORT).show()
                            }
                        },
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
                    val redactedHost = activeSession?.serverUrl?.let { com.example.core.redaction.SensitiveDataRedactor.redactUrl(it) } ?: "demo.iptvserver.net"
                    DiagnosticItem(label = "Application Version", value = "v1.1.4 (WhiteLabel Build)")
                    DiagnosticItem(label = "Active Provider ID", value = profile.id)
                    DiagnosticItem(label = "Redacted Host", value = redactedHost)
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
                        onClick = {
                            val redactedHost = activeSession?.serverUrl?.let { com.example.core.redaction.SensitiveDataRedactor.redactUrl(it) } ?: "demo.iptvserver.net"
                            val appVersion = "v1.1.4 (WhiteLabel Build)"
                            val providerId = profile.id
                            val androidVersion = "SDK ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})"
                            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"

                            val diagnosticBody = """
Category: $activeTicketType
Details: $ticketDetails

--- DIAGNOSTIC INFORMATION ---
App Version: $appVersion
Provider Name: ${profile.name} (ID: $providerId)
Redacted Account Host: $redactedHost
Android Version: $androidVersion
Device Model: $deviceModel
                            """.trimIndent()

                            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:")
                                putExtra(Intent.EXTRA_EMAIL, arrayOf(profile.support.email))
                                putExtra(Intent.EXTRA_SUBJECT, "[VisionPlayer] Support Ticket")
                                putExtra(Intent.EXTRA_TEXT, diagnosticBody)
                            }
                            try {
                                context.startActivity(emailIntent)
                                showConfirmation = true
                            } catch (e: Exception) {
                                Toast.makeText(context, "No email client found to send support ticket.", Toast.LENGTH_LONG).show()
                            }
                        },
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
