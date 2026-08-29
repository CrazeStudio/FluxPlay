package com.example.fluxplay.ui.player

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.*
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.fluxplay.data.model.MediaItemEntity
import com.example.fluxplay.data.model.PlayerEngine
import com.example.fluxplay.data.model.PlayerSettings
import com.example.fluxplay.data.repository.MediaRepository
import com.example.fluxplay.data.repository.SettingsRepository
import com.example.fluxplay.service.PlaybackNotificationHelper
import com.example.fluxplay.util.MediaTitleFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

enum class VideoResizeMode(val displayName: String, val exoMode: Int) {
    FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    ZOOM("Zoom / Fill", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    STRETCH("Stretch", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    FIXED_16_9("16:9", AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH)
}

data class TrackItem(
    val id: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val name: String,
    val language: String,
    val isSelected: Boolean
)

data class VideoQualityItem(
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val label: String,
    val isSelected: Boolean
)

data class PlayerUiState(
    val currentMedia: MediaItemEntity? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val playbackError: String? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val isMuted: Boolean = false,
    val volumeLevel: Float = 1.0f, // 0.0 to 1.5 (boost)
    val brightnessLevel: Float = 0.5f,
    val gestureIndicatorText: String? = null,
    val isFullscreen: Boolean = false,
    val isControlsLocked: Boolean = false,
    val areControlsVisible: Boolean = true,
    val resizeMode: VideoResizeMode = VideoResizeMode.FIT,
    val audioTracks: List<TrackItem> = emptyList(),
    val subtitleTracks: List<TrackItem> = emptyList(),
    val videoQualities: List<VideoQualityItem> = emptyList(),
    val selectedAudioTrackName: String = "Default",
    val selectedSubtitleTrackName: String = "Off",
    val selectedQualityLabel: String = "Auto",
    val selectedEngine: PlayerEngine = PlayerEngine.EXOPLAYER,
    val streamTelemetry: String = "",
    val settings: PlayerSettings = PlayerSettings()
)

class PlayerViewModel(
    application: Application,
    private val mediaRepository: MediaRepository,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private var exoPlayer: ExoPlayer? = null
    private var mpvPlayer: `is`.xyz.mpv.MPV? = null
    private var progressTrackingJob: Job? = null
    private var gestureIndicatorDismissJob: Job? = null
    private var controlsHideJob: Job? = null

    init {
        initExoPlayer(_uiState.value.selectedEngine)

        // Sync settings
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                val prevEngine = _uiState.value.selectedEngine
                _uiState.value = _uiState.value.copy(
                    settings = settings,
                    selectedEngine = settings.selectedEngine
                )
                if (!settings.notificationsEnabled) {
                    PlaybackNotificationHelper.dismissNotification(getApplication())
                }
            }
        }

        // Resume last watched media from DB if available
        viewModelScope.launch {
            mediaRepository.getWatchHistory().collect { historyList ->
                if (_uiState.value.currentMedia == null && historyList.isNotEmpty()) {
                    val last = historyList.first()
                    _uiState.value = _uiState.value.copy(currentMedia = last)
                }
            }
        }

        // Register notification actions
        PlaybackNotificationHelper.registerActionListener(getApplication()) { action ->
            when (action) {
                PlaybackNotificationHelper.ACTION_PLAY -> play()
                PlaybackNotificationHelper.ACTION_PAUSE -> pause()
                PlaybackNotificationHelper.ACTION_REWIND -> seekRelative(-10000)
                PlaybackNotificationHelper.ACTION_FORWARD -> seekRelative(10000)
                PlaybackNotificationHelper.ACTION_STOP -> {
                    pause()
                    PlaybackNotificationHelper.dismissNotification(getApplication())
                }
            }
        }
    }

