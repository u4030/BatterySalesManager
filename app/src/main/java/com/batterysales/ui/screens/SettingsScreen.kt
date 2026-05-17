package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.batterysales.viewmodel.SettingsViewModel
import com.batterysales.ui.components.SharedHeader
import com.batterysales.ui.components.HeaderIconButton

import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TextFields

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val fontSizeScale by viewModel.fontSizeScale.collectAsState()
    val isBold by viewModel.isBold.collectAsState()
    val scaleInputText by viewModel.scaleInputText.collectAsState()
    val migrationStatus by viewModel.migrationStatus.collectAsState()
    val isMigrating by viewModel.isMigrating.collectAsState()
    val summaryStatus by viewModel.summaryStatus.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState(initial = null)

    val bgColor = MaterialTheme.colorScheme.background
    val cardBgColor = MaterialTheme.colorScheme.surface
    val accentColor = Color(0xFFFB8C00)
    val headerGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFE53935), Color(0xFFFB8C00))
    )

    Scaffold(
        containerColor = bgColor
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Modern Header
            SharedHeader(
                title = "إعدادات التطبيق",
                onBackClick = { navController.popBackStack() }
            )

            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                // Data Management Group (Admin/Nuclear)
                if (currentUser?.role == "admin") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsHeaderItem("إدارة البيانات والنظام", Icons.Default.Storage)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBgColor)
                        ) {
                            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    "تحديث المعمارية وبناء الملخصات",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "يستخدم هذا الخيار لإعادة حساب الأرصدة والمخزون وبناء وثائق الملخصات الموفرة للقراءات.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Summary Status Indicators
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SummaryStatusTag("المخزون", summaryStatus["inventory_global"] ?: false)
                                    SummaryStatusTag("الموردين", summaryStatus["suppliers_overview"] ?: false)
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Button(
                                    onClick = { viewModel.startDataMigration() },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isMigrating,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                                ) {
                                    if (isMigrating) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("جاري المعالجة...")
                                    } else {
                                        Icon(Icons.Default.Refresh, contentDescription = null)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("ترحيل البيانات وإعادة بناء الملخصات")
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { viewModel.performHealthCheck() },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isMigrating,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFB8C00)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("فحص صحة البيانات (Cross-Audit)")
                                }

                                migrationStatus?.let { status ->
                                    Text(
                                        text = status,
                                        color = if (status.contains("نجاح")) Color(0xFF43A047) else Color(0xFFE53935),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Font Settings Group
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsHeaderItem("تنسيق الخط والعرض", Icons.Default.TextFields)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            SettingsSliderItem(
                                label = "حجم الخط العام",
                                value = fontSizeScale,
                                onValueChange = { viewModel.setFontSizeScale(it) },
                                valueRange = 0.8f..2.0f
                            )
                            
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            
                            SettingsToggleItem(
                                label = "خط عريض (Bold)",
                                checked = isBold,
                                onCheckedChange = { viewModel.setIsBold(it) }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                            SettingsToggleItem(
                                label = "تكبير خط نصوص الإدخال",
                                checked = scaleInputText,
                                onCheckedChange = { viewModel.setScaleInputText(it) }
                            )
                        }
                    }
                }

                // Preview Section
                Text(
                    "معاينة التغييرات",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "هذا نص للمعاينة بحجم الخط الحالي.",
                            fontSize = (16 * fontSizeScale).sp,
                            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "سيتم تطبيق هذه الإعدادات على جميع شاشات التطبيق.",
                            fontSize = (14 * fontSizeScale).sp,
                            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SummaryStatusTag(label: String, isReady: Boolean) {
    Surface(
        color = if (isReady) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                if (isReady) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (isReady) Color(0xFF43A047) else Color(0xFFFB8C00),
                modifier = Modifier.size(14.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isReady) Color(0xFF2E7D32) else Color(0xFFE65100)
            )
        }
    }
}

@Composable
private fun SettingsHeaderItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color(0xFFFB8C00), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SettingsSliderItem(label: String, value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
            Text(String.format("%.1f", value), color = Color(0xFFFB8C00), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFB8C00),
                activeTrackColor = Color(0xFFFB8C00),
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
        )
    }
}

@Composable
private fun SettingsToggleItem(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFFFB8C00),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
        )
    }
}
