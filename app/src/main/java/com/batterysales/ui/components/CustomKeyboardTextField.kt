package com.batterysales.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.batterysales.ui.theme.LocalInputTextStyle
import com.batterysales.utils.StringUtils
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
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
            .width(2.dp)
            .height(24.dp)
            .background(Color(0xFFFB8C00).copy(alpha = alpha))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val isRtl = keyboardType == KeyboardLanguage.ARABIC
    val layoutDir = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    val inputTextStyle = LocalInputTextStyle.current
    val interactionSource = remember { MutableInteractionSource() }

    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
        Box(modifier = modifier) {
            val colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isFocused) Color(0xFFFB8C00) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                focusedLabelColor = Color(0xFFFB8C00),
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )

            BasicTextField(
                value = value,
                onValueChange = { },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                enabled = enabled,
                textStyle = inputTextStyle.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                ),
                cursorBrush = SolidColor(Color.Transparent), // We draw our own cursor
                visualTransformation = visualTransformation,
                onTextLayout = { textLayoutResult = it },
                decorationBox = { innerTextField ->
                    OutlinedTextFieldDefaults.DecorationBox(
                        value = value,
                        innerTextField = innerTextField,
                        enabled = enabled,
                        singleLine = true,
                        visualTransformation = visualTransformation,
                        interactionSource = interactionSource,
                        label = { Text(label) },
                        placeholder = placeholder?.let { { Text(it) } },
                        leadingIcon = leadingIcon,
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
                        },
                        colors = colors,
                        contentPadding = OutlinedTextFieldDefaults.contentPadding(),
                        container = {
                            OutlinedTextFieldDefaults.ContainerBox(
                                enabled = enabled,
                                isError = false,
                                interactionSource = interactionSource,
                                colors = colors,
                                shape = RoundedCornerShape(12.dp),
                                focusedBorderThickness = if (isFocused) 2.dp else 1.dp,
                                unfocusedBorderThickness = 1.dp
                            )
                        }
                    )
                }
            )

            // Precision Blinking Cursor Overlay
            if (isFocused) {
                val layout = textLayoutResult
                // Safety: Only draw cursor if layout is synchronized with the current text value
                // and the cursor position is within current layout text bounds.
                if (layout != null && layout.layoutInput.text.text == value) {
                    val layoutTextLength = layout.layoutInput.text.length
                    val safePos = cursorPosition.coerceIn(0, layoutTextLength)
                    val cursorRect = try {
                        layout.getCursorRect(safePos)
                    } catch (e: Exception) {
                        null
                    }

                    if (cursorRect != null) {
                        val density = androidx.compose.ui.platform.LocalDensity.current

                        // OutlinedTextField standard padding is 16.dp horizontal
                        val xOffsetPx = cursorRect.left + with(density) { 16.dp.toPx() }
                        val yOffsetPx = cursorRect.top + with(density) { 16.dp.toPx() } // Adjusted vertical padding

                        androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Box(
                                modifier = Modifier.offset { IntOffset(xOffsetPx.roundToInt(), yOffsetPx.roundToInt()) }
                            ) {
                                BlinkingCursor()
                            }
                        }
                    }
                }
            }

            // Precision Touch Overlay
            if (enabled && !readOnly) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(value, label, isRtl) {
                            detectTapGestures { offset ->
                                val xOff = if (isRtl) size.width - offset.x else offset.x
                                val adjustedOffset = androidx.compose.ui.geometry.Offset(
                                    x = xOff - with(density) { 16.dp.toPx() },
                                    y = offset.y - with(density) { 16.dp.toPx() }
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
}
