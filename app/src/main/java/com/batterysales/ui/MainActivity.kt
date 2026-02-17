package com.batterysales

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.batterysales.services.AppNotificationManager
import com.batterysales.services.NotificationService
import com.batterysales.ui.components.*
import com.batterysales.ui.navigation.AppNavigation
import com.batterysales.ui.theme.BatterySalesManagerTheme
import com.batterysales.utils.SettingsManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var notificationManager: AppNotificationManager

    @Inject
    lateinit var settingsManager: SettingsManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Start background notification service
        val serviceIntent = Intent(this, NotificationService::class.java)
        startService(serviceIntent)

        // Request notification permission for Android 13+
        askNotificationPermission()

        setContent {
            val fontSizeScale by settingsManager.fontSizeScale.collectAsState()
            val isBold by settingsManager.isBold.collectAsState()
            val scaleInputText by settingsManager.scaleInputText.collectAsState()
            val keyboardController = remember { CustomKeyboardController() }

            BatterySalesManagerTheme(
                fontSizeScale = fontSizeScale,
                isBold = isBold,
                scaleInputText = scaleInputText
            ) {
                CompositionLocalProvider(LocalCustomKeyboardController provides keyboardController) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f)) {
                            val navController = rememberNavController()
                            AppNavigation(navController = navController)
                        }

                        // This spacer mimics the keyboard space and pushes content up
                        Spacer(modifier = Modifier.height(keyboardController.keyboardHeight.value))
                    }

                    CustomAppKeyboard(
                        onValueChange = { keyboardController.updateValue(it) },
                        currentValue = keyboardController.currentValue.value,
                        isVisible = keyboardController.isVisible.value,
                        initialLanguage = keyboardController.keyboardType.value,
                        onDone = { keyboardController.hideKeyboard() },
                        onSearch = { keyboardController.onSearchClicked() }
                    )
                }
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // Already have permission
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
