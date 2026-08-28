package com.example.fluxplay.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.fluxplay.data.model.MediaItemEntity
import com.example.fluxplay.data.model.PlayerEngine
import com.example.fluxplay.data.model.ResizeMode
import com.example.fluxplay.ui.theme.*

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    var showSpeedDialog by remember { mutableStateOf(false) }
    var showTrackDialog by remember { mutableStateOf(false) }
    var showResizeDialog by remember { mutableStateOf(false) }

    // Synchronize system fullscreen & orientation with uiState.isFullscreen
    LaunchedEffect(uiState.isFullscreen) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (uiState.isFullscreen) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Handle back button when in fullscreen
    BackHandler(enabled = uiState.isFullscreen) {
        viewModel.setFullscreen(false)
    }

    // Apply brightness to window if changed
    LaunchedEffect(uiState.brightnessLevel) {
        activity?.let { act ->
            val lp = act.window.attributes
            if (uiState.brightnessLevel > 0f) {
                lp.screenBrightness = uiState.brightnessLevel
                act.window.attributes = lp
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        if (uiState.isFullscreen) {
            // Fullscreen mode: Single player container fills the entire screen
            PlayerCoreSurface(
                viewModel = viewModel,
                uiState = uiState,
                modifier = Modifier.fillMaxSize(),
                onOpenSpeed = { showSpeedDialog = true },
                onOpenTracks = { showTrackDialog = true },
                onOpenResize = { showResizeDialog = true }
            )
        } else {
            // Portrait/Embedded mode: Player at top, details and stream list below
            Column(modifier = Modifier.fillMaxSize()) {
                PlayerCoreSurface(
                    viewModel = viewModel,
                    uiState = uiState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    onOpenSpeed = { showSpeedDialog = true },
                    onOpenTracks = { showTrackDialog = true },
                    onOpenResize = { showResizeDialog = true }
                )

                PlayerMetadataSection(
                    uiState = uiState,
                    viewModel = viewModel,
                    onOpenSpeed = { showSpeedDialog = true },
                    onOpenTracks = { showTrackDialog = true },
                    onOpenResize = { showResizeDialog = true }
                )
            }
        }

        // Speed Selection Dialog
        if (showSpeedDialog) {
            SpeedSelectionDialog(
                currentSpeed = uiState.playbackSpeed,
                onSpeedSelected = {
                    viewModel.setPlaybackSpeed(it)
                    showSpeedDialog = false
                },
                onDismiss = { showSpeedDialog = false }
            )
        }

        // Audio & Subtitles Dialog
        if (showTrackDialog) {
            TrackSelectionDialog(
                audioTracks = uiState.audioTracks,
                subtitleTracks = uiState.subtitleTracks,
                selectedAudioId = uiState.selectedAudioTrackId,
                selectedSubId = uiState.selectedSubtitleTrackId,
                onSelectAudio = {
                    viewModel.selectAudioTrack(it)
                    showTrackDialog = false
                },
                onSelectSub = {
                    viewModel.selectSubtitleTrack(it)
                    showTrackDialog = false
                },
                onDismiss = { showTrackDialog = false }
            )
        }

        // Resize Mode Dialog
        if (showResizeDialog) {
            ResizeModeDialog(
                currentMode = uiState.resizeMode,
                onModeSelected = {
                    viewModel.setResizeMode(it)
                    showResizeDialog = false
                },
                onDismiss = { showResizeDialog = false }
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerCoreSurface(
    viewModel: PlayerViewModel,
    uiState: PlayerUiState,
    modifier: Modifier = Modifier,
    onOpenSpeed: () -> Unit,
    onOpenTracks: () -> Unit,
    onOpenResize: () -> Unit
) {
    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { viewModel.toggleControls() },
                    onDoubleTap = { offset ->
                        val width = size.width
                        if (offset.x < width / 2) {
                            viewModel.seekRelative(-uiState.settings.doubleTapSeekSeconds)
                        } else {
                            viewModel.seekRelative(uiState.settings.doubleTapSeekSeconds)
                        }
                    }
                )
            }
            .pointerInput(uiState.settings) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    val positionX = change.position.x
                    val width = size.width
                    val delta = -dragAmount / 500f

                    if (positionX < width / 2 && uiState.settings.gestureBrightness) {
                        val current = if (uiState.brightnessLevel < 0f) 0.5f else uiState.brightnessLevel
                        viewModel.setBrightness(current + delta)
                    } else if (positionX >= width / 2 && uiState.settings.gestureVolume) {
                        viewModel.setVolume(uiState.volumeLevel + delta)
                    }
                }
            }
    ) {
        // Player Surface Engine: ExoPlayer or LibMPV
        if (uiState.selectedEngine == PlayerEngine.EXOPLAYER) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = viewModel.exoPlayer
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        resizeMode = when (uiState.resizeMode) {
                            ResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            ResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            ResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            ResizeMode.ORIGINAL_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                            ResizeMode.ORIGINAL_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                        }
                    }
                },
                update = { view ->
                    view.player = viewModel.exoPlayer
                    view.resizeMode = when (uiState.resizeMode) {
                        ResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        ResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        ResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        ResizeMode.ORIGINAL_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                        ResizeMode.ORIGINAL_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    MPVPlayerViewWrapper(ctx).apply {
                        setOnMpvReadyListener { wrapper ->
                            viewModel.registerMpv(wrapper.mpv)
                        }
                        initPlayer()
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Loading indicator
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = IndigoPrimary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // Error Banner
        if (uiState.errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = ErrorRed,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = uiState.errorMessage ?: "Unknown error",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                val altEngine = if (uiState.selectedEngine == PlayerEngine.EXOPLAYER)
                                    PlayerEngine.LIBMPV else PlayerEngine.EXOPLAYER
                                viewModel.switchEngine(altEngine)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                        ) {
                            Text("Try ${if (uiState.selectedEngine == PlayerEngine.EXOPLAYER) "MPV" else "ExoPlayer"}")
                        }
                        OutlinedButton(
                            onClick = { viewModel.clearError() }
                        ) {
                            Text("Dismiss", color = TextPrimary)
                        }
                    }
                }
            }
        }

        // Player Controls Overlay
        AnimatedVisibility(
            visible = uiState.showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            PlayerControlsOverlay(
                uiState = uiState,
                viewModel = viewModel,
                onOpenSpeed = onOpenSpeed,
                onOpenTracks = onOpenTracks,
                onOpenResize = onOpenResize
            )
        }
    }
}

