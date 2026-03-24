package com.batterysales.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.batterysales.ui.theme.LocalInputTextStyle
import com.batterysales.utils.StringUtils
import kotlin.math.roundToInt

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
    val cursorPosition by keyboardController.cursorPosition
    val isFocused = isKeyboardVisible && currentKeyboardLabel == label

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { },
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            readOnly = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            onTextLayout = { textLayoutResult = it },
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

        // Precision Blinking Cursor Overlay
        if (isFocused) {
            textLayoutResult?.let { layout ->
                val cursorRect = layout.getCursorRect(cursorPosition.coerceIn(0, value.length))
                val density = androidx.compose.ui.platform.LocalDensity.current

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (cursorRect.left + with(density) { 16.dp.toPx() }).roundToInt(),
                                y = (cursorRect.top + with(density) { 24.dp.toPx() }).roundToInt()
                            )
                        }
                ) {
                    BlinkingCursor()
                }
            }
        }

        // Precision Touch Overlay
        if (enabled && !readOnly) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(value, label) {
                        detectTapGestures { offset ->
                            // Adjust offset by the same internal padding used for cursor
                            val adjustedOffset = offset.copy(
                                x = offset.x - with(density) { 16.dp.toPx() },
                                y = offset.y - with(density) { 24.dp.toPx() }
                            )
                            val clickedPosition = textLayoutResult?.getOffsetForPosition(adjustedOffset) ?: value.length

                            if (isFocused) {
                                keyboardController.setCursorPosition(clickedPosition)
                            } else {
                                keyboardController.showKeyboard(
                                    initialValue = value,
                                    label = label,
                                    keyboardType = keyboardType,
                                    onValueChange = { newValue ->
                                        onValueChange(StringUtils.normalizeDigits(newValue))
                                    },
                                    onSearch = onSearch,
                                    cursorPosition = clickedPosition
                                )
                            }
                        }
                    }
            )
        }
    }
}
