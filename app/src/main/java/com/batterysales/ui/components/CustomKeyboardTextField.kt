package com.batterysales.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.batterysales.ui.theme.LocalInputTextStyle

@Composable
fun CustomKeyboardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    keyboardType: KeyboardLanguage = KeyboardLanguage.ARABIC
) {
    val keyboardController = LocalCustomKeyboardController.current

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { },
            label = { Text(label) },
            readOnly = true, // Always readOnly for system keyboard
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalInputTextStyle.current
        )
        // Transparent overlay to capture clicks
        if (enabled && !readOnly) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable {
                        keyboardController.showKeyboard(
                            initialValue = value,
                            label = label,
                            keyboardType = keyboardType,
                            onValueChange = onValueChange
                        )
                    }
            )
        }
    }
}
