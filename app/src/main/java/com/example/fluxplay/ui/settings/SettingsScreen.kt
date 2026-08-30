package com.example.fluxplay.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fluxplay.data.model.AppThemeMode
import com.example.fluxplay.data.model.PlayerEngine
import com.example.fluxplay.ui.theme.*

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val cacheSize by viewModel.cacheSizeFormatted.collectAsStateWithLifecycle()
    val downloadsSize by viewModel.downloadsSizeFormatted.collectAsStateWithLifecycle()
    val isCleaningCache by viewModel.isCleaningCache.collectAsStateWithLifecycle()
    val cacheCleanMessage by viewModel.cacheCleanMessage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearBookmarksDialog by remember { mutableStateOf(false) }
    var showClearDownloadsDialog by remember { mutableStateOf(false) }
    var showCleanCacheDialog by remember { mutableStateOf(false) }
    var showResetUserDataDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshCacheSize()
        viewModel.refreshDownloadsSize()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = if (onBack != null) 4.dp else 16.dp, vertical = 8.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Theme Section
            Text(
                text = "Visual Themes",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppThemeMode.values().forEach { theme ->
                        val isSelected = settings.selectedTheme == theme
                        val themeScheme = getThemeColorScheme(theme)

                        Surface(
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setTheme(theme) }
                                .testTag("theme_option_${theme.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = theme.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = theme.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = FluxTextSecondary
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(themeScheme.primary)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(themeScheme.secondary)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(themeScheme.background)
                                            .border(1.dp, FluxCardBorder, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Player Engine Profiles
            Text(
                text = "Player Engine & Profiles",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PlayerEngine.values().forEach { engine ->
                        val isSelected = settings.selectedEngine == engine

                        Surface(
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setEngine(engine) }
                                .testTag("engine_option_${engine.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = engine.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = engine.badge,
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = engine.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = FluxTextSecondary
                                    )
                                }

                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.setEngine(engine) },
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }
            }

            // Playback & Background Features
            Text(
                text = "Background & Notifications",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Background Playback", style = MaterialTheme.typography.bodyMedium, color = FluxTextPrimary, fontWeight = FontWeight.SemiBold)
                            Text("Keep audio/video streaming when screen is off or app minimized", style = MaterialTheme.typography.bodySmall, color = FluxTextSecondary)
                        }
                        Switch(
                            checked = settings.backgroundPlayEnabled,
                            onCheckedChange = { viewModel.setBackgroundPlay(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.background,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("switch_background_play")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Media Notifications", style = MaterialTheme.typography.bodyMedium, color = FluxTextPrimary, fontWeight = FontWeight.SemiBold)
                            Text("Show lockscreen & notification bar media transport controls", style = MaterialTheme.typography.bodySmall, color = FluxTextSecondary)
                        }
                        Switch(
                            checked = settings.notificationsEnabled,
                            onCheckedChange = { viewModel.setNotifications(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.background,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("switch_notifications")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hardware Acceleration", style = MaterialTheme.typography.bodyMedium, color = FluxTextPrimary, fontWeight = FontWeight.SemiBold)
                            Text("Use GPU MediaCodec hardware decoding", style = MaterialTheme.typography.bodySmall, color = FluxTextSecondary)
                        }
                        Switch(
                            checked = settings.hardwareAcceleration,
                            onCheckedChange = { viewModel.setHardwareAcceleration(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.background,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("switch_hw_accel")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto Resume", style = MaterialTheme.typography.bodyMedium, color = FluxTextPrimary, fontWeight = FontWeight.SemiBold)
                            Text("Remember playback time & restore position automatically", style = MaterialTheme.typography.bodySmall, color = FluxTextSecondary)
                        }
                        Switch(
                            checked = settings.autoResume,
                            onCheckedChange = { viewModel.setAutoResume(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.background,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("switch_auto_resume")
                        )
                    }
                }
            }

            // Storage & Cache Cleaner
            Text(
                text = "Storage & Cache Cleaner",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.CleaningServices,
                                    contentDescription = "Cache",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Active Media & App Cache",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = FluxTextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "ExoPlayer stream chunks, decoded posters & HTTP cache",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FluxTextSecondary
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = cacheSize,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    if (cacheCleanMessage != null) {
                        Surface(
                            color = FluxEmerald.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = FluxEmerald, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = cacheCleanMessage ?: "",
                                        color = FluxEmerald,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.dismissCacheMessage() },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = FluxEmerald, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showCleanCacheDialog = true },
                            enabled = !isCleaningCache,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(1f).testTag("btn_clean_cache")
                        ) {
                            if (isCleaningCache) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Cleaning...")
                            } else {
                                Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Clean Cache", fontWeight = FontWeight.Bold)
                            }
                        }

                        IconButton(
                            onClick = { viewModel.refreshCacheSize() },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh Cache", tint = FluxTextPrimary)
                        }
                    }
                }
            }

            // Data & Storage
            Text(
                text = "Data Management",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { showClearHistoryDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("btn_clear_history")
                    ) {
                        Icon(Icons.Filled.History, contentDescription = null, tint = FluxAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Watch History", color = FluxTextPrimary)
                    }

                    OutlinedButton(
                        onClick = { showClearBookmarksDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("btn_clear_bookmarks")
                    ) {
                        Icon(Icons.Filled.BookmarkBorder, contentDescription = null, tint = FluxSecondary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Saved Bookmarks", color = FluxTextPrimary)
                    }

                    OutlinedButton(
                        onClick = { showClearDownloadsDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("btn_clear_downloads")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, tint = FluxCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete All Downloaded Videos ($downloadsSize)", color = FluxTextPrimary)
                    }

                    Button(
                        onClick = { showResetUserDataDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("btn_reset_user_data")
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Erase All User Data & Reset", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // App Engine Details
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Fluxplay v2.0 • Pro Media Suite",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = FluxTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Hardware-Accelerated HLS • DASH • MKV • MP4 • Audio/Subtitles",
                        style = MaterialTheme.typography.labelSmall,
                        color = FluxTextSecondary
                    )
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear History?", style = MaterialTheme.typography.titleMedium) },
            text = { Text("Are you sure you want to clear your playback history?", color = FluxTextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearHistoryDialog = false
                    Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Clear", color = FluxAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel", color = FluxTextSecondary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showCleanCacheDialog) {
        AlertDialog(
            onDismissRequest = { showCleanCacheDialog = false },
            title = { Text("Clean Storage Cache?", style = MaterialTheme.typography.titleMedium) },
            text = { Text("This will purge all temporary video streaming chunks, HTTP network responses, and cached artwork to free up space. Your bookmarks, settings, and watch history will be preserved.", color = FluxTextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showCleanCacheDialog = false
                    viewModel.cleanCache { freed ->
                        Toast.makeText(context, "Cleaned $freed of cache storage", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Clean Now", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanCacheDialog = false }) {
                    Text("Cancel", color = FluxTextSecondary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showClearBookmarksDialog) {
        AlertDialog(
            onDismissRequest = { showClearBookmarksDialog = false },
            title = { Text("Clear Bookmarks?", style = MaterialTheme.typography.titleMedium) },
            text = { Text("Are you sure you want to clear all saved items?", color = FluxTextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearBookmarks()
                    showClearBookmarksDialog = false
                    Toast.makeText(context, "Bookmarks cleared", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Clear", color = FluxAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearBookmarksDialog = false }) {
                    Text("Cancel", color = FluxTextSecondary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showClearDownloadsDialog) {
        AlertDialog(
            onDismissRequest = { showClearDownloadsDialog = false },
            title = { Text("Delete All Downloads?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete all offline downloaded videos ($downloadsSize)? This will free up storage immediately.", color = FluxTextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllDownloads {
                        Toast.makeText(context, "All downloaded videos deleted", Toast.LENGTH_SHORT).show()
                    }
                    showClearDownloadsDialog = false
                }) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDownloadsDialog = false }) {
                    Text("Cancel", color = FluxTextSecondary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showResetUserDataDialog) {
        AlertDialog(
            onDismissRequest = { showResetUserDataDialog = false },
            title = { Text("Erase All User Data?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text("This will permanently clear your watch history, bookmarks, offline downloads, and streaming cache. Are you sure you want to proceed?", color = FluxTextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetAllUserData {
                        Toast.makeText(context, "All user data & cache wiped", Toast.LENGTH_SHORT).show()
                    }
                    showResetUserDataDialog = false
                }) {
                    Text("Erase Everything", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetUserDataDialog = false }) {
                    Text("Cancel", color = FluxTextSecondary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
