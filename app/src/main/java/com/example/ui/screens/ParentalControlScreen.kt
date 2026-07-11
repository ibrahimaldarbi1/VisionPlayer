package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.config.ProviderConfigRegistry
import com.example.data.IptvRepository
import com.example.data.ParentalControlEntity
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.feature.live.LiveParentalPolicyParser
import com.example.ui.feature.live.LiveParentalPolicySerializer

@Composable
fun ParentalControlScreen(
    repository: IptvRepository,
    onBack: () -> Unit
) {
    val profile = ProviderConfigRegistry.currentProfile
    val coroutineScope = rememberCoroutineScope()

    var isPinSet by remember { mutableStateOf(false) }
    var isUnlocked by remember { mutableStateOf(false) }

    // Forms
    var pinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    
    // Parental Control Parameters
    var adultContentModeHidden by remember { mutableStateOf(false) }
    val lockedCategories = remember { mutableStateListOf<String>() }

    val liveCategories by repository
        .observeAllCategoriesForManagement("LIVE")
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Load State
    LaunchedEffect(profile.id, profile.providerId) {
        pinInput = ""
        confirmPinInput = ""
        inputError = null

        val settings = repository.getParentalSettingsDirect()
        val status = settings?.let {
            LiveParentalPolicyParser.parse(
                storedPin = it.pin,
                storedCategoryPolicy = it.lockedCategories
            )
        }
        val loadedState = ParentalControlScreenStateMapper.fromStatus(status)

        isPinSet = loadedState.isPinSet
        isUnlocked = loadedState.isUnlocked
        adultContentModeHidden = loadedState.hideAdultContent
        lockedCategories.clear()
        lockedCategories.addAll(loadedState.lockedCategoryIds)
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
                        .testTag("parental_back_button")
                ) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "PARENTAL CONTROLS",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            if (isPinSet && !isUnlocked) {
                // LOCK Screen block
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Security, "Secure Lock", tint = Color(profile.branding.primaryColor), modifier = Modifier.size(72.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Enter Parental PIN", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Access to kids' limits and category controls is restricted.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4) pinInput = it },
                        label = { Text("4-Digit Passcode") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(profile.branding.primaryColor),
                            focusedLabelColor = Color(profile.branding.primaryColor)
                        ),
                        modifier = Modifier
                            .width(200.dp)
                            .testTag("parental_pin_verify_input")
                    )
                    
                    inputError?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val settings = repository.getParentalSettingsDirect()
                                if (settings != null && settings.pin == pinInput) {
                                    isUnlocked = true
                                    inputError = null
                                    pinInput = ""
                                } else {
                                    inputError = "Incorrect Passcode. Try again."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(profile.branding.primaryColor)),
                        modifier = Modifier.width(200.dp).testTag("verify_pin_button")
                    ) {
                        Text("UNLOCK SETTINGS")
                    }
                }
            } else if (!isPinSet) {
                // PIN Creation Flow
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Lock, "Set Lock", tint = Color(profile.branding.primaryColor), modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Create Parental Code", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Set a secure 4-digit passcode to lock adult categories.", color = Color.Gray)
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4) pinInput = it },
                        label = { Text("Enter 4-Digit PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(profile.branding.primaryColor),
                            focusedLabelColor = Color(profile.branding.primaryColor)
                        ),
                        modifier = Modifier.width(260.dp).testTag("parental_new_pin")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPinInput,
                        onValueChange = { if (it.length <= 4) confirmPinInput = it },
                        label = { Text("Confirm 4-Digit PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(profile.branding.primaryColor),
                            focusedLabelColor = Color(profile.branding.primaryColor)
                        ),
                        modifier = Modifier.width(260.dp).testTag("parental_new_pin_confirm")
                    )

                    inputError?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (pinInput.length != 4) {
                                inputError = "PIN must be exactly 4 digits."
                            } else if (pinInput != confirmPinInput) {
                                inputError = "PINs do not match."
                            } else {
                                coroutineScope.launch {
                                    repository.saveParentalSettings(
                                        ParentalControlEntity(pin = pinInput, lockedCategories = "")
                                    )
                                    isPinSet = true
                                    isUnlocked = true
                                    inputError = null
                                    pinInput = ""
                                    confirmPinInput = ""
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(profile.branding.primaryColor)),
                        modifier = Modifier.width(260.dp).testTag("create_pin_button")
                    ) {
                        Text("CREATE PASSCODE")
                    }
                }
            } else {
                // PIN is Unlocked -> Category locks manager UI
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor))) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Fully Hide Adult Categories", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("Hide adult-tagged channels and selected locked categories from Live TV.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(
                                    checked = adultContentModeHidden,
                                    onCheckedChange = { value ->
                                        adultContentModeHidden = value
                                        coroutineScope.launch {
                                            saveLockedCategories(
                                                repository = repository,
                                                lockedCategories = lockedCategories.toList(),
                                                hideAdult = value
                                            )
                                        }
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color(profile.branding.primaryColor))
                                )
                            }
                        }
                    }

                    item {
                        Text("Lock Live TV Categories", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    items(
                        items = liveCategories,
                        key = { it.id }
                    ) { category ->
                        val isLocked = lockedCategories.contains(category.id)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                            modifier = Modifier.clickable {
                                if (isLocked) {
                                    lockedCategories.remove(category.id)
                                } else {
                                    lockedCategories.add(category.id)
                                }
                                coroutineScope.launch {
                                    saveLockedCategories(
                                        repository = repository,
                                        lockedCategories = lockedCategories.toList(),
                                        hideAdult = adultContentModeHidden
                                    )
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(category.name, style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("Subtype: LIVE", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                }
                                Checkbox(
                                    checked = isLocked,
                                    onCheckedChange = { _ ->
                                        if (isLocked) {
                                            lockedCategories.remove(category.id)
                                        } else {
                                            lockedCategories.add(category.id)
                                        }
                                        coroutineScope.launch {
                                            saveLockedCategories(
                                                repository = repository,
                                                lockedCategories = lockedCategories.toList(),
                                                hideAdult = adultContentModeHidden
                                            )
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(profile.branding.primaryColor))
                                )
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    repository.clearParentalSettings()
                                    isPinSet = false
                                    isUnlocked = false
                                    lockedCategories.clear()
                                    adultContentModeHidden = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text("DISABLE ALL PARENTAL PASSCODES", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private suspend fun saveLockedCategories(
    repository: IptvRepository,
    lockedCategories: Collection<String>,
    hideAdult: Boolean
) {
    val settings = repository.getParentalSettingsDirect() ?: return
    val serializedPolicy = LiveParentalPolicySerializer.serialize(
        lockedCategoryIds = lockedCategories,
        hideAdultContent = hideAdult
    )
    repository.saveParentalSettings(
        ParentalControlEntity(
            pin = settings.pin,
            lockedCategories = serializedPolicy
        )
    )
}
