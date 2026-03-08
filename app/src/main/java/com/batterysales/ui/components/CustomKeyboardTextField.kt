package com.batterysales.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.batterysales.ui.theme.LocalInputTextStyle

@Composable
fun BlinkingCursor() {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.7f at 500
            },
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    Box(
        modifier = Modifier
            .padding(end = 12.dp)
            .width(2.dp)
            .height(24.dp)
            .background(Color(0xFFFB8C00).copy(alpha = alpha))
    )
}

@Composable
fun CustomKeyboardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    keyboardType: KeyboardLanguage = KeyboardLanguage.ARABIC,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    suffix: String? = null,
    onSearch: (() -> Unit)? = null
) {
    val keyboardController = LocalCustomKeyboardController.current
    val isKeyboardVisible by keyboardController.isVisible
    val currentKeyboardLabel by keyboardController.label
    val isFocused = isKeyboardVisible && currentKeyboardLabel == label

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { },
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            readOnly = true, // Always readOnly for system keyboard
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalInputTextStyle.current,
            shape = RoundedCornerShape(12.dp),
            visualTransformation = visualTransformation,
            leadingIcon = leadingIcon,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isFocused) Color(0xFFFB8C00) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                focusedLabelColor = Color(0xFFFB8C00),
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isFocused) {
                        BlinkingCursor()
                    }
                    if (suffix != null) {
                        Text(
                            text = suffix,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                    trailingIcon?.invoke()
                }
            }
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
                            onValueChange = onValueChange,
                            onSearch = onSearch
                        )
                    }
            )
        }
    }
}
