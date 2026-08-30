package com.example.fluxplay.ui.discover

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fluxplay.data.model.MediaItemEntity
import com.example.fluxplay.ui.player.getFileName
import com.example.fluxplay.ui.theme.*
import com.example.fluxplay.util.MediaTitleFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel,
    onPlayMedia: (MediaItemEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var showAddStreamDialog by remember { mutableStateOf(false) }
    var showImportM3uDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            val fileName = getFileName(context, it)
            val item = MediaItemEntity(
                url = it.toString(),
                title = fileName,
                poster = "",
                year = "Local",
                type = "Local File",
                source = "Device Storage",
                provider = "local",
                providerId = "local_${System.currentTimeMillis()}"
            )
            onPlayMedia(item)
        }
    }

    LaunchedEffect(state.importMessage) {
        state.importMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearImportMessage()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar & Action Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Streams & Library",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = { showAddStreamDialog = true },
                            modifier = Modifier
                                .background(FluxSurfaceVariantDark, RoundedCornerShape(8.dp))
                                .size(36.dp)
                                .testTag("btn_add_stream_url")
                        ) {
                            Icon(Icons.Filled.AddLink, contentDescription = "Add Stream", tint = FluxPrimary, modifier = Modifier.size(20.dp))
                        }

                        IconButton(
                            onClick = { showImportM3uDialog = true },
                            modifier = Modifier
                                .background(FluxSurfaceVariantDark, RoundedCornerShape(8.dp))
                                .size(36.dp)
                                .testTag("btn_import_m3u")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Import M3U", tint = FluxSecondary, modifier = Modifier.size(20.dp))
                        }

                        IconButton(
                            onClick = { filePickerLauncher.launch(arrayOf("video/*", "audio/*", "application/octet-stream", "*/*")) },
                            modifier = Modifier
                                .background(FluxSurfaceVariantDark, RoundedCornerShape(8.dp))
                                .size(36.dp)
                                .testTag("btn_pick_file")
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = "Open File", tint = FluxEmerald, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Search streams or URLs...", color = FluxTextMuted, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = FluxTextSecondary) },
                    trailingIcon = {
                        if (state.searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = FluxTextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = FluxCardDark,
                        unfocusedContainerColor = FluxCardDark,
                        focusedBorderColor = FluxPrimary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_search_streams")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Filter Pills
                val filters = listOf(
                    "all" to "All",
                    "hls" to "HLS Streams",
                    "direct" to "Direct Video",
                    "local" to "Local Files",
                    "iptv" to "IPTV / Playlist"
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filters) { (id, label) ->
                        val isSelected = state.selectedFilter == id
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.onFilterChanged(id) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FluxPrimary,
                                selectedLabelColor = FluxBgDark,
                                containerColor = FluxSurfaceVariantDark,
                                labelColor = FluxTextSecondary
                            ),
                            border = null
                        )
                    }
                }
            }
        }

        // Stream List or Empty State
        if (state.filteredStreams.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = FluxCardDark),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LiveTv,
                            contentDescription = null,
                            tint = FluxSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (state.searchQuery.isNotBlank()) "No Matching Streams" else "No Streams Added",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = FluxTextPrimary
                        )
                        Text(
                            text = if (state.searchQuery.isNotBlank()) "Try a different search query." else "Add a stream link, open a local video file, or import an M3U playlist to begin streaming.",
                            style = MaterialTheme.typography.bodySmall,
                            color = FluxTextSecondary
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showAddStreamDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = FluxPrimary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Add URL", color = FluxBgDark, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = { showImportM3uDialog = true },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Import M3U")
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().testTag("streams_list")
            ) {
                items(
                    count = state.filteredStreams.size,
                    key = { index -> "${state.filteredStreams[index].providerId}_${state.filteredStreams[index].url}_$index" }
                ) { index ->
                    val item = state.filteredStreams[index]
                    Surface(
                        color = FluxCardDark,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPlayMedia(item) }
                            .testTag("stream_card_${item.providerId}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Icon container
                            Surface(
                                color = FluxSurfaceVariantDark,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = when {
                                            item.url.contains(".m3u8") -> Icons.Filled.Sensors
                                            item.provider == "local" -> Icons.Filled.VideoFile
                                            item.provider == "playlist" -> Icons.Filled.Tv
                                            else -> Icons.Filled.PlayCircle
                                        },
                                        contentDescription = null,
                                        tint = FluxPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Stream Title & Info
                            Column(modifier = Modifier.weight(1f)) {
                                val cleanTitle = MediaTitleFormatter.extractCleanTitle(item.title, item.url, context)
                                val subtitleText = when {
                                    item.provider == "local" -> "Local File"
                                    item.provider == "playlist" -> item.source.ifBlank { "Playlist Stream" }
                                    item.url.startsWith("http") -> {
                                        try {
                                            val uri = Uri.parse(item.url)
                                            uri.host?.removePrefix("www.") ?: item.source
                                        } catch (_: Exception) {
                                            item.source
                                        }
                                    }
                                    else -> item.source
                                }

                                Text(
                                    text = cleanTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FluxTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = FluxPrimary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = item.type,
                                            color = FluxPrimary,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                    Text(
                                        text = subtitleText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = FluxTextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Actions
                            Row {
                                IconButton(
                                    onClick = { viewModel.toggleBookmark(item) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (item.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                        contentDescription = "Bookmark",
                                        tint = if (item.isBookmarked) FluxSecondary else FluxTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.deleteStream(item) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Delete",
                                        tint = FluxAccent.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Stream Dialog
    if (showAddStreamDialog) {
        var inputUrl by remember { mutableStateOf("") }
        var inputTitle by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddStreamDialog = false },
            title = { Text("Add Stream URL", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        label = { Text("Stream URL (.m3u8, .mp4, etc.)") },
                        placeholder = { Text("https://example.com/live.m3u8") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { clipboard.getText()?.let { inputUrl = it.text } }) {
                                Icon(Icons.Filled.ContentPaste, contentDescription = "Paste", tint = FluxPrimary)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("input_new_stream_url")
                    )

                    OutlinedTextField(
                        value = inputTitle,
                        onValueChange = { inputTitle = it },
                        label = { Text("Stream Title (Optional)") },
                        placeholder = { Text("e.g. Sports HD") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputUrl.isNotBlank()) {
                            viewModel.addStream(inputUrl, inputTitle)
                            showAddStreamDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FluxPrimary),
                    modifier = Modifier.testTag("btn_save_stream")
                ) {
                    Text("Save", color = FluxBgDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddStreamDialog = false }) {
                    Text("Cancel", color = FluxTextSecondary)
                }
            },
            containerColor = FluxSurfaceDark
        )
    }

    // Import M3U Dialog
    if (showImportM3uDialog) {
        var tabIndex by remember { mutableStateOf(0) }
        var m3uUrl by remember { mutableStateOf("") }
        var m3uContent by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showImportM3uDialog = false },
            title = { Text("Import M3U Playlist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TabRow(
                        selectedTabIndex = tabIndex,
                        containerColor = FluxSurfaceVariantDark,
                        contentColor = FluxTextPrimary
                    ) {
                        Tab(
                            selected = tabIndex == 0,
                            onClick = { tabIndex = 0 },
                            text = { Text("Playlist URL") }
                        )
                        Tab(
                            selected = tabIndex == 1,
                            onClick = { tabIndex = 1 },
                            text = { Text("Paste M3U") }
                        )
                    }

                    if (tabIndex == 0) {
                        OutlinedTextField(
                            value = m3uUrl,
                            onValueChange = { m3uUrl = it },
                            placeholder = { Text("https://example.com/playlist.m3u") },
                            trailingIcon = {
                                IconButton(onClick = { clipboard.getText()?.let { m3uUrl = it.text } }) {
                                    Icon(Icons.Filled.ContentPaste, contentDescription = "Paste", tint = FluxSecondary)
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_m3u_url")
                        )
                    } else {
                        OutlinedTextField(
                            value = m3uContent,
                            onValueChange = { m3uContent = it },
                            placeholder = { Text("#EXTM3U\n#EXTINF:-1,Channel 1\nhttp://...") },
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth().height(120.dp).testTag("input_m3u_text")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tabIndex == 0 && m3uUrl.isNotBlank()) {
                            viewModel.importM3uUrl(m3uUrl)
                            showImportM3uDialog = false
                        } else if (tabIndex == 1 && m3uContent.isNotBlank()) {
                            viewModel.importM3uText(m3uContent)
                            showImportM3uDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FluxSecondary),
                    modifier = Modifier.testTag("btn_confirm_import_m3u")
                ) {
                    Text("Import", color = FluxBgDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportM3uDialog = false }) {
                    Text("Cancel", color = FluxTextSecondary)
                }
            },
            containerColor = FluxSurfaceDark
        )
    }
}
