package com.batterysales.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.batterysales.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val fontSizeScale by viewModel.fontSizeScale.collectAsState()
    val isBold by viewModel.isBold.collectAsState()
    val scaleInputText by viewModel.scaleInputText.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                "إعدادات الخط",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            // Font Size Setting
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("حجم الخط", style = MaterialTheme.typography.bodyLarge)
                    Text(String.format("%.1f", fontSizeScale), style = MaterialTheme.typography.bodyMedium)
                }
                Slider(
                    value = fontSizeScale,
                    onValueChange = { viewModel.setFontSizeScale(it) },
                    valueRange = 0.8f..1.5f,
                    steps = 6
                )
            }

            // Bold Text Setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("خط غامق", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = isBold,
                    onCheckedChange = { viewModel.setIsBold(it) }
                )
            }

            // Scale Input Text Setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("تكبير خط نصوص الإدخال", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = scaleInputText,
                    onCheckedChange = { viewModel.setScaleInputText(it) }
                )
            }

            HorizontalDivider()

            // Preview Section
            Text(
                "معاينة",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("هذا نص للمعاينة بحجم الخط الحالي.", fontSize = (16 * fontSizeScale).sp, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal)
                    Text("سيتم تطبيق هذه الإعدادات على جميع شاشات التطبيق.", fontSize = (14 * fontSizeScale).sp, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
