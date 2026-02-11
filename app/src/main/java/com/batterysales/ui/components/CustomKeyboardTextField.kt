package com.batterysales.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
            textStyle = LocalInputTextStyle.current,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFFB8C00),
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                focusedLabelColor = Color(0xFFFB8C00),
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
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
