package com.batterysales.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
class CustomKeyboardController {
    private val _isVisible = mutableStateOf(false)
    val isVisible: State<Boolean> = _isVisible

    private val _currentValue = mutableStateOf("")
    val currentValue: State<String> = _currentValue

    private val _cursorPosition = mutableStateOf(0)
    val cursorPosition: State<Int> = _cursorPosition

    private val _label = mutableStateOf("")
    val label: State<String> = _label

    private val _keyboardType = mutableStateOf(KeyboardLanguage.ARABIC)
    val keyboardType: State<KeyboardLanguage> = _keyboardType

    private var onValueChange: ((String) -> Unit)? = null
    private var onSearch: (() -> Unit)? = null

    fun showKeyboard(
        initialValue: String,
        label: String,
        keyboardType: KeyboardLanguage,
        onValueChange: (String) -> Unit,
        onSearch: (() -> Unit)? = null,
        cursorPosition: Int = -1
    ) {
        _currentValue.value = initialValue
        _label.value = label
        _keyboardType.value = keyboardType
        this.onValueChange = onValueChange
        this.onSearch = onSearch
        _cursorPosition.value = if (cursorPosition == -1) initialValue.length else cursorPosition
        _isVisible.value = true
    }

    fun onSearchClicked() {
        onSearch?.invoke()
        hideKeyboard()
    }

    fun hideKeyboard() {
        _isVisible.value = false
    }

    val keyboardHeight: State<Dp> = derivedStateOf {
        if (_isVisible.value) 350.dp else 0.dp
    }

    fun updateValue(newValue: String) {
        _currentValue.value = newValue
        onValueChange?.invoke(newValue)
    }

    fun moveCursorLeft() {
        if (_cursorPosition.value > 0) {
            _cursorPosition.value -= 1
        }
    }

    fun moveCursorRight() {
        if (_cursorPosition.value < _currentValue.value.length) {
            _cursorPosition.value += 1
        }
    }

    fun setCursorPosition(pos: Int) {
        _cursorPosition.value = pos.coerceIn(0, _currentValue.value.length)
    }

    fun insertText(text: String) {
        val current = _currentValue.value
        val pos = _cursorPosition.value
        val nextValue = current.substring(0, pos) + text + current.substring(pos)
        _currentValue.value = nextValue
        _cursorPosition.value = pos + text.length
        onValueChange?.invoke(nextValue)
    }

    fun deleteAtCursor() {
        val current = _currentValue.value
        val pos = _cursorPosition.value
        if (pos > 0) {
            val nextValue = current.substring(0, pos - 1) + current.substring(pos)
            _currentValue.value = nextValue
            _cursorPosition.value = pos - 1
            onValueChange?.invoke(nextValue)
        }
    }
}

val LocalCustomKeyboardController = staticCompositionLocalOf<CustomKeyboardController> {
    error("No CustomKeyboardController provided")
}
