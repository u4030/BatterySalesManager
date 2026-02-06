package com.batterysales.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import android.graphics.drawable.ColorDrawable

enum class KeyboardLanguage {
    ARABIC, ENGLISH_UPPER, ENGLISH_LOWER, NUMERIC
}

@Composable
fun CustomAppKeyboard(
    onValueChange: (String) -> Unit,
    currentValue: String,
    isVisible: Boolean,
    initialLanguage: KeyboardLanguage = KeyboardLanguage.ARABIC,
    onDone: () -> Unit
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
        }

        val arabicRows = listOf(
            listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج", "د"),
            listOf("ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م", "ك", "ط"),
            listOf("ئ", "ء", "ؤ", "ر", "لا", "ى", "ة", "و", "ز", "ظ")
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
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
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
                        // Language toggle or special key can go here
                        Surface(
                            onClick = {
                                if (currentValue.isNotEmpty()) {
                                    onValueChange(currentValue.dropLast(1))
                                }
                            },
                            modifier = Modifier.height(50.dp).weight(1.5f),
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
                            modifier = Modifier.height(50.dp).weight(4f),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Space", fontSize = 14.sp)
                            }
                        }

                        Surface(
                            onClick = onDone,
                            modifier = Modifier.height(50.dp).weight(1.5f),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("تم", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
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
        "ا" -> listOf("أ", "إ", "آ", "آ")
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
                .height(50.dp)
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        if (alternatives.isNotEmpty()) {
                            showAlternatives = true
                        }
                    }
                ),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = text,
                    fontSize = 22.sp,
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
                    modifier = Modifier.width(50.dp).wrapContentHeight(),
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
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(alt, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}
