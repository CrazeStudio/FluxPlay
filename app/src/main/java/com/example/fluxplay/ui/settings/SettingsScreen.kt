package com.example.fluxplay.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fluxplay.data.model.PlayerEngine
import com.example.fluxplay.ui.theme.*

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Player Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        containerColor = DarkBackground,
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Engine Section
            SettingsSectionHeader(title = "Engine & Decoding", icon = Icons.Default.Tune)

            SettingsEngineSelector(
                selectedEngine = settings.defaultEngine,
                onSelectEngine = { viewModel.setDefaultEngine(it) }
            )

            SettingsSwitchItem(
                title = "Hardware Acceleration",
                subtitle = "Use GPU decoding (hwdec=auto) for 4K 60fps low battery usage",
                checked = settings.hardwareAcceleration,
                onCheckedChange = { viewModel.setHardwareAcceleration(it) },
                testTag = "hw_accel_switch"
            )

            SettingsSwitchItem(
                title = "Background Audio Playback",
                subtitle = "Keep audio playing when app is minimized or screen is locked",
                checked = settings.backgroundPlay,
                onCheckedChange = { viewModel.setBackgroundPlay(it) },
                testTag = "bg_play_switch"
            )

            // Gestures Section
            SettingsSectionHeader(title = "Gestures & Controls", icon = Icons.Default.TouchApp)

            SettingsSwitchItem(
                title = "Vertical Swipe for Brightness",
                subtitle = "Swipe up/down on the left side of the screen",
                checked = settings.gestureBrightness,
                onCheckedChange = { viewModel.setGestureBrightness(it) }
            )

            SettingsSwitchItem(
                title = "Vertical Swipe for Volume",
                subtitle = "Swipe up/down on the right side of the screen",
                checked = settings.gestureVolume,
                onCheckedChange = { viewModel.setGestureVolume(it) }
            )

            SettingsSwitchItem(
                title = "Remember Playback Position",
                subtitle = "Resume videos and streams where you left off",
                checked = settings.rememberLastPosition,
                onCheckedChange = { viewModel.setRememberPosition(it) }
            )

            // Theme & Appearance
            SettingsSectionHeader(title = "Appearance", icon = Icons.Default.Palette)

            SettingsThemeSelector(
                currentTheme = settings.selectedTheme,
                onThemeSelected = { viewModel.setTheme(it) }
            )

            // App Info
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DarkSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Fluxplay v2.0.0",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Dual-Engine (ExoPlayer + LibMPV 0.38) High-Performance Media Player with Material 3 UI",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = IndigoPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SettingsEngineSelector(
    selectedEngine: PlayerEngine,
    onSelectEngine: (PlayerEngine) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DarkSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Default Video Engine",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EngineOptionCard(
                    title = "ExoPlayer",
                    subtitle = "Google Media3 / HLS / DASH",
                    isSelected = selectedEngine == PlayerEngine.EXOPLAYER,
                    onClick = { onSelectEngine(PlayerEngine.EXOPLAYER) },
                    modifier = Modifier.weight(1f)
                )

                EngineOptionCard(
                    title = "LibMPV",
                    subtitle = "FFmpeg / MKV / GPU Vo",
                    isSelected = selectedEngine == PlayerEngine.LIBMPV,
                    onClick = { onSelectEngine(PlayerEngine.LIBMPV) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun EngineOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) IndigoPrimary.copy(alpha = 0.2f) else DarkSurfaceVariant,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, IndigoPrimary) else null,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = if (isSelected) IndigoPrimary else TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                if (isSelected) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String = ""
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DarkSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = IndigoPrimary,
                    checkedTrackColor = IndigoPrimary.copy(alpha = 0.4f),
                    uncheckedTrackColor = DarkSurfaceVariant
                ),
                modifier = if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier
            )
        }
    }
}

@Composable
fun SettingsThemeSelector(
    currentTheme: String,
    onThemeSelected: (String) -> Unit
) {
    val themes = listOf("Dark", "AMOLED", "Cyberpunk")
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DarkSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Color Palette",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                themes.forEach { theme ->
                    val isSelected = theme == currentTheme
                    FilterChip(
                        selected = isSelected,
                        onClick = { onThemeSelected(theme) },
                        label = { Text(theme) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = DarkSurfaceVariant,
                            labelColor = TextSecondary,
                            selectedContainerColor = IndigoPrimary,
                            selectedLabelColor = TextPrimary
                        ),
                        border = null
                    )
                }
            }
        }
    }
}