    private fun initExoPlayer(engine: PlayerEngine = _uiState.value.selectedEngine) {
        val app = getApplication<Application>()

        val renderersFactory = DefaultRenderersFactory(app).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true)
        }

        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("FluxPlay/2.0 (Android; Universal Media Player)")

        val dataSourceFactory = DefaultDataSource.Factory(app, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(app)
            .setDataSourceFactory(dataSourceFactory)

        val loadControl = when (engine) {
            PlayerEngine.JWPLAYER -> {
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        2000,
                        10000,
                        500,
                        1000
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()
            }
            PlayerEngine.MKV_HARDWARE -> {
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        50000,
                        120000,
                        2500,
                        5000
                    )
                    .setTargetBufferBytes(64 * 1024 * 1024)
                    .setPrioritizeTimeOverSizeThresholds(false)
                    .build()
            }
            else -> {
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        25000,
                        50000,
                        1500,
                        3000
                    )
                    .build()
            }
        }

        val player = ExoPlayer.Builder(app, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build().apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
                setSeekParameters(
                    when (engine) {
                        PlayerEngine.JWPLAYER -> SeekParameters.CLOSEST_SYNC
                        PlayerEngine.MKV_HARDWARE -> SeekParameters.EXACT
                        else -> SeekParameters.DEFAULT
                    }
                )
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                        updatePlaybackNotification()
                        if (isPlaying) {
                            scheduleControlsAutoHide()
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val isBuffering = playbackState == Player.STATE_BUFFERING
                        val duration = if (duration > 0) duration else 0L
                        _uiState.value = _uiState.value.copy(
                            isBuffering = isBuffering,
                            durationMs = duration
                        )
                        if (playbackState == Player.STATE_READY) {
                            _uiState.value = _uiState.value.copy(playbackError = null)
                            updateTelemetry()
                        }
                        updatePlaybackNotification()
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        extractTracks(tracks)
                        updateTelemetry()
                    }

                    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                        val metaTitle = mediaMetadata.title?.toString()
                            ?: mediaMetadata.displayTitle?.toString()
                        if (!metaTitle.isNullOrBlank()) {
                            val clean = MediaTitleFormatter.extractCleanTitle(metaTitle, _uiState.value.currentMedia?.url, getApplication())
                            updateCurrentMediaTitle(clean)
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val errorMsg = error.localizedMessage ?: "Playback error (${error.errorCodeName})"
                        _uiState.value = _uiState.value.copy(
                            playbackError = errorMsg,
                            isBuffering = false,
                            isPlaying = false,
                            areControlsVisible = true
                        )
                    }
                })
            }
        exoPlayer?.release()
        exoPlayer = player
        startProgressTracking()
    }

    fun getExoPlayer(): ExoPlayer? = exoPlayer

    fun attachMpv(mpv: `is`.xyz.mpv.MPV) {
        this.mpvPlayer = mpv
        val url = _uiState.value.currentMedia?.url ?: return
        if (uiState.value.selectedEngine == PlayerEngine.LIBMPV) {
            // Stop ExoPlayer to prevent two players playing simultaneously
            exoPlayer?.pause()
            exoPlayer?.clearMediaItems()
            
            mpv.command("loadfile", url)
            val resumePos = _uiState.value.currentPositionMs
            if (resumePos > 0) {
                mpv.setPropertyDouble("time-pos", resumePos / 1000.0)
            }
            if (_uiState.value.isPlaying) {
                mpv.setPropertyBoolean("pause", false)
            }
            startProgressTracking()
        }
    }

    private fun updatePlaybackNotification() {
        if (!_uiState.value.settings.notificationsEnabled) {
            PlaybackNotificationHelper.dismissNotification(getApplication())
            return
        }
        val media = _uiState.value.currentMedia ?: return
        PlaybackNotificationHelper.showPlaybackNotification(
            context = getApplication(),
            media = media,
            isPlaying = _uiState.value.isPlaying,
            coroutineScope = viewModelScope
        )
    }

    private fun updateTelemetry() {
        val player = exoPlayer ?: return
        val format = player.videoFormat
        val width = format?.width ?: 0
        val height = format?.height ?: 0
        val fps = format?.frameRate ?: 0f
        val codec = format?.sampleMimeType?.substringAfter("/") ?: "Hardware Codec"
        val bitrateKbps = if ((format?.bitrate ?: 0) > 0) "${format!!.bitrate / 1000} kbps" else "Adaptive Bitrate"

        val engineName = _uiState.value.selectedEngine.displayName
        val resStr = if (height > 0) "${width}x${height}p @ ${fps.toInt()}fps" else "Auto Resolution"

        _uiState.value = _uiState.value.copy(
            streamTelemetry = "$engineName • $resStr • $codec • $bitrateKbps"
        )
    }

    private fun extractTracks(tracks: Tracks) {
        val audioList = mutableListOf<TrackItem>()
        val subtitleList = mutableListOf<TrackItem>()
        val qualityList = mutableListOf<VideoQualityItem>()

        subtitleList.add(
            TrackItem(
                id = "off",
                groupIndex = -1,
                trackIndex = -1,
                name = "Off",
                language = "",
                isSelected = true
            )
        )

        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            val trackType = group.type

            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val isSelected = group.isTrackSelected(trackIndex)
                val lang = format.language ?: "und"
                val label = format.label ?: format.id ?: "Track ${trackIndex + 1}"

                when (trackType) {
                    C.TRACK_TYPE_AUDIO -> {
                        val audioName = if (format.channelCount > 2) "$label (${format.channelCount}ch / $lang)" else "$label ($lang)"
                        audioList.add(
                            TrackItem(
                                id = "audio_${groupIndex}_$trackIndex",
                                groupIndex = groupIndex,
                                trackIndex = trackIndex,
                                name = audioName,
                                language = lang,
                                isSelected = isSelected
                            )
                        )
                    }
                    C.TRACK_TYPE_TEXT -> {
                        val subName = if (label.isNotBlank()) "$label ($lang)" else "Subtitle ($lang)"
                        val item = TrackItem(
                            id = "sub_${groupIndex}_$trackIndex",
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            name = subName,
                            language = lang,
                            isSelected = isSelected
                        )
                        subtitleList.add(item)
                    }
                    C.TRACK_TYPE_VIDEO -> {
                        if (format.height > 0) {
                            val qLabel = "${format.height}p" + if (format.bitrate > 0) " (${format.bitrate / 1000} kbps)" else ""
                            qualityList.add(
                                VideoQualityItem(
                                    width = format.width,
                                    height = format.height,
                                    bitrate = format.bitrate,
                                    label = qLabel,
                                    isSelected = isSelected
                                )
                            )
                        }
                    }
                }
            }
        }

        val activeAudio = audioList.find { it.isSelected }?.name ?: if (audioList.isNotEmpty()) audioList.first().name else "Default"
        val activeSub = subtitleList.find { it.isSelected && it.id != "off" }?.name ?: "Off"
        val activeQuality = qualityList.find { it.isSelected && it.height > 0 }?.label ?: "Auto"

        _uiState.value = _uiState.value.copy(
            audioTracks = audioList,
            subtitleTracks = subtitleList,
            videoQualities = qualityList,
            selectedAudioTrackName = activeAudio,
            selectedSubtitleTrackName = activeSub,
            selectedQualityLabel = activeQuality
        )
    }

    private fun startProgressTracking() {
        progressTrackingJob?.cancel()
        progressTrackingJob = viewModelScope.launch {
            while (isActive) {
                if (_uiState.value.selectedEngine == PlayerEngine.LIBMPV) {
                    mpvPlayer?.let { player ->
                        val pos = ((player.getPropertyDouble("time-pos") ?: 0.0) * 1000).toLong()
                        val dur = ((player.getPropertyDouble("duration") ?: 0.0) * 1000).toLong()
                        val isPaused = player.getPropertyBoolean("pause") ?: true
                        
                        _uiState.value = _uiState.value.copy(
                            currentPositionMs = pos,
                            durationMs = dur,
                            isPlaying = !isPaused,
                            bufferedPositionMs = pos // MPV buffering is more complex to extract simply
                        )
                        if (!isPaused && dur > 0) {
                            _uiState.value.currentMedia?.let { media ->
                                mediaRepository.updateProgress(
                                    url = media.url,
                                    progress = pos / 1000,
                                    duration = dur / 1000
                                )
                            }
                        }
                    }
                } else {
                    exoPlayer?.let { player ->
                        val pos = player.currentPosition
                        val dur = if (player.duration > 0) player.duration else 0L
                        val buffered = player.bufferedPosition
                        _uiState.value = _uiState.value.copy(
                            currentPositionMs = pos,
                            durationMs = dur,
                            bufferedPositionMs = buffered
                        )
                        if (player.isPlaying && dur > 0) {
                            _uiState.value.currentMedia?.let { media ->
                                mediaRepository.updateProgress(
                                    url = media.url,
                                    progress = pos / 1000,
                                    duration = dur / 1000
                                )
                            }
                        }
                    }
                }
                delay(250)
            }
        }
    }

    fun updateCurrentMediaTitle(newTitle: String) {
        val current = _uiState.value.currentMedia ?: return
        val clean = MediaTitleFormatter.extractCleanTitle(newTitle, current.url, getApplication())
        if (clean.isBlank() || current.title == clean) return
        val updated = current.copy(title = clean)
        _uiState.value = _uiState.value.copy(currentMedia = updated)
        viewModelScope.launch {
            mediaRepository.saveOrUpdateMedia(updated)
        }
        updatePlaybackNotification()
    }

    fun loadMedia(media: MediaItemEntity) {
        val cleanTitle = MediaTitleFormatter.extractCleanTitle(media.title, media.url, getApplication())
        val cleanMedia = media.copy(title = cleanTitle)

        _uiState.value = _uiState.value.copy(
            currentMedia = cleanMedia,
            playbackError = null,
            isBuffering = true,
            areControlsVisible = true
        )
        viewModelScope.launch {
            mediaRepository.saveOrUpdateMedia(cleanMedia)
            if (_uiState.value.selectedEngine == PlayerEngine.LIBMPV) {
                // Ensure ExoPlayer is paused and cleared
                exoPlayer?.pause()
                exoPlayer?.clearMediaItems()
                mpvPlayer?.let { player ->
                    player.command("loadfile", cleanMedia.url)
                    if (cleanMedia.progressSeconds > 0) {
                        player.setPropertyDouble("time-pos", cleanMedia.progressSeconds.toDouble())
                    }
                    player.setPropertyBoolean("pause", false)
                    _uiState.value = _uiState.value.copy(isPlaying = true)
                    startProgressTracking()
                    scheduleControlsAutoHide()
                }
            } else {
                // Ensure MPV is paused
                mpvPlayer?.setPropertyBoolean("pause", true)
                exoPlayer?.let { player ->
                    try {
                        val uri = Uri.parse(cleanMedia.url)
                        val mediaItem = MediaItem.Builder()
                            .setUri(uri)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(cleanMedia.title)
                                    .setDisplayTitle(cleanMedia.title)
                                    .setArtworkUri(if (cleanMedia.poster.isNotBlank()) Uri.parse(cleanMedia.poster) else null)
                                    .build()
                            )
                            .build()
    
                        player.setMediaItem(mediaItem)
                        player.prepare()
                        if (cleanMedia.progressSeconds > 0) {
                            player.seekTo(cleanMedia.progressSeconds * 1000)
                        }
                        player.play()
                        updatePlaybackNotification()
                        scheduleControlsAutoHide()
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            playbackError = "Unable to load media: ${e.localizedMessage}",
                            isBuffering = false
                        )
                    }
                }
            }

            // Asynchronously check for embedded container metadata tags (MKV/MP4/ID3)
            launch(Dispatchers.IO) {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    if (cleanMedia.url.startsWith("content://")) {
                        retriever.setDataSource(getApplication(), Uri.parse(cleanMedia.url))
                    } else if (cleanMedia.url.startsWith("file://") || cleanMedia.url.startsWith("/")) {
                        retriever.setDataSource(cleanMedia.url.removePrefix("file://"))
                    }
                    val embeddedTitle = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                    retriever.release()
                    if (!embeddedTitle.isNullOrBlank()) {
                        val formatted = MediaTitleFormatter.extractCleanTitle(embeddedTitle, cleanMedia.url, getApplication())
                        withContext(Dispatchers.Main) {
                            updateCurrentMediaTitle(formatted)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun openLocalFile(uri: Uri, displayName: String) {
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}

        val cleanTitle = MediaTitleFormatter.extractCleanTitle(displayName, uri.toString(), getApplication())
        val item = MediaItemEntity(
            url = uri.toString(),
            title = cleanTitle,
            poster = "",
            year = "Offline",
            type = "Offline Video",
            source = "Device Storage",
            provider = "local",
            providerId = "local_${System.currentTimeMillis()}",
            synopsis = "Offline video file: $cleanTitle"
        )
        loadMedia(item)
    }

    fun playCustomStream(url: String, title: String = "") {
        if (url.isBlank()) return
        val trimmed = url.trim()
        val cleanTitle = MediaTitleFormatter.extractCleanTitle(title, trimmed, getApplication())
        val customItem = MediaItemEntity(
            url = trimmed,
            title = cleanTitle,
            poster = "",
            year = "Stream",
            type = if (trimmed.contains(".m3u8")) "HLS Stream" else if (trimmed.contains(".mpd")) "DASH Stream" else "Direct Video",
            rating = "HD",
            source = "Network Stream",
            provider = "custom",
            providerId = "custom_${System.currentTimeMillis()}",
            synopsis = "Network Stream: $cleanTitle"
        )
        loadMedia(customItem)
    }

    fun retryCurrentMedia() {
        _uiState.value.currentMedia?.let { loadMedia(it) }
    }

    fun play() {
        if (_uiState.value.selectedEngine == PlayerEngine.LIBMPV) {
            mpvPlayer?.setPropertyBoolean("pause", false)
            _uiState.value = _uiState.value.copy(isPlaying = true)
            updatePlaybackNotification()
            scheduleControlsAutoHide()
            return
        }
        exoPlayer?.play()
        scheduleControlsAutoHide()
    }

    fun pause() {
        mpvPlayer?.setPropertyBoolean("pause", true)
        exoPlayer?.pause()
        _uiState.value = _uiState.value.copy(isPlaying = false)
        updatePlaybackNotification()
        setControlsVisibility(true)
    }

    fun togglePlayPause() {
        if (_uiState.value.selectedEngine == PlayerEngine.LIBMPV) {
            mpvPlayer?.let { player ->
                val isPaused = player.getPropertyBoolean("pause") ?: false
                player.setPropertyBoolean("pause", !isPaused)
                _uiState.value = _uiState.value.copy(isPlaying = isPaused)
                updatePlaybackNotification()
                if (isPaused) {
                    scheduleControlsAutoHide()
                } else {
                    setControlsVisibility(true)
                }
            }
            return
        }
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                setControlsVisibility(true)
            } else {
                player.play()
                scheduleControlsAutoHide()
            }
        }
    }

    fun toggleControlsVisibility() {
        if (_uiState.value.isControlsLocked) return
        val newVisible = !_uiState.value.areControlsVisible
        setControlsVisibility(newVisible)
    }

    fun setControlsVisibility(visible: Boolean) {
        _uiState.value = _uiState.value.copy(areControlsVisible = visible)
        controlsHideJob?.cancel()
        if (visible && _uiState.value.isPlaying) {
            scheduleControlsAutoHide()
        }
    }

    fun scheduleControlsAutoHide() {
        controlsHideJob?.cancel()
        controlsHideJob = viewModelScope.launch {
            delay(4000)
            if (_uiState.value.isPlaying && !_uiState.value.isControlsLocked) {
                _uiState.value = _uiState.value.copy(areControlsVisible = false)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        if (_uiState.value.selectedEngine == PlayerEngine.LIBMPV) {
            mpvPlayer?.setPropertyDouble("time-pos", positionMs / 1000.0)
        } else {
            exoPlayer?.seekTo(positionMs)
        }
        scheduleControlsAutoHide()
    }

    fun seekRelative(deltaMs: Long) {
        if (_uiState.value.selectedEngine == PlayerEngine.LIBMPV) {
            mpvPlayer?.let { player ->
                val current = player.getPropertyDouble("time-pos") ?: 0.0
                player.setPropertyDouble("time-pos", current + (deltaMs / 1000.0))
                showGestureIndicator(if (deltaMs > 0) "+${deltaMs/1000}s \u23E9" else "\u23EA ${deltaMs/1000}s")
            }
            return
        }
        exoPlayer?.let { player ->
            val target = (player.currentPosition + deltaMs).coerceIn(0L, if (player.duration > 0) player.duration else Long.MAX_VALUE)
            player.seekTo(target)
            val sec = (deltaMs / 1000).toInt()
            val sign = if (sec > 0) "+$sec" else "$sec"
            showGestureIndicator("${sign}s")
            scheduleControlsAutoHide()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
        showGestureIndicator("${speed}x Speed")
        scheduleControlsAutoHide()
    }

    fun toggleMute() {
        val newMuted = !_uiState.value.isMuted
        exoPlayer?.volume = if (newMuted) 0f else _uiState.value.volumeLevel.coerceAtMost(1.0f)
        _uiState.value = _uiState.value.copy(isMuted = newMuted)
        showGestureIndicator(if (newMuted) "Muted" else "Unmuted")
    }

    fun setVolumeDelta(delta: Float) {
        val current = _uiState.value.volumeLevel
        val updated = (current + delta).coerceIn(0.0f, 1.5f)
        exoPlayer?.volume = updated.coerceAtMost(1.0f)
        _uiState.value = _uiState.value.copy(volumeLevel = updated, isMuted = updated <= 0.01f)
        val pct = (updated * 100).toInt()
        showGestureIndicator("Volume $pct%")
    }

    fun setBrightnessDelta(delta: Float) {
        val current = _uiState.value.brightnessLevel
        val updated = (current + delta).coerceIn(0.05f, 1.0f)
        _uiState.value = _uiState.value.copy(brightnessLevel = updated)
        val pct = (updated * 100).toInt()
        showGestureIndicator("Brightness $pct%")
    }

    private fun showGestureIndicator(text: String) {
        _uiState.value = _uiState.value.copy(gestureIndicatorText = text)
        gestureIndicatorDismissJob?.cancel()
        gestureIndicatorDismissJob = viewModelScope.launch {
            delay(1200)
            _uiState.value = _uiState.value.copy(gestureIndicatorText = null)
        }
    }

    fun setFullscreen(fullscreen: Boolean) {
        _uiState.value = _uiState.value.copy(isFullscreen = fullscreen, areControlsVisible = true)
        scheduleControlsAutoHide()
    }

    fun toggleFullscreen() {
        setFullscreen(!_uiState.value.isFullscreen)
    }

    fun toggleControlsLock() {
        val locked = !_uiState.value.isControlsLocked
        _uiState.value = _uiState.value.copy(isControlsLocked = locked, areControlsVisible = !locked)
        showGestureIndicator(if (locked) "Controls Locked" else "Controls Unlocked")
    }

    fun cycleResizeMode() {
        val modes = VideoResizeMode.values()
        val nextIndex = (modes.indexOf(_uiState.value.resizeMode) + 1) % modes.size
        val newMode = modes[nextIndex]
        _uiState.value = _uiState.value.copy(resizeMode = newMode)
        showGestureIndicator(newMode.displayName)
        scheduleControlsAutoHide()

        if (_uiState.value.selectedEngine == PlayerEngine.LIBMPV) {
            mpvPlayer?.let { player ->
                when (newMode) {
                    VideoResizeMode.FIT -> {
                        player.setPropertyDouble("panscan", 0.0)
                        player.setPropertyBoolean("keepaspect", true)
                    }
                    VideoResizeMode.ZOOM -> {
                        player.setPropertyDouble("panscan", 1.0)
                        player.setPropertyBoolean("keepaspect", true)
                    }
                    VideoResizeMode.STRETCH -> {
                        player.setPropertyBoolean("keepaspect", false)
                    }
                    VideoResizeMode.FIXED_16_9 -> {
                        player.setPropertyDouble("panscan", 0.0)
                        player.setPropertyBoolean("keepaspect", true)
                        player.setPropertyDouble("video-aspect-override", 16.0 / 9.0)
                    }
                }
                if (newMode != VideoResizeMode.FIXED_16_9) {
                    player.setPropertyDouble("video-aspect-override", -1.0) // Reset override
                }
            }
        }
    }

    fun setResizeMode(mode: VideoResizeMode) {
        _uiState.value = _uiState.value.copy(resizeMode = mode)
        scheduleControlsAutoHide()
        
        if (_uiState.value.selectedEngine == PlayerEngine.LIBMPV) {
            mpvPlayer?.let { player ->
                when (mode) {
                    VideoResizeMode.FIT -> {
                        player.setPropertyDouble("panscan", 0.0)
                        player.setPropertyBoolean("keepaspect", true)
                    }
                    VideoResizeMode.ZOOM -> {
                        player.setPropertyDouble("panscan", 1.0)
                        player.setPropertyBoolean("keepaspect", true)
                    }
                    VideoResizeMode.STRETCH -> {
                        player.setPropertyBoolean("keepaspect", false)
                    }
                    VideoResizeMode.FIXED_16_9 -> {
                        player.setPropertyDouble("panscan", 0.0)
                        player.setPropertyBoolean("keepaspect", true)
                        player.setPropertyDouble("video-aspect-override", 16.0 / 9.0)
                    }
                }
                if (mode != VideoResizeMode.FIXED_16_9) {
                    player.setPropertyDouble("video-aspect-override", -1.0)
                }
            }
        }
    }

    fun setEngine(engine: PlayerEngine) {
        val oldEngine = _uiState.value.selectedEngine
        if (oldEngine == engine) return

        val wasPlaying = _uiState.value.isPlaying
        val currentPos = _uiState.value.currentPositionMs
        val currentMedia = _uiState.value.currentMedia

        if (oldEngine == PlayerEngine.LIBMPV) {
            mpvPlayer?.setPropertyBoolean("pause", true)
        } else {
            exoPlayer?.pause()
        }

        settingsRepository.updateEngine(engine)
        _uiState.value = _uiState.value.copy(selectedEngine = engine)
        showGestureIndicator("Engine: ${engine.displayName}")

        if (engine != PlayerEngine.LIBMPV) {
            initExoPlayer(engine)
        }

        updateTelemetry()

        if (currentMedia != null) {
            if (engine == PlayerEngine.LIBMPV) {
                exoPlayer?.clearMediaItems()
                mpvPlayer?.let { player ->
                    player.command("loadfile", currentMedia.url)
                    if (currentPos > 0) {
                        player.setPropertyDouble("time-pos", currentPos / 1000.0)
                    }
                    player.setPropertyBoolean("pause", !wasPlaying)
                    _uiState.value = _uiState.value.copy(isPlaying = wasPlaying)
                }
            } else {
                exoPlayer?.let { player ->
                    val uri = Uri.parse(currentMedia.url)
                    val mediaItem = MediaItem.Builder()
                        .setUri(uri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(currentMedia.title)
                                .setDisplayTitle(currentMedia.title)
                                .setArtworkUri(if (currentMedia.poster.isNotBlank()) Uri.parse(currentMedia.poster) else null)
                                .build()
                        )
                        .build()
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    if (currentPos > 0) {
                        player.seekTo(currentPos)
                    }
                    if (wasPlaying) {
                        player.play()
                    } else {
                        player.pause()
                    }
                }
            }
        }
    }

    fun selectAudioTrack(track: TrackItem) {
        val player = exoPlayer ?: return
        val currentTracks = player.currentTracks
        if (track.groupIndex in 0 until currentTracks.groups.size) {
            val group = currentTracks.groups[track.groupIndex]
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
                .build()
            _uiState.value = _uiState.value.copy(selectedAudioTrackName = track.name)
            showGestureIndicator("Audio: ${track.name}")
        }
    }

    fun selectSubtitleTrack(track: TrackItem) {
        val player = exoPlayer ?: return
        if (track.id == "off") {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            _uiState.value = _uiState.value.copy(selectedSubtitleTrackName = "Off")
            showGestureIndicator("Subtitles: Off")
        } else {
            val currentTracks = player.currentTracks
            if (track.groupIndex in 0 until currentTracks.groups.size) {
                val group = currentTracks.groups[track.groupIndex]
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
                    .build()
                _uiState.value = _uiState.value.copy(selectedSubtitleTrackName = track.name)
                showGestureIndicator("Subtitles: ${track.name}")
            }
        }
    }

    fun selectVideoQuality(quality: VideoQualityItem) {
        val player = exoPlayer ?: return
        if (quality.height <= 0) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearVideoSizeConstraints()
                .build()
            _uiState.value = _uiState.value.copy(selectedQualityLabel = "Auto")
            showGestureIndicator("Quality: Auto")
        } else {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setMaxVideoSize(quality.width.coerceAtLeast(1), quality.height)
                .build()
            _uiState.value = _uiState.value.copy(selectedQualityLabel = quality.label)
            showGestureIndicator("Quality: ${quality.label}")
        }
    }

    fun toggleBookmark() {
        val media = _uiState.value.currentMedia ?: return
        viewModelScope.launch {
            mediaRepository.toggleBookmark(media)
            val updated = mediaRepository.getMediaDirect(media.url)
            if (updated != null) {
                _uiState.value = _uiState.value.copy(currentMedia = updated)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressTrackingJob?.cancel()
        gestureIndicatorDismissJob?.cancel()
        controlsHideJob?.cancel()
        PlaybackNotificationHelper.unregisterReceiver(getApplication())
        PlaybackNotificationHelper.dismissNotification(getApplication())
        exoPlayer?.release()
        mpvPlayer?.destroy()
        mpvPlayer = null
        exoPlayer = null
    }
}
