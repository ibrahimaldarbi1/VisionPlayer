package com.example.ui.feature.live

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.config.ProviderProfile

@Composable
fun LiveParentalPinDialog(
    visible: Boolean,
    verificationLoading: Boolean,
    verificationError: String?,
    profile: ProviderProfile,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }

    LaunchedEffect(visible) {
        if (!visible) {
            pinInput = ""
        }
    }

    if (visible) {
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Parental Control PIN Required") },
            text = {
                Column {
                    val textDesc = "This Live TV content is protected by parental controls."
                    Text(textDesc)
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = pinInput,
                        onValueChange = { value ->
                            pinInput = value.filter { it.isDigit() }.take(4)
                        },
                        label = { Text("Enter 4-Digit PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color(profile.branding.primaryColor),
                            focusedLabelColor = Color(profile.branding.primaryColor)
                        ),
                        modifier = Modifier.testTag("parental_pin_input_field")
                    )
                    if (verificationError != null) {
                        Text(
                            text = verificationError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("parental_pin_error_text")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onSubmit(pinInput) },
                    enabled = !verificationLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(profile.branding.primaryColor)),
                    modifier = Modifier.testTag("confirm_parental_pin_button")
                ) {
                    if (verificationLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Unlock")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag("cancel_parental_pin_button")
                ) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("parental_pin_dialog")
        )
    }
}