@Composable
fun PlayerControlsOverlay(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel,
    onOpenSpeed: () -> Unit,
    onOpenTracks: () -> Unit,
    onOpenResize: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.7f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.85f)
                    )
                )
            )
            .padding(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiState.currentMedia?.title ?: "Ready to Play",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${uiState.selectedEngine.name} • ${uiState.currentMedia?.groupTitle ?: "Live/Stream"}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Engine toggle pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSurfaceVariant,
                    modifier = Modifier.clickable {
                        val next = if (uiState.selectedEngine == PlayerEngine.EXOPLAYER)
                            PlayerEngine.LIBMPV else PlayerEngine.EXOPLAYER
                        viewModel.switchEngine(next)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Switch Engine",
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.selectedEngine == PlayerEngine.EXOPLAYER) "EXO" else "MPV",
                            color = TextPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.toggleBookmark() },
                    modifier = Modifier.testTag("player_bookmark_btn")
                ) {
                    Icon(
                        imageVector = if (uiState.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Bookmark",
                        tint = if (uiState.isBookmarked) IndigoPrimary else TextPrimary
                    )
                }

                IconButton(onClick = onOpenTracks) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = "Tracks",
                        tint = TextPrimary
                    )
                }
            }
        }

        // Center Action Controls
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            IconButton(
                onClick = { viewModel.seekRelative(-uiState.settings.doubleTapSeekSeconds) },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Replay10,
                    contentDescription = "Rewind",
                    tint = TextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(
                onClick = { viewModel.togglePlayPause() },
                modifier = Modifier
                    .size(64.dp)
                    .background(IndigoPrimary, CircleShape)
                    .testTag("play_pause_button")
            ) {
                Icon(
                    imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(
                onClick = { viewModel.seekRelative(uiState.settings.doubleTapSeekSeconds) },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Forward10,
                    contentDescription = "Forward",
                    tint = TextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Bottom Controls: Progress bar, duration, fullscreen & speed
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            // Seek bar
            val currentPos = uiState.currentPositionMs.toFloat()
            val totalDur = uiState.durationMs.coerceAtLeast(1L).toFloat()

            Slider(
                value = (currentPos / totalDur).coerceIn(0f, 1f),
                onValueChange = { fraction ->
                    val targetMs = (fraction * totalDur).toLong()
                    viewModel.seekTo(targetMs)
                },
                colors = SliderDefaults.colors(
                    thumbColor = IndigoPrimary,
                    activeTrackColor = IndigoPrimary,
                    inactiveTrackColor = DarkSurfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("player_seek_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${formatTime(uiState.currentPositionMs)} / ${if (uiState.durationMs > 0) formatTime(uiState.durationMs) else "LIVE"}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Speed button
                    TextButton(onClick = onOpenSpeed) {
                        Text(
                            text = "${uiState.playbackSpeed}x",
                            color = CyanAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Resize Mode button
                    IconButton(onClick = onOpenResize) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = "Aspect Ratio",
                            tint = TextPrimary
                        )
                    }

                    // Fullscreen toggle button
                    IconButton(
                        onClick = { viewModel.toggleFullscreen() },
                        modifier = Modifier.testTag("fullscreen_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (uiState.isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Toggle Fullscreen",
                            tint = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerMetadataSection(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel,
    onOpenSpeed: () -> Unit,
    onOpenTracks: () -> Unit,
    onOpenResize: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiState.currentMedia?.title ?: "Select a stream to begin",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uiState.currentMedia?.uri ?: "HLS / DASH / MP4 / IPTV compatible",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Technical stream info badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BadgeChip(
                label = "Engine: ${uiState.selectedEngine.name}",
                color = IndigoPrimary
            )
            BadgeChip(
                label = "Aspect: ${uiState.resizeMode.name}",
                color = CyanAccent
            )
            BadgeChip(
                label = "HW Dec: ${if (uiState.settings.hardwareAcceleration) "ON" else "OFF"}",
                color = if (uiState.settings.hardwareAcceleration) SuccessGreen else TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Quick action row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuickActionButton(
                icon = Icons.Default.Speed,
                label = "Speed (${uiState.playbackSpeed}x)",
                onClick = onOpenSpeed
            )
            QuickActionButton(
                icon = Icons.Default.Subtitles,
                label = "Tracks & Audio",
                onClick = onOpenTracks
            )
            QuickActionButton(
                icon = Icons.Default.FitScreen,
                label = "Resize Mode",
                onClick = onOpenResize
            )
            QuickActionButton(
                icon = if (uiState.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                label = if (uiState.isBookmarked) "Saved" else "Save",
                tint = if (uiState.isBookmarked) IndigoPrimary else TextPrimary,
                onClick = { viewModel.toggleBookmark() }
            )
        }
    }
}

@Composable
fun BadgeChip(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = TextPrimary,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = DarkSurfaceVariant,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, fontSize = 11.sp)
    }
}

@Composable
fun SpeedSelectionDialog(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback Speed", color = TextPrimary) },
        text = {
            Column {
                speeds.forEach { speed ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSpeedSelected(speed) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${speed}x",
                            color = if (speed == currentSpeed) IndigoPrimary else TextPrimary,
                            fontWeight = if (speed == currentSpeed) FontWeight.Bold else FontWeight.Normal
                        )
                        if (speed == currentSpeed) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = IndigoPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = DarkSurface
    )
}

@Composable
fun ResizeModeDialog(
    currentMode: ResizeMode,
    onModeSelected: (ResizeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video Aspect Ratio", color = TextPrimary) },
        text = {
            Column {
                ResizeMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModeSelected(mode) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = mode.name.replace('_', ' '),
                            color = if (mode == currentMode) IndigoPrimary else TextPrimary,
                            fontWeight = if (mode == currentMode) FontWeight.Bold else FontWeight.Normal
                        )
                        if (mode == currentMode) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = IndigoPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = DarkSurface
    )
}

@Composable
fun TrackSelectionDialog(
    audioTracks: List<com.example.fluxplay.data.model.MediaTrackInfo>,
    subtitleTracks: List<com.example.fluxplay.data.model.MediaTrackInfo>,
    selectedAudioId: String?,
    selectedSubId: String?,
    onSelectAudio: (com.example.fluxplay.data.model.MediaTrackInfo) -> Unit,
    onSelectSub: (com.example.fluxplay.data.model.MediaTrackInfo?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio & Subtitle Tracks", color = TextPrimary) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Audio Tracks",
                    color = CyanAccent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (audioTracks.isEmpty()) {
                    Text("Default Audio Track", color = TextSecondary, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    audioTracks.forEach { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectAudio(track) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = track.label, color = if (track.id == selectedAudioId) IndigoPrimary else TextPrimary)
                            if (track.id == selectedAudioId) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = IndigoPrimary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Subtitles",
                    color = CyanAccent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSub(null) }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Off / None", color = if (selectedSubId == null) IndigoPrimary else TextPrimary)
                    if (selectedSubId == null) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = IndigoPrimary)
                    }
                }

                subtitleTracks.forEach { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSub(track) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = track.label, color = if (track.id == selectedSubId) IndigoPrimary else TextPrimary)
                        if (track.id == selectedSubId) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = IndigoPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = TextSecondary) }
        },
        containerColor = DarkSurface
    )
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
