package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.config.ProviderProfile
import com.example.core.support.ContactIntentBuilder
import com.example.core.support.DiagnosticReportBuilder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    profile: ProviderProfile,
    viewModel: SupportViewModel,
    isTv: Boolean,
    onBack: () -> Unit,
    onSendIntent: (Intent) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    // Lifecycle-aware collection of the active session server URL
    val activeServerUrl by viewModel.activeServerUrl.collectAsStateWithLifecycle()

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

    // Build diagnostic report dynamically using the centralized DiagnosticReportBuilder
    val diagnosticReport = remember(activeServerUrl, activeTicketType, ticketDetails, isTv) {
        DiagnosticReportBuilder.buildReport(
            context = context,
            profile = profile,
            activeServerUrl = activeServerUrl,
            subject = activeTicketType,
            description = ticketDetails,
            isTv = isTv
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
                        .size(48.dp)
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
                val emailAddress = profile.support.email
                val isEmailValid = ContactIntentBuilder.isValidEmail(emailAddress)
                Button(
                    onClick = {
                        if (!isEmailValid) {
                            Toast.makeText(context, "Invalid support email configured.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val emailIntent = ContactIntentBuilder.buildEmailIntent(
                            recipient = emailAddress,
                            subject = "[${profile.appName}] Support - $activeTicketType",
                            body = diagnosticReport
                        )
                        
                        if (emailIntent.resolveActivity(context.packageManager) != null) {
                            try {
                                context.startActivity(emailIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to open email app.", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(context, "No email app found. Please copy report and contact manually.", Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = ticketDetails.isNotBlank() && isEmailValid,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(profile.branding.primaryColor)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("submit_ticket_button")
                ) {
                    Icon(Icons.Default.Email, "Email")
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("OPEN EMAIL APP", fontWeight = FontWeight.Bold)
                }

                // Telegram Channel
                val rawTelegram = profile.support.telegram
                val normalizedTelegram = ContactIntentBuilder.normalizeTelegramHandle(rawTelegram)
                Button(
                    onClick = {
                        if (normalizedTelegram == null) {
                            Toast.makeText(context, "Invalid Telegram handle configured.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        clipboardManager.setText(AnnotatedString(diagnosticReport))
                        Toast.makeText(context, "Report copied! Please paste it in the chat.", Toast.LENGTH_LONG).show()

                        val telegramUrl = ContactIntentBuilder.buildTelegramUrl(normalizedTelegram)
                        val telegramIntent = Intent(Intent.ACTION_VIEW, Uri.parse(telegramUrl))
                        
                        if (telegramIntent.resolveActivity(context.packageManager) != null) {
                            try {
                                context.startActivity(telegramIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to open Telegram.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "No browser or Telegram app found.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = ticketDetails.isNotBlank() && normalizedTelegram != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088CC)), // Telegram Blue
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(Icons.Default.Send, "Telegram")
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("OPEN TELEGRAM CHAT", fontWeight = FontWeight.Bold)
                }

                // WhatsApp Channel
                val rawWhatsapp = profile.support.whatsapp
                val normalizedWhatsapp = ContactIntentBuilder.normalizeWhatsAppNumber(rawWhatsapp)
                Button(
                    onClick = {
                        if (normalizedWhatsapp == null) {
                            Toast.makeText(context, "Invalid WhatsApp number configured.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val whatsappUrl = ContactIntentBuilder.buildWhatsAppUrl(normalizedWhatsapp, diagnosticReport)
                        val whatsappIntent = Intent(Intent.ACTION_VIEW, Uri.parse(whatsappUrl))
                        
                        if (whatsappIntent.resolveActivity(context.packageManager) != null) {
                            try {
                                context.startActivity(whatsappIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to open WhatsApp.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "No browser or WhatsApp app found.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = ticketDetails.isNotBlank() && normalizedWhatsapp != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)), // WhatsApp Green
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(Icons.Default.Phone, "WhatsApp")
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("OPEN WHATSAPP CHAT", fontWeight = FontWeight.Bold)
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
