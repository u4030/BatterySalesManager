package com.batterysales.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

enum class KeyboardLanguage {
    ARABIC, ENGLISH_UPPER, ENGLISH_LOWER, NUMERIC
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CustomAppKeyboard(
    onValueChange: (String) -> Unit,
    currentValue: String,
    isVisible: Boolean,
    initialLanguage: KeyboardLanguage = KeyboardLanguage.ARABIC,
    onDone: () -> Unit,
    onSearch: (() -> Unit)? = null
) {
    if (!isVisible) return

    var language by remember { mutableStateOf(initialLanguage) }

    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        window?.let {
            it.setGravity(Gravity.BOTTOM)
            it.setDimAmount(0f)
            it.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            it.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            it.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        }

        // <<<=== 1. تم تعديل الصفوف لتقليل عدد الأزرار في كل صف
        val arabicRows = listOf(
            listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح"),
            listOf("ج", "د", "ش", "س", "ي", "ب", "ل", "ا", "ت"),
            listOf("ن", "م", "ك", "ط", "ئ", "ء", "ؤ", "ر"),
            listOf("لا", "ى", "ة", "و", "ز", "ظ")
        )

        val englishUpperRows = listOf(
            listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
            listOf("Z", "X", "C", "V", "B", "N", "M")
        )

        val englishLowerRows = listOf(
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            listOf("z", "x", "c", "v", "b", "n", "m")
        )

        val numericRows = listOf(
            listOf("1", "2", "3", "+"),
            listOf("4", "5", "6", "-"),
            listOf("7", "8", "9", "*"),
            listOf(".", "0", "=", "/")
        )

        val currentRows = when (language) {
            KeyboardLanguage.ARABIC -> arabicRows
            KeyboardLanguage.ENGLISH_UPPER -> englishUpperRows
            KeyboardLanguage.ENGLISH_LOWER -> englishLowerRows
            KeyboardLanguage.NUMERIC -> numericRows
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column(modifier = Modifier.padding(4.dp)) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f)) {
                        KeyboardControlBtn(
                            text = "عربي",
                            isSelected = language == KeyboardLanguage.ARABIC,
                            onClick = { language = KeyboardLanguage.ARABIC }
                        )
                        KeyboardControlBtn(
                            text = "EN",
                            isSelected = language == KeyboardLanguage.ENGLISH_LOWER || language == KeyboardLanguage.ENGLISH_UPPER,
                            onClick = {
                                language = if (language == KeyboardLanguage.ENGLISH_LOWER) KeyboardLanguage.ENGLISH_UPPER else KeyboardLanguage.ENGLISH_LOWER
                            }
                        )
                        KeyboardControlBtn(
                            text = "123",
                            isSelected = language == KeyboardLanguage.NUMERIC,
                            onClick = { language = KeyboardLanguage.NUMERIC }
                        )
                    }

                    IconButton(onClick = onDone) {
                        Icon(Icons.Default.KeyboardHide, contentDescription = "Hide")
                    }
                }

                // Keys Grid
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    currentRows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
                        ) {
                            row.forEach { key ->
                                KeyboardKey(
                                    text = key,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onValueChange(currentValue + key) },
                                    onAltClick = { onValueChange(currentValue + it) }
                                )
                            }
                        }
                    }

                    // Bottom Row (Space, Delete, Done)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val currentTextState = rememberUpdatedState(currentValue)

                        // Continuous delete on long press
                        LaunchedEffect(isPressed) {
                            if (isPressed) {
                                while (currentTextState.value.isNotEmpty()) {
                                    onValueChange(currentTextState.value.dropLast(1))
                                    delay(100) // Adjust delay for deletion speed
                                }
                            }
                        }

                        // Backspace Button
                        Surface(
                            modifier = Modifier
                                .height(55.dp)
                                .weight(1.5f)
                                .combinedClickable(
                                    interactionSource = interactionSource,
                                    indication = null, // Disable ripple for custom feedback
                                    onClick = {
                                        if (currentValue.isNotEmpty()) {
                                            onValueChange(currentValue.dropLast(1))
                                        }
                                    },
                                    onLongClick = {} // Handled by LaunchedEffect
                                ),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }

                        // SPACE BAR (Centered and Longer)
                        Surface(
                            onClick = { onValueChange(currentValue + " ") },
                            modifier = Modifier
                                .height(55.dp)
                                .weight(4f),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Space", fontSize = 14.sp)
                            }
                        }

                        Surface(
                            onClick = {
                                if (onSearch != null) onSearch() else onDone()
                            },
                            modifier = Modifier
                                .height(55.dp)
                                .weight(1.5f),
                            shape = RoundedCornerShape(8.dp),
                            color = if (onSearch != null) Color(0xFFFB8C00) else MaterialTheme.colorScheme.primary
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "تم",
                                    color = if (onSearch != null) Color.White else MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeyboardKey(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onAltClick: (String) -> Unit
) {
    var showAlternatives by remember { mutableStateOf(false) }
    val alternatives = when (text) {
        "ا" -> listOf("أ", "إ", "آ")
        "و" -> listOf("ؤ")
        "ي" -> listOf("ئ", "ى")
        "ه" -> listOf("ة")
        "ت" -> listOf("ة")
        "د" -> listOf("ذ")
        "ط" -> listOf("ظ")
        "ص" -> listOf("ض")
        "ع" -> listOf("غ")
        "ف" -> listOf("ق")
        "س" -> listOf("ش")
        "ح" -> listOf("خ")
        "ج" -> listOf("چ")
        "A" -> listOf("4")
        "E" -> listOf("3")
        "I" -> listOf("8")
        "O" -> listOf("9")
        "P" -> listOf("0")
        "Q" -> listOf("1")
        "R" -> listOf("4")
        "S" -> listOf("5")
        "T" -> listOf("5")
        "U" -> listOf("7")
        "W" -> listOf("2")
        "a" -> listOf("4")
        "e" -> listOf("3")
        "i" -> listOf("8")
        "o" -> listOf("9")
        "p" -> listOf("0")
        "q" -> listOf("1")
        "r" -> listOf("4")
        "s" -> listOf("5")
        "t" -> listOf("5")
        "u" -> listOf("7")
        "w" -> listOf("2")
        else -> emptyList()
    }

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .height(55.dp)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { if (alternatives.isNotEmpty()) showAlternatives = true }
                    )
                },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = text,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (showAlternatives) {
            Popup(
                onDismissRequest = { showAlternatives = false },
                alignment = Alignment.TopCenter
            ) {
                Card(
                    modifier = Modifier.wrapContentHeight(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        alternatives.forEach { alt ->
                            Surface(
                                onClick = {
                                    onAltClick(alt)
                                    showAlternatives = false
                                },
                                modifier = Modifier.size(55.dp),
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(alt, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KeyboardControlBtn(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
            Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}
