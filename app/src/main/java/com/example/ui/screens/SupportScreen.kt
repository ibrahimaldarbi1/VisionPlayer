package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.config.ProviderConfigRegistry
import com.example.core.support.DiagnosticReportBuilder
import com.example.data.IptvRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    repository: IptvRepository,
    onBack: () -> Unit,
    onSendIntent: (Intent) -> Unit = {}
) {
    val profile = ProviderConfigRegistry.currentProfile
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    val activeSession by repository.activeSession.collectAsState(initial = null)

    var activeTicketType by remember { mutableStateOf("Channel Not Working") }
    var ticketDetails by remember { mutableStateOf("") }

    val ticketTypes = listOf(
        "Channel Not Working",
        "Movie Playback Freeze",
        "Wrong EPG information",
        "Login or Account Issue",
        "App Crash / Performance",
        "General Feedback"
    )

    // Build the diagnostic report dynamically using our centralized builder
    val diagnosticReport = remember(activeSession, activeTicketType, ticketDetails) {
        DiagnosticReportBuilder.buildReport(
            context = context,
            profile = profile,
            activeServerUrl = activeSession?.serverUrl,
            subject = activeTicketType,
            description = ticketDetails,
            isTv = false // Default/Fallback
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
                        .size(48.dp) // Accessibility min target
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "SUPPORT & DIAGNOSTICS",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Ticket Creation Form
            Text(
                text = "Compose Support Request",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select Subject Category", style = MaterialTheme.typography.titleSmall, color = Color.LightGray)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Box(modifier = Modifier.fillMaxWidth()) {
                        var expanded by remember { mutableStateOf(false) }
                        Button(
                            onClick = { expanded = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
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

                    Text("Detailed Description", style = MaterialTheme.typography.titleSmall, color = Color.LightGray)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ticketDetails,
                        onValueChange = { ticketDetails = it },
                        placeholder = { Text("Describe the channel name, error message, or feedback here...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .testTag("support_details_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(profile.branding.primaryColor),
                            focusedLabelColor = Color(profile.branding.primaryColor),
                            unfocusedTextColor = Color.White,
                            focusedTextColor = Color.White
                        )
                    )
                }
            }

            // Diagnostics Preview Section
            Text(
                text = "Diagnostics Preview (Auto-Generated)",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = diagnosticReport,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(diagnosticReport))
                            Toast.makeText(context, "Copied diagnostics to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.Share, "Copy")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy Report to Clipboard", color = Color.White)
                    }
                }
            }

            // Contact Channels Section
            Text(
                text = "Select Contact Channel to Send",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // 3 prominent channels
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Email Channel
                Button(
                    onClick = {
                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:${profile.support.email}")
                            putExtra(Intent.EXTRA_SUBJECT, "[${profile.appName}] Support - $activeTicketType")
                            putExtra(Intent.EXTRA_TEXT, diagnosticReport)
                        }
                        try {
                            context.startActivity(emailIntent)
                            onSendIntent(emailIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No email app found. Please copy report and contact manually.", Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = ticketDetails.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(profile.branding.primaryColor)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("submit_ticket_button")
                ) {
                    Icon(Icons.Default.Email, "Email")
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("SEND VIA EMAIL", fontWeight = FontWeight.Bold)
                }

                // Telegram Channel
                Button(
                    onClick = {
                        val handle = profile.support.telegram.substringAfterLast("/")
                        val primaryIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$handle"))
                        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$handle"))
                        
                        try {
                            clipboardManager.setText(AnnotatedString(diagnosticReport))
                            Toast.makeText(context, "Report copied to clipboard! Paste it in the chat.", Toast.LENGTH_LONG).show()
                            
                            context.startActivity(primaryIntent)
                            onSendIntent(primaryIntent)
                        } catch (e: Exception) {
                            try {
                                context.startActivity(fallbackIntent)
                                onSendIntent(fallbackIntent)
                            } catch (ex: Exception) {
                                Toast.makeText(context, "No Telegram app or web browser found.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = ticketDetails.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088CC)), // Telegram Blue
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(Icons.Default.Send, "Telegram")
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("SEND VIA TELEGRAM", fontWeight = FontWeight.Bold)
                }

                // WhatsApp Channel
                Button(
                    onClick = {
                        val cleanPhone = profile.support.whatsapp.replace("+", "").replace(" ", "").replace("-", "").trim()
                        val whatsappUri = Uri.parse("https://wa.me/$cleanPhone?text=${Uri.encode(diagnosticReport)}")
                        val whatsappIntent = Intent(Intent.ACTION_VIEW, whatsappUri)
                        
                        try {
                            context.startActivity(whatsappIntent)
                            onSendIntent(whatsappIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No WhatsApp app or web browser found.", Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = ticketDetails.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)), // WhatsApp Green
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(Icons.Default.Phone, "WhatsApp")
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("SEND VIA WHATSAPP", fontWeight = FontWeight.Bold)
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
