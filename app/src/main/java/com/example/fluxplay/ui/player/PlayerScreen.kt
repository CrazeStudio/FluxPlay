package com.example.fluxplay.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalClipboardManager
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.example.fluxplay.data.model.MediaItemEntity
import com.example.fluxplay.data.model.PlayerEngine
import com.example.fluxplay.ui.theme.*

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            }
        } catch (_: Exception) {}
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result?.ifBlank { "Offline Video" } ?: "Offline Video"
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = (ms / 1000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()

    var showCustomUrlDialog by remember { mutableStateOf(false) }
    var showAudioTrackSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showEngineSheet by remember { mutableStateOf(false) }

    // File Picker for Offline & Local Videos with persistable permission support
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            val fileName = getFileName(context, it)
            viewModel.openLocalFile(it, fileName)
        }
    }

    // Orientation & Fullscreen handling (Immersive sticky mode)
    DisposableEffect(state.isFullscreen) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (state.isFullscreen) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            val win = activity?.window
            if (win != null) {
                val insetsController = WindowCompat.getInsetsController(win, win.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Brightness adjustment on window
    LaunchedEffect(state.brightnessLevel) {
        activity?.window?.attributes = activity?.window?.attributes?.apply {
            screenBrightness = state.brightnessLevel
        }
    }

    BackHandler(enabled = state.isFullscreen) {
        viewModel.setFullscreen(false)
    }

    if (state.isFullscreen) {
        // ==========================================
        // FULLSCREEN JWX PRO PLAYER
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Android ExoPlayer View
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        resizeMode = state.resizeMode.exoMode
                        player = viewModel.getExoPlayer()
                    }
                },
                update = { view ->
                    view.player = viewModel.getExoPlayer()
                    view.resizeMode = state.resizeMode.exoMode
                },
                modifier = Modifier.fillMaxSize().testTag("fullscreen_player_view")
            )

            // Touch & Gesture Interaction Surface
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(state.isControlsLocked) {
                        detectTapGestures(
                            onTap = {
                                viewModel.toggleControlsVisibility()
                            },
                            onDoubleTap = { offset ->
                                if (!state.isControlsLocked) {
                                    val isRight = offset.x > size.width / 2
                                    viewModel.seekRelative(if (isRight) 10000L else -10000L)
                                }
                            }
                        )
                    }
                    .pointerInput(state.isControlsLocked) {
                        if (!state.isControlsLocked) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val isRight = change.position.x > size.width / 2
                                val delta = -dragAmount.y / 700f
                                if (isRight) {
                                    viewModel.setVolumeDelta(delta)
                                } else {
                                    viewModel.setBrightnessDelta(delta)
                                }
                            }
                        }
                    }
            )

            // Gesture HUD Pill (e.g., +10s, Volume, Brightness)
            state.gestureIndicatorText?.let { text ->
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                ) {
                    Text(
                        text = text,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }

            // Buffering Spinner
            if (state.isBuffering && state.playbackError == null) {
                Box(
                    modifier = Modifier.align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = if (state.selectedEngine == PlayerEngine.JWPLAYER) Color(0xFFFF0040) else MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(54.dp)
                    )
                }
            }

            // Fullscreen Controls Overlay (JWX Pro UI)
            androidx.compose.animation.AnimatedVisibility(
                visible = state.areControlsVisible || state.isControlsLocked,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.75f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                ) {
                    // Lock-Only Mode
                    if (state.isControlsLocked) {
                        IconButton(
                            onClick = { viewModel.toggleControlsLock() },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(24.dp)
                                .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                                .size(48.dp)
                                .testTag("btn_unlock_screen")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Unlock Controls",
                                tint = Color(0xFFFF0040)
                            )
                        }
                    } else {
                        // Full Interactive Controls
                        // TOP BAR
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopStart)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(
                                    onClick = { viewModel.setFullscreen(false) },
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        .testTag("btn_exit_fullscreen")
                                ) {
                                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                                }

                                Column {
                                    Text(
                                        text = state.currentMedia?.title ?: "FluxPlay Stream",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    // Engine Badge
                                    Text(
                                        text = if (state.selectedEngine == PlayerEngine.JWPLAYER) "JWX PRO CORE • 0-LATENCY" else state.selectedEngine.displayName,
                                        color = if (state.selectedEngine == PlayerEngine.JWPLAYER) Color(0xFFFF0040) else MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.toggleControlsLock() },
                                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(Icons.Filled.LockOpen, contentDescription = "Lock", tint = Color.White)
                                }

                                IconButton(
                                    onClick = { showEngineSheet = true },
                                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(Icons.Filled.Tune, contentDescription = "Engine", tint = if (state.selectedEngine == PlayerEngine.JWPLAYER) Color(0xFFFF0040) else MaterialTheme.colorScheme.primary)
                                }

                                IconButton(
                                    onClick = { showQualitySheet = true },
                                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(Icons.Filled.HighQuality, contentDescription = "Quality", tint = Color.White)
                                }

                                IconButton(
                                    onClick = { showAudioTrackSheet = true },
                                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(Icons.Filled.Audiotrack, contentDescription = "Audio", tint = Color.White)
                                }

                                IconButton(
                                    onClick = { showSubtitleSheet = true },
                                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(Icons.Filled.Subtitles, contentDescription = "Subtitles", tint = Color.White)
                                }

                                IconButton(
                                    onClick = { viewModel.cycleResizeMode() },
                                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(Icons.Filled.AspectRatio, contentDescription = "Aspect", tint = Color.White)
                                }
                            }
                        }

                        // CENTER PLAY / PAUSE & 10s JUMP BUTTONS
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(36.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rewind 10s
                            IconButton(
                                onClick = { viewModel.seekRelative(-10000L) },
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .testTag("btn_rewind_10_fullscreen")
                            ) {
                                Icon(Icons.Filled.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                            }

                            // Big Play / Pause
                            IconButton(
                                onClick = { viewModel.togglePlayPause() },
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(
                                        if (state.selectedEngine == PlayerEngine.JWPLAYER) Color(0xFFFF0040) else MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    )
                                    .testTag("btn_play_pause_fullscreen")
                            ) {
                                Icon(
                                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(42.dp)
                                )
                            }

                            // Forward 10s
                            IconButton(
                                onClick = { viewModel.seekRelative(10000L) },
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .testTag("btn_forward_10_fullscreen")
                            ) {
                                Icon(Icons.Filled.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }

                        // BOTTOM BAR WITH SCRUBBER & TIMELINE
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            // JWX Glowing Scrubber
                            val currentPos = state.currentPositionMs.toFloat()
                            val duration = (if (state.durationMs > 0) state.durationMs else 1L).toFloat()
                            val sliderValue = (currentPos / duration).coerceIn(0f, 1f)

                            Slider(
                                value = sliderValue,
                                onValueChange = { frac ->
                                    val newPos = (frac * duration).toLong()
                                    viewModel.seekTo(newPos)
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = if (state.selectedEngine == PlayerEngine.JWPLAYER) Color(0xFFFF0040) else MaterialTheme.colorScheme.primary,
                                    activeTrackColor = if (state.selectedEngine == PlayerEngine.JWPLAYER) Color(0xFFFF0040) else MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("fullscreen_scrubber")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${formatDuration(state.currentPositionMs)} / ${formatDuration(state.durationMs)}",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Speed Chip
                                    Surface(
                                        color = Color.White.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.clickable { showSpeedSheet = true }
                                    ) {
                                        Text(
                                            text = "${state.playbackSpeed}x",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    // Aspect Mode Chip
                                    Surface(
                                        color = Color.White.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.clickable { viewModel.cycleResizeMode() }
                                    ) {
                                        Text(
                                            text = state.resizeMode.displayName,
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    // Fullscreen Exit
                                    IconButton(
                                        onClick = { viewModel.setFullscreen(false) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Filled.FullscreenExit, contentDescription = "Exit", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // ==========================================
        // PORTRAIT JWX PRO PLAYER
        // ==========================================
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            // Viewport & Touch Controller
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                // ExoPlayer View
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            resizeMode = state.resizeMode.exoMode
                            player = viewModel.getExoPlayer()
                        }
                    },
                    update = { view ->
                        view.player = viewModel.getExoPlayer()
                        view.resizeMode = state.resizeMode.exoMode
                    },
                    modifier = Modifier.fillMaxSize().testTag("portrait_player_view")
                )

                // Tap & Gesture Detector Overlay (100% Clickable & Responsive)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(state.isControlsLocked) {
                            detectTapGestures(
                                onTap = {
                                    viewModel.toggleControlsVisibility()
                                },
                                onDoubleTap = { offset ->
                                    if (!state.isControlsLocked) {
                                        val isRight = offset.x > size.width / 2
                                        viewModel.seekRelative(if (isRight) 10000L else -10000L)
                                    }
                                }
                            )
                        }
                )

                // Top Engine Badge
                if (state.selectedEngine == PlayerEngine.JWPLAYER) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(bottomEnd = 8.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF0040))
                            )
                            Text(
                                text = "JWX REAL ENGINE",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                } else {
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(bottomEnd = 8.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = state.selectedEngine.badge.uppercase(),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Buffering Spinner
                if (state.isBuffering && state.playbackError == null) {
                    Box(
                        modifier = Modifier.align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = if (state.selectedEngine == PlayerEngine.JWPLAYER) Color(0xFFFF0040) else MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                // Gesture HUD pill
                state.gestureIndicatorText?.let { text ->
                    Surface(
                        color = Color.Black.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text(
                            text = text,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                // On-Screen Controls Overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.areControlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        // Quick 10s Rewind / Play / Forward Center Buttons
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.seekRelative(-10000L) },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .testTag("btn_rewind_10_portrait")
                            ) {
                                Icon(Icons.Filled.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(26.dp))
                            }

                            IconButton(
                                onClick = { viewModel.togglePlayPause() },
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(
                                        if (state.selectedEngine == PlayerEngine.JWPLAYER) Color(0xFFFF0040) else MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    )
                                    .testTag("btn_play_pause_portrait")
                            ) {
                                Icon(
                                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.seekRelative(10000L) },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .testTag("btn_forward_10_portrait")
                            ) {
                                Icon(Icons.Filled.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(26.dp))
                            }
                        }

                        // Bottom Scrubber Bar & Fullscreen Button
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            val currentPos = state.currentPositionMs.toFloat()
                            val duration = (if (state.durationMs > 0) state.durationMs else 1L).toFloat()
                            val sliderValue = (currentPos / duration).coerceIn(0f, 1f)

                            Slider(
                                value = sliderValue,
                                onValueChange = { frac ->
                                    val newPos = (frac * duration).toLong()
                                    viewModel.seekTo(newPos)
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = if (state.selectedEngine == PlayerEngine.JWPLAYER) Color(0xFFFF0040) else MaterialTheme.colorScheme.primary,
                                    activeTrackColor = if (state.selectedEngine == PlayerEngine.JWPLAYER) Color(0xFFFF0040) else MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("portrait_scrubber")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${formatDuration(state.currentPositionMs)} / ${formatDuration(state.durationMs)}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                IconButton(
                                    onClick = { viewModel.toggleFullscreen() },
                                    modifier = Modifier.size(28.dp).testTag("btn_portrait_to_fullscreen")
                                ) {
                                    Icon(Icons.Filled.Fullscreen, contentDescription = "Fullscreen", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }

                // Error Retry Banner
                if (state.playbackError != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.9f))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = Color(0xFFFF0040), modifier = Modifier.size(36.dp))
                            Text("Media Error", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = state.playbackError ?: "",
                                color = FluxTextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Button(
                                onClick = { viewModel.retryCurrentMedia() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("btn_retry_playback")
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry Playback", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Quick Input Action Buttons (Open Local Video File, Direct URL)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Open Offline Local File
                Button(
                    onClick = {
                        filePickerLauncher.launch(arrayOf("video/*", "application/x-matroska", "application/octet-stream", "*/*"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).testTag("btn_open_file")
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Video", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
                }

                // Open Stream URL
                Button(
                    onClick = { showCustomUrlDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).testTag("btn_open_url")
                ) {
                    Icon(Icons.Filled.Link, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stream URL", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
                }
            }

            // Media Info & Advanced Controls Toolbar
            val currentMedia = state.currentMedia
            if (currentMedia != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentMedia.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = if (currentMedia.type.contains("Offline", ignoreCase = true) || currentMedia.source.contains("Storage", ignoreCase = true)) FluxEmerald.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = currentMedia.type.uppercase(),
                                        color = if (currentMedia.type.contains("Offline", ignoreCase = true) || currentMedia.source.contains("Storage", ignoreCase = true)) FluxEmerald else MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                if (state.settings.backgroundPlayEnabled) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "BG PLAY",
                                            color = MaterialTheme.colorScheme.secondary,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if (currentMedia.source.isNotBlank()) {
                                    Text(
                                        text = currentMedia.source,
                                        color = FluxTextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        // Bookmark Action
                        IconButton(
                            onClick = { viewModel.toggleBookmark() },
                            modifier = Modifier.testTag("btn_bookmark")
                        ) {
                            Icon(
                                imageVector = if (currentMedia.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = if (currentMedia.isBookmarked) MaterialTheme.colorScheme.secondary else FluxTextSecondary
                            )
                        }
                    }

                    if (state.streamTelemetry.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.streamTelemetry,
                            style = MaterialTheme.typography.bodySmall,
                            color = FluxTextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Player Advanced Controls Bar (Engine Switch, Aspect Ratio, Audio, Subtitles, Speed, PiP, Fullscreen)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Engine Switcher
                        FilledTonalButton(
                            onClick = { showEngineSheet = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (state.selectedEngine == PlayerEngine.JWPLAYER) Color(0xFFFF0040).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                contentColor = if (state.selectedEngine == PlayerEngine.JWPLAYER) Color(0xFFFF0040) else MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_engine_switch")
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(state.selectedEngine.badge, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }

                        // Aspect Ratio
                        FilledTonalButton(
                            onClick = { viewModel.cycleResizeMode() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_aspect_ratio")
                        ) {
                            Icon(Icons.Filled.AspectRatio, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(state.resizeMode.displayName, style = MaterialTheme.typography.labelSmall)
                        }

                        // Audio Track
                        FilledTonalButton(
                            onClick = { showAudioTrackSheet = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_audio_tracks")
                        ) {
                            Icon(Icons.Filled.Audiotrack, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Audio", style = MaterialTheme.typography.labelSmall)
                        }

                        // Subtitle
                        FilledTonalButton(
                            onClick = { showSubtitleSheet = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_subtitles")
                        ) {
                            Icon(Icons.Filled.Subtitles, contentDescription = null, modifier = Modifier.size(14.dp), tint = FluxEmerald)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Sub", style = MaterialTheme.typography.labelSmall)
                        }

                        // Speed
                        FilledTonalButton(
                            onClick = { showSpeedSheet = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_speed")
                        ) {
                            Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(14.dp), tint = FluxAccent)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("${state.playbackSpeed}x", style = MaterialTheme.typography.labelSmall)
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Picture-in-Picture
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            IconButton(
                                onClick = {
                                    val params = PictureInPictureParams.Builder()
                                        .setAspectRatio(Rational(16, 9))
                                        .build()
                                    activity?.enterPictureInPictureMode(params)
                                },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .size(34.dp)
                                    .testTag("btn_pip")
                            ) {
                                Icon(Icons.Filled.PictureInPictureAlt, contentDescription = "PiP", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                            }
                        }

                        // Fullscreen
                        IconButton(
                            onClick = { viewModel.toggleFullscreen() },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .size(34.dp)
                                .testTag("btn_fullscreen")
                        ) {
                            Icon(Icons.Filled.Fullscreen, contentDescription = "Fullscreen", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayCircleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Fluxplay Media Hub",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Play offline video files (MKV/MP4/WebM), stream direct network links, or explore curated live streams in Discover.",
                            style = MaterialTheme.typography.bodySmall,
                            color = FluxTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    // ==========================================
    // MODAL BOTTOM SHEETS & DIALOGS
    // ==========================================

    // Engine Switcher Sheet
    if (showEngineSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEngineSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Player Engine & Decoder",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                PlayerEngine.values().forEach { engine ->
                    val isSelected = state.selectedEngine == engine
                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setEngine(engine)
                                showEngineSheet = false
                            }
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
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = engine.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        color = if (engine == PlayerEngine.JWPLAYER) Color(0xFFFF0040).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = engine.badge,
                                            color = if (engine == PlayerEngine.JWPLAYER) Color(0xFFFF0040) else MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = engine.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FluxTextSecondary
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Audio Track Sheet
    if (showAudioTrackSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAudioTrackSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Select Audio Track", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                if (state.audioTracks.isEmpty()) {
                    Text("No secondary audio tracks found", style = MaterialTheme.typography.bodyMedium, color = FluxTextSecondary, modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        items(state.audioTracks) { track ->
                            Surface(
                                color = if (track.name == state.selectedAudioTrackName) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectAudioTrack(track)
                                        showAudioTrackSheet = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(track.name, style = MaterialTheme.typography.bodyMedium, color = if (track.name == state.selectedAudioTrackName) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                    if (track.name == state.selectedAudioTrackName) {
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Subtitle Sheet
    if (showSubtitleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSubtitleSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Select Subtitles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    items(state.subtitleTracks) { track ->
                        val isSelected = track.name == state.selectedSubtitleTrackName || (track.id == "off" && state.selectedSubtitleTrackName == "Off")
                        Surface(
                            color = if (isSelected) FluxEmerald.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectSubtitleTrack(track)
                                    showSubtitleSheet = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(track.name, style = MaterialTheme.typography.bodyMedium, color = if (isSelected) FluxEmerald else MaterialTheme.colorScheme.onSurface)
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = FluxEmerald)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Speed Sheet
    if (showSpeedSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSpeedSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Playback Speed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
                    val isSelected = state.playbackSpeed == speed
                    Surface(
                        color = if (isSelected) FluxAccent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setPlaybackSpeed(speed)
                                showSpeedSheet = false
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${speed}x Speed", style = MaterialTheme.typography.bodyMedium, color = if (isSelected) FluxAccent else MaterialTheme.colorScheme.onSurface, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            if (isSelected) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = FluxAccent)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Quality Sheet
    if (showQualitySheet) {
        ModalBottomSheet(
            onDismissRequest = { showQualitySheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Video Quality (Bitrate)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    items(state.videoQualities) { q ->
                        val isSelected = (q.height <= 0 && state.selectedQualityLabel == "Auto") || q.label == state.selectedQualityLabel
                        Surface(
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectVideoQuality(q)
                                    showQualitySheet = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(q.label, style = MaterialTheme.typography.bodyMedium, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Custom URL Stream Dialog
    if (showCustomUrlDialog) {
        var inputUrl by remember { mutableStateOf("") }
        var inputTitle by remember { mutableStateOf("") }
        val clipboardManager = LocalClipboardManager.current

        AlertDialog(
            onDismissRequest = { showCustomUrlDialog = false },
            title = { Text("Stream Network URL", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        label = { Text("Stream / Video URL") },
                        placeholder = { Text("https://example.com/stream.m3u8") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_stream_url")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                val clip = clipboardManager.getText()
                                if (clip != null && clip.text.isNotBlank()) {
                                    inputUrl = clip.text.trim()
                                }
                            },
                            modifier = Modifier.testTag("btn_paste_url")
                        ) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste from Clipboard")
                        }
                    }

                    OutlinedTextField(
                        value = inputTitle,
                        onValueChange = { inputTitle = it },
                        label = { Text("Title (Optional)") },
                        placeholder = { Text("My Custom Stream") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_stream_title")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputUrl.isNotBlank()) {
                            viewModel.playCustomStream(inputUrl, inputTitle)
                            showCustomUrlDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("btn_confirm_play_url")
                ) {
                    Text("Play Now", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomUrlDialog = false }) {
                    Text("Cancel", color = FluxTextSecondary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
