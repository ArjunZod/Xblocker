package com.antigravity.shieldx.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import com.antigravity.shieldx.ui.theme.*

/**
 * Dialogs shared across screens. Layout primitives live in Primitives.kt; this
 * file holds only the interactive pieces that need their own state.
 */

/**
 * Gates a protected setting behind the admin PIN.
 *
 * Note the failure copy: it distinguishes "no PIN has been set yet" from "that
 * PIN is wrong", because those need different actions from the user and the old
 * dialog reported both as a flat access denial.
 */
@Composable
fun PinEntryDialog(
    onDismiss: () -> Unit,
    onVerify: (String) -> Boolean,
    onSuccess: () -> Unit,
    title: String = "Enter admin PIN",
    subtitle: String = "This setting is protected.",
    isPinConfigured: Boolean = true
) {
    var pin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        },
        text = {
            Column {
                Text(
                    text = if (isPinConfigured) {
                        subtitle
                    } else {
                        "No admin PIN has been set yet. Set one in Settings before changing this."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                if (isPinConfigured) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            if (it.length <= 8) {
                                pin = it.filter(Char::isDigit)
                                isError = false
                            }
                        },
                        placeholder = { Text("PIN", color = TextTertiary) },
                        isError = isError,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        shape = Radius.sm,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = Border,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = Accent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Space.md)
                    )

                    if (isError) {
                        Text(
                            text = "That PIN is not correct.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Danger,
                            modifier = Modifier.padding(top = Space.sm)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isPinConfigured,
                onClick = {
                    if (onVerify(pin)) onSuccess() else isError = true
                }
            ) {
                Text(
                    text = "Unlock",
                    color = if (isPinConfigured) Accent else TextTertiary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

