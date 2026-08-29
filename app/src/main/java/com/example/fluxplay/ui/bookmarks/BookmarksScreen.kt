package com.example.fluxplay.ui.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.fluxplay.data.model.MediaItemEntity
import com.example.fluxplay.ui.history.HistoryViewModel
import com.example.fluxplay.ui.theme.*
import com.example.fluxplay.util.MediaTitleFormatter

@Composable
fun BookmarksScreen(
    viewModel: HistoryViewModel,
    onPlayMedia: (MediaItemEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val bookmarks by viewModel.bookmarkedItems.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Saved Streams",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            if (bookmarks.isNotEmpty()) {
                TextButton(onClick = { viewModel.clearBookmarks() }) {
                    Text("Clear All", color = FluxAccent)
                }
            }
        }

        if (bookmarks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "No saved streams yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = FluxTextSecondary
                    )
                    Text(
                        text = "Tap the bookmark icon on any stream to save it here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FluxTextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(bookmarks, key = { it.url }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayMedia(item) }
                            .testTag("bookmark_item_${item.providerId}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = FluxCardDark)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (item.poster.isNotBlank()) {
                                AsyncImage(
                                    model = item.poster,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(70.dp)
                                        .height(50.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                            } else {
                                Surface(
                                    color = FluxSurfaceVariantDark,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .width(70.dp)
                                        .height(50.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (item.url.contains(".m3u8")) Icons.Filled.Sensors else if (item.provider == "local") Icons.Filled.VideoFile else Icons.Filled.PlayCircle,
                                            contentDescription = null,
                                            tint = FluxSecondary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val cleanTitle = MediaTitleFormatter.extractCleanTitle(item.title, item.url)
                                Text(
                                    text = cleanTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FluxTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.type,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = FluxTextSecondary,
                                    fontSize = 10.sp
                                )
                            }

                            IconButton(onClick = { viewModel.toggleBookmark(item) }) {
                                Icon(
                                    Icons.Filled.BookmarkRemove,
                                    contentDescription = "Remove bookmark",
                                    tint = FluxAccent,
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
