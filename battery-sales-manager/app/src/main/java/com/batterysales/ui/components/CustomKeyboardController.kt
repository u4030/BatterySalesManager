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
        onSearch: (() -> Unit)? = null
    ) {
        _currentValue.value = initialValue
        _label.value = label
        _keyboardType.value = keyboardType
        this.onValueChange = onValueChange
        this.onSearch = onSearch
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
}

val LocalCustomKeyboardController = staticCompositionLocalOf<CustomKeyboardController> {
    error("No CustomKeyboardController provided")
}
