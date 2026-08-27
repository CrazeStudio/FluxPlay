package com.example.fluxplay.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.fluxplay.data.model.MediaItemEntity
import com.example.fluxplay.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailSheet(
    media: MediaItemEntity,
    onDismiss: () -> Unit,
    onPlay: (MediaItemEntity) -> Unit,
    onToggleBookmark: (MediaItemEntity) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = FluxSurfaceDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = FluxCardBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AsyncImage(
                    model = media.poster,
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(100.dp)
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(FluxCardDark)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = media.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (media.year.isNotEmpty()) {
                            Text(text = media.year, style = MaterialTheme.typography.labelSmall, color = FluxTextSecondary)
                        }
                        if (media.rating.isNotEmpty()) {
                            Surface(color = FluxAmber.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = "★ ${media.rating}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = FluxAmber,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (media.duration.isNotEmpty()) {
                            Text(text = media.duration, style = MaterialTheme.typography.labelSmall, color = FluxTextMuted)
                        }
                    }
                    if (media.source.isNotEmpty()) {
                        Text(
                            text = "Provider: ${media.source}",
                            style = MaterialTheme.typography.labelSmall,
                            color = FluxCyan
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        onPlay(media)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FluxPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("detail_play_button")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = FluxBgDark)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stream Now", color = FluxBgDark, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onToggleBookmark(media) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FluxTextPrimary),
                    modifier = Modifier.testTag("detail_bookmark_button")
                ) {
                    Icon(
                        imageVector = if (media.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Bookmark",
                        tint = if (media.isBookmarked) FluxAccent else FluxTextSecondary
                    )
                }
            }

            // Genres
            if (media.genres.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    media.genres.forEach { genre ->
                        Surface(
                            color = FluxCardDark,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = genre,
                                style = MaterialTheme.typography.labelSmall,
                                color = FluxPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Synopsis
            if (media.synopsis.isNotEmpty()) {
                Text(
                    text = "Overview",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = media.synopsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FluxTextSecondary,
                    lineHeight = 22.sp
                )
            }

            // Cast / Studios
            if (media.cast.isNotEmpty()) {
                Text(
                    text = "Cast & Crew",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = media.cast.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FluxTextSecondary
                )
            }
        }
    }
}
