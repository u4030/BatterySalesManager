package com.batterysales.utils

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _fontSizeScale = MutableStateFlow(prefs.getFloat("font_size_scale", 1.0f))
    val fontSizeScale: StateFlow<Float> = _fontSizeScale

    private val _isBold = MutableStateFlow(prefs.getBoolean("is_bold", false))
    val isBold: StateFlow<Boolean> = _isBold

    private val _scaleInputText = MutableStateFlow(prefs.getBoolean("scale_input_text", true))
    val scaleInputText: StateFlow<Boolean> = _scaleInputText

    fun setFontSizeScale(scale: Float) {
        prefs.edit().putFloat("font_size_scale", scale).apply()
        _fontSizeScale.value = scale
    }

    fun setIsBold(bold: Boolean) {
        prefs.edit().putBoolean("is_bold", bold).apply()
        _isBold.value = bold
    }

    fun setScaleInputText(scale: Boolean) {
        prefs.edit().putBoolean("scale_input_text", scale).apply()
        _scaleInputText.value = scale
    }
}
