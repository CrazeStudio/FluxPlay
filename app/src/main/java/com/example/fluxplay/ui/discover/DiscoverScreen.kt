package com.example.fluxplay.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.fluxplay.data.model.MediaItemEntity
import com.example.fluxplay.data.model.PlayerEngine
import com.example.fluxplay.ui.theme.*

@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel,
    onSelectMedia: (MediaItemEntity, PlayerEngine) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedMediaForSheet by remember { mutableStateOf<MediaItemEntity?>(null) }
    var isM3uDialogVisible by remember { mutableStateOf(false) }

    val filteredStreams = remember(uiState.streamList, uiState.selectedCategory, uiState.searchQuery) {
        uiState.streamList.filter { item ->
            val matchCat = uiState.selectedCategory == "All" || item.groupTitle == uiState.selectedCategory
            val matchQuery = uiState.searchQuery.isBlank() ||
                    item.title.contains(uiState.searchQuery, ignoreCase = true) ||
                    item.uri.contains(uiState.searchQuery, ignoreCase = true)
            matchCat && matchQuery
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Streams & IPTV",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    IconButton(
                        onClick = { isM3uDialogVisible = true },
                        modifier = Modifier.testTag("import_m3u_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddLink,
                            contentDescription = "Import Playlist",
                            tint = CyanAccent
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Search streams or paste URL...", color = TextSecondary) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary)
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stream_search_field"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        focusedBorderColor = IndigoPrimary,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Category Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(uiState.categories) { category ->
                        val isSelected = category == uiState.selectedCategory
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectCategory(category) },
                            label = { Text(category, fontSize = 13.sp) },
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
        },
        containerColor = DarkBackground,
        modifier = modifier
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    color = IndigoPrimary,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (filteredStreams.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.TvOff,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No streams found",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Try searching something else or add a custom M3U playlist",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredStreams, key = { it.id }) { stream ->
                        StreamItemCard(
                            media = stream,
                            onClick = { selectedMediaForSheet = stream },
                            onQuickPlay = { onSelectMedia(stream, PlayerEngine.EXOPLAYER) }
                        )
                    }
                }
            }
        }

        // Details Bottom Sheet
        selectedMediaForSheet?.let { media ->
            MediaDetailSheet(
                media = media,
                onPlayExo = {
                    selectedMediaForSheet = null
                    onSelectMedia(media, PlayerEngine.EXOPLAYER)
                },
                onPlayMpv = {
                    selectedMediaForSheet = null
                    onSelectMedia(media, PlayerEngine.LIBMPV)
                },
                isBookmarked = media.isBookmark,
                onToggleBookmark = {
                    viewModel.toggleBookmark(media)
                },
                onDismiss = { selectedMediaForSheet = null }
            )
        }

        // M3U URL Dialog
        if (isM3uDialogVisible) {
            var urlText by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { isM3uDialogVisible = false },
                title = { Text("Import M3U / M3U8 Playlist", color = TextPrimary) },
                text = {
                    Column {
                        Text(
                            "Enter the URL of your IPTV / M3U playlist or live stream feed:",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = urlText,
                            onValueChange = { urlText = it },
                            placeholder = { Text("https://example.com/playlist.m3u") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("m3u_input_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = IndigoPrimary,
                                unfocusedBorderColor = DarkSurfaceVariant
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.importM3uPlaylist(urlText)
                            isM3uDialogVisible = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                    ) {
                        Text("Import")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isM3uDialogVisible = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = DarkSurface
            )
        }
    }
}

@Composable
fun StreamItemCard(
    media: MediaItemEntity,
    onClick: () -> Unit,
    onQuickPlay: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DarkSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (media.thumbnailUri != null) {
                AsyncImage(
                    model = media.thumbnailUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DarkSurfaceVariant,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayCircleOutline,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = media.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = media.groupTitle ?: media.mediaType.name,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onQuickPlay,
                modifier = Modifier
                    .size(40.dp)
                    .background(IndigoPrimary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = IndigoPrimary
                )
            }
        }
    }
}
