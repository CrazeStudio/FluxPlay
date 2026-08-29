package com.example.fluxplay.ui.downloads

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.fluxplay.data.model.DownloadItemEntity
import com.example.fluxplay.data.model.DownloadStatus
import com.example.fluxplay.data.model.MediaItemEntity
import com.example.fluxplay.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onPlayMedia: (MediaItemEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val downloads by viewModel.filteredDownloads.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val totalStorage by viewModel.totalStorageFormatted.collectAsStateWithLifecycle()

    var showDownloadDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<DownloadItemEntity?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshStorage()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Downloads",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Offline Storage: $totalStorage",
                                style = MaterialTheme.typography.labelMedium,
                                color = FluxCyan
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (downloads.isNotEmpty()) {
                                IconButton(
                                    onClick = { showClearAllDialog = true },
                                    modifier = Modifier.testTag("clear_all_downloads_btn")
                                ) {
                                    Icon(
                                        Icons.Outlined.DeleteSweep,
                                        contentDescription = "Clear All",
                                        tint = FluxTextMuted
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = { showDownloadDialog = true },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("new_download_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Download", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = { Text("Search downloaded videos...", color = FluxTextMuted) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = FluxTextMuted) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = FluxTextMuted)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("downloads_search_input")
                    )
                }
            }

            // Quick Samples Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Quick Test Video Downloads",
                    style = MaterialTheme.typography.labelSmall,
                    color = FluxTextMuted,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        SampleDownloadChip(
                            title = "Big Buck Bunny (MP4)",
                            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                            poster = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
                            onDownload = { u, t, p -> viewModel.startDownload(u, t, p) }
                        )
                    }
                    item {
                        SampleDownloadChip(
                            title = "Tears of Steel (MP4)",
                            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                            poster = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/TearsOfSteel.jpg",
                            onDownload = { u, t, p -> viewModel.startDownload(u, t, p) }
                        )
                    }
                    item {
                        SampleDownloadChip(
                            title = "For Bigger Blazes",
                            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                            poster = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerBlazes.jpg",
                            onDownload = { u, t, p -> viewModel.startDownload(u, t, p) }
                        )
                    }
                    item {
                        SampleDownloadChip(
                            title = "Elephants Dream",
                            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                            poster = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ElephantsDream.jpg",
                            onDownload = { u, t, p -> viewModel.startDownload(u, t, p) }
                        )
                    }
                }
            }

            // Downloads List
            if (downloads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.CloudDownload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching downloads" else "No Downloaded Videos Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Download full videos from direct links or sample videos to watch offline anytime with zero buffering.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FluxTextMuted,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Button(
                            onClick = { showDownloadDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("empty_state_download_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download Video by URL", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(downloads, key = { it.id }) { item ->
                        DownloadItemCard(
                            item = item,
                            onClick = {
                                if (item.status == DownloadStatus.COMPLETED) {
                                    onPlayMedia(viewModel.mapToMediaItem(item))
                                }
                            },
                            onLongClick = {
                                itemToDelete = item
                            },
                            onDelete = {
                                itemToDelete = item
                            },
                            onCancel = {
                                viewModel.cancelDownload(item.id)
                            },
                            onRetry = {
                                viewModel.startDownload(item.url, item.title, item.poster)
                            }
                        )
                    }
                }
            }
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = { showDownloadDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = FluxBgDark,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("fab_download_video")
        ) {
            Icon(Icons.Filled.Download, contentDescription = "Download Video")
        }
    }

    // Delete Confirmation Dialog
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Downloaded Video?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to delete \"${item.title}\"? This will remove the offline video file from your device storage."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDownload(item)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Video", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear All Confirmation Dialog
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear All Downloads?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete all downloaded video files from storage?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllDownloads()
                        showClearAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete All", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Download by URL Dialog
    if (showDownloadDialog) {
        DownloadUrlDialog(
            onDismiss = { showDownloadDialog = false },
            onStartDownload = { url, title ->
                viewModel.startDownload(url, title)
                showDownloadDialog = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadItemCard(
    item: DownloadItemEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val isCompleted = item.status == DownloadStatus.COMPLETED
    val isDownloading = item.status == DownloadStatus.DOWNLOADING
    val isFailed = item.status == DownloadStatus.FAILED || item.status == DownloadStatus.CANCELLED

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDownloading) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("download_card_${item.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Thumbnail / Video Icon Box
                Box(
                    modifier = Modifier
                        .size(width = 72.dp, height = 54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.poster.isNotBlank()) {
                        AsyncImage(
                            model = item.poster,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (isCompleted) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            shape = CircleShape,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = "Play",
                                    tint = FluxBgDark,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else if (isDownloading) {
                        CircularProgressIndicator(
                            progress = { item.progressPercent / 100f },
                            modifier = Modifier.size(26.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.Filled.VideoFile,
                            contentDescription = null,
                            tint = FluxTextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Info Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = if (isCompleted) FluxEmerald.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = item.format,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isCompleted) FluxEmerald else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }

                        if (isCompleted) {
                            Text(
                                text = formatBytes(item.totalBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = FluxTextMuted
                            )
                            Text(
                                text = "• Ready to watch",
                                style = MaterialTheme.typography.labelSmall,
                                color = FluxEmerald
                            )
                        } else if (isDownloading) {
                            Text(
                                text = "${item.progressPercent}% • ${item.speedFormatted}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else if (isFailed) {
                            Text(
                                text = item.errorMessage ?: "Failed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Actions
                if (isDownloading) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                    }
                } else if (isFailed) {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Retry", tint = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = FluxTextMuted)
                    }
                }
            }

            // Progress Bar if Downloading
            if (isDownloading) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { item.progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = FluxTextMuted
                    )
                    Text(
                        text = "Downloading...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun SampleDownloadChip(
    title: String,
    url: String,
    poster: String,
    onDownload: (String, String, String) -> Unit
) {
    SuggestionChip(
        onClick = { onDownload(url, title, poster) },
        label = { Text(title, style = MaterialTheme.typography.labelSmall) },
        icon = { Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(14.dp)) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurface,
            iconContentColor = MaterialTheme.colorScheme.primary
        ),
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun DownloadUrlDialog(
    onDismiss: () -> Unit,
    onStartDownload: (String, String) -> Unit
) {
    var urlText by remember { mutableStateOf("") }
    var titleText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download Video", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter direct video URL (.mp4, .mkv, .webm, etc.) to download locally:",
                    style = MaterialTheme.typography.bodySmall,
                    color = FluxTextMuted
                )
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Video URL (HTTPS / HTTP)") },
                    placeholder = { Text("https://example.com/video.mp4") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("download_url_input")
                )
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Video Title (Optional)") },
                    placeholder = { Text("My Offline Movie") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("download_title_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (urlText.isNotBlank()) {
                        onStartDownload(urlText.trim(), titleText.trim())
                    }
                },
                enabled = urlText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("start_download_submit_btn")
            ) {
                Text("Start Download", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
