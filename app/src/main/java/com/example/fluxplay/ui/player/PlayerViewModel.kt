package com.example.fluxplay.ui.player

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.session.MediaSession
import com.example.fluxplay.data.cache.FluxplayMediaCache
import com.example.fluxplay.data.model.DiscoverItem
import com.example.fluxplay.data.model.MediaItemEntity
import com.example.fluxplay.data.repository.MediaRepository
import com.example.fluxplay.data.repository.MetadataRepository
import com.example.fluxplay.data.repository.SettingsRepository
import com.example.fluxplay.service.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URLDecoder

data class PlayerUiState(
    val currentUrl: String = "",
    val currentMedia: MediaItemEntity? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedPositionMs: Long = 0,
    val playbackSpeed: Float = 1.0f,
    val isMuted: Boolean = false,
    val isFullscreen: Boolean = false,
    val aspectRatioMode: String = "contain", // "contain", "cover", "fill"
    val showControls: Boolean = true,
    val showSkipLeft: Boolean = false,
    val showSkipRight: Boolean = false,
    val statusText: String = "Ready",
    val statusType: String = "", // "", "live", "warning", "error"
    val cachedBytes: Long = 0L,
    val totalCacheSizeBytes: Long = 0L,
    val isBookmarked: Boolean = false,
    val playerType: String = "builtin", // "builtin", "jwplayer"
    val error: String? = null
)

@OptIn(UnstableApi::class)
class PlayerViewModel(
    application: Application,
    private val mediaRepository: MediaRepository,
    private val settingsRepository: SettingsRepository,
    private val metadataRepository: MetadataRepository = MetadataRepository(settingsRepository)
) : AndroidViewModel(application) {

    // High bandwidth estimation for instant max-speed chunk fetching
    private val bandwidthMeter = DefaultBandwidthMeter.Builder(application)
        .setInitialBitrateEstimate(50_000_000L)
        .build()

    // Ultra-responsive, highly reliable LoadControl: instant 400ms startup with deep 15s-50s anti-stutter buffer
    private val loadControl = DefaultLoadControl.Builder()
        .setAllocator(DefaultAllocator(true, 64 * 1024))
        .setBufferDurationsMs(
            /* minBufferMs = */ 15000,
            /* maxBufferMs = */ 50000,
            /* bufferForPlaybackMs = */ 400,
            /* bufferForPlaybackAfterRebufferMs = */ 800
        )
        .setBackBuffer(
            /* backBufferDurationMs = */ 10000,
            /* retainBackBufferFromKeyframe = */ true
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    // High throughput Media DataSource with Keep-Alive
    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile; Fluxplay Native Engine)")
        .setConnectTimeoutMs(8000)
        .setReadTimeoutMs(15000)
        .setAllowCrossProtocolRedirects(true)
        .setTransferListener(bandwidthMeter)
        .setDefaultRequestProperties(
            mapOf(
                "Connection" to "keep-alive"
            )
        )

    private val upstreamDataSourceFactory = DefaultDataSource.Factory(application, httpDataSourceFactory)

    // Progressive Cache DataSource: Downloads & plays simultaneously, caching stream data to disk
    private val cacheDataSourceFactory = FluxplayMediaCache.createCacheDataSourceFactory(
        application,
        upstreamDataSourceFactory
    )

    private val mediaSourceFactory = DefaultMediaSourceFactory(application)
        .setDataSourceFactory(cacheDataSourceFactory)

    private val renderersFactory = DefaultRenderersFactory(application)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        .setEnableDecoderFallback(true)
        .setAllowedVideoJoiningTimeMs(5000)

    private val trackSelector = DefaultTrackSelector(application).apply {
        setParameters(
            buildUponParameters()
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setAllowVideoNonSeamlessAdaptiveness(true)
                .setExceedRendererCapabilitiesIfNecessary(true)
                .setTunnelingEnabled(false)
        )
    }

    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .setUsage(C.USAGE_MEDIA)
        .build()

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(application, renderersFactory)
        .setMediaSourceFactory(mediaSourceFactory)
        .setLoadControl(loadControl)
        .setBandwidthMeter(bandwidthMeter)
        .setTrackSelector(trackSelector)
        .setSeekBackIncrementMs(10000)
        .setSeekForwardIncrementMs(10000)
        .setAudioAttributes(audioAttributes, true)
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .setHandleAudioBecomingNoisy(true)
        .setSeekParameters(SeekParameters.EXACT)
        .build()

    private var mediaSession: MediaSession? = null

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var progressTrackingJob: Job? = null
    private var controlsHideJob: Job? = null

    init {
        try {
            mediaSession = MediaSession.Builder(application, exoPlayer)
                .setId("fluxplay_media_session")
                .build()
            PlaybackService.setMediaSession(mediaSession)
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Error creating MediaSession", e)
        }

        PlaybackService.setPlaybackControlListener(object : PlaybackService.PlaybackControlListener {
            override fun onTogglePlayPause() {
                togglePlayPause()
            }

            override fun onSkip(seconds: Int) {
                skip(seconds)
            }

            override fun onStopPlayback() {
                stop()
            }
        })

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(
                    isPlaying = isPlaying,
                    statusText = if (isPlaying) "Playing" else "Paused",
                    statusType = if (isPlaying) "live" else ""
                )
                notifyPlaybackService(isPlaying)
                if (isPlaying) {
                    startProgressTracker()
                    scheduleHideControls()
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _uiState.value = _uiState.value.copy(
                            isBuffering = true,
                            statusText = "Buffering...",
                            statusType = "warning"
                        )
                    }
                    Player.STATE_READY -> {
                        val duration = exoPlayer.duration.coerceAtLeast(0)
                        _uiState.value = _uiState.value.copy(
                            isBuffering = false,
                            durationMs = duration,
                            statusText = if (exoPlayer.isPlaying) "Playing" else "Ready",
                            statusType = if (exoPlayer.isPlaying) "live" else ""
                        )
                        notifyPlaybackService(exoPlayer.isPlaying)
                    }
                    Player.STATE_ENDED -> {
                        val watchedUrl = _uiState.value.currentUrl
                        _uiState.value = _uiState.value.copy(
                            isPlaying = false,
                            isBuffering = false,
                            statusText = "Completed",
                            statusType = "",
                            cachedBytes = 0L
                        )
                        if (watchedUrl.isNotBlank()) {
                            FluxplayMediaCache.removeResourceForUrl(getApplication(), watchedUrl)
                        }
                        notifyPlaybackService(false)
                        stopProgressTracker()
                    }
                    Player.STATE_IDLE -> {
                        _uiState.value = _uiState.value.copy(
                            isBuffering = false
                        )
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlayerViewModel", "Player error: ${error.errorCodeName}", error)
                val friendlyMessage = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Network timeout. Please verify stream URL."
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> "Unsupported stream format or codec."
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Server returned an error (404/403/500)."
                    else -> error.localizedMessage ?: "Playback error occurred"
                }
                _uiState.value = _uiState.value.copy(
                    isBuffering = false,
                    isPlaying = false,
                    error = friendlyMessage,
                    statusText = "Playback Error",
                    statusType = "error"
                )
                notifyPlaybackService(false)
                stopProgressTracker()
            }
        })

        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    playerType = settings.playerType
                )
            }
        }
    }

    fun playLocalFile(uri: Uri) {
        val context: android.content.Context = getApplication()
        var title = "Local Media"
        var sizeBytes = 0L
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = it.getString(nameIndex)
                        if (!name.isNullOrBlank()) title = name
                    }
                    val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        sizeBytes = it.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Error querying uri metadata", e)
            title = uri.lastPathSegment ?: "Local Media"
        }

        val isAudio = title.endsWith(".mp3", ignoreCase = true) ||
                title.endsWith(".m4a", ignoreCase = true) ||
                title.endsWith(".aac", ignoreCase = true) ||
                title.endsWith(".flac", ignoreCase = true) ||
                title.endsWith(".wav", ignoreCase = true) ||
                title.endsWith(".ogg", ignoreCase = true)

        val formattedSize = if (sizeBytes > 0) formatBytes(sizeBytes) else ""
        val media = MediaItemEntity(
            url = uri.toString(),
            title = title,
            source = "Local Storage",
            type = if (isAudio) "Audio" else "Local Video",
            duration = formattedSize
        )
        playUrl(uri.toString(), media)
    }

    fun setPlayerType(type: String) {
        settingsRepository.updatePlayerType(type)
    }

    fun togglePlayerType() {
        val next = when (_uiState.value.playerType) {
            "builtin" -> "jwplayer"
            "jwplayer" -> "vimeo"
            else -> "builtin"
        }
        setPlayerType(next)
    }

    fun playUrl(url: String, customMedia: MediaItemEntity? = null) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return

        val previousUrl = _uiState.value.currentUrl
        if (previousUrl.isNotBlank() && previousUrl != trimmed) {
            FluxplayMediaCache.removeResourceForUrl(getApplication(), previousUrl)
        }

        val baseMedia = customMedia ?: createDefaultMediaFromUrl(trimmed)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                currentUrl = trimmed,
                currentMedia = baseMedia,
                isBuffering = true,
                isPlaying = true,
                statusText = "Connecting...",
                statusType = "live",
                error = null,
                showControls = true
            )

            // If it's a Vimeo link and we need resolution
            var activeMedia = baseMedia
            var streamToPlay = trimmed

            if (trimmed.contains("vimeo.com", ignoreCase = true)) {
                _uiState.value = _uiState.value.copy(statusText = "Resolving Vimeo Stream...")
                val resolved = metadataRepository.resolveVimeoUrl(trimmed)
                if (resolved != null) {
                    activeMedia = resolved
                    streamToPlay = resolved.url
                }
            } else {
                val existing = mediaRepository.getMediaDirect(trimmed)
                if (existing != null) {
                    activeMedia = existing
                }
            }

            _uiState.value = _uiState.value.copy(
                currentUrl = streamToPlay,
                currentMedia = activeMedia,
                statusText = "Buffering..."
            )

            // Save to history
            mediaRepository.recordWatch(activeMedia)

            val isLive = streamToPlay.contains(".m3u8", ignoreCase = true) || streamToPlay.contains("live", ignoreCase = true)
            val isCurrentBookmarked = activeMedia.isBookmarked
            _uiState.value = _uiState.value.copy(isBookmarked = isCurrentBookmarked)
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(streamToPlay))
                .apply {
                    if (isLive) {
                        setLiveConfiguration(
                            MediaItem.LiveConfiguration.Builder()
                                .setTargetOffsetMs(1000)
                                .setMinOffsetMs(200)
                                .setMaxOffsetMs(3000)
                                .build()
                        )
                    }
                }
                .build()

            exoPlayer.playWhenReady = true
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()

            if (activeMedia.progressSeconds > 0) {
                val resumeMs = activeMedia.progressSeconds * 1000
                exoPlayer.seekTo(resumeMs)
            }
            exoPlayer.play()

            notifyPlaybackService(true)
            scheduleHideControls()
        }
    }

    fun playFromDiscover(item: DiscoverItem) {
        val streamUrl = if (item.trailerUrl.isNotBlank()) item.trailerUrl else item.sourceUrl
        if (streamUrl.isBlank()) return
        val media = MediaItemEntity(
            url = streamUrl,
            title = item.title,
            poster = item.poster,
            year = item.year,
            type = item.type,
            rating = item.rating,
            source = item.source,
            provider = item.provider,
            providerId = item.id,
            synopsis = item.synopsis,
            duration = item.duration,
            genres = item.genres,
            cast = item.characters,
            studios = item.studios,
            sourceUrl = item.sourceUrl,
            trailerUrl = item.trailerUrl
        )
        playUrl(streamUrl, media)
    }

    fun retry() {
        val currentUrl = _uiState.value.currentUrl
        if (currentUrl.isNotBlank()) {
            playUrl(currentUrl, _uiState.value.currentMedia)
        }
    }

    fun stop() {
        val currentUrl = _uiState.value.currentUrl
        exoPlayer.stop()
        _uiState.value = _uiState.value.copy(
            isPlaying = false,
            isBuffering = false,
            error = null,
            statusText = "Stopped",
            statusType = "",
            cachedBytes = 0L
        )
        if (currentUrl.isNotBlank()) {
            FluxplayMediaCache.removeResourceForUrl(getApplication(), currentUrl)
        }
        PlaybackService.stop(getApplication())
        stopProgressTracker()
    }

    fun togglePlayPause() {
        if (_uiState.value.currentUrl.isBlank()) return
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
        showControlsTemporarily()
    }

    fun seekTo(positionMs: Long) {
        val maxDuration = _uiState.value.durationMs.coerceAtLeast(0)
        val clamped = if (maxDuration > 0) positionMs.coerceIn(0, maxDuration) else positionMs.coerceAtLeast(0)
        exoPlayer.seekTo(clamped)
        _uiState.value = _uiState.value.copy(
            currentPositionMs = clamped
        )
        showControlsTemporarily()
    }

    fun skip(seconds: Int) {
        val current = _uiState.value.currentPositionMs
        val duration = _uiState.value.durationMs
        val target = if (duration > 0) {
            (current + (seconds * 1000)).coerceIn(0, duration)
        } else {
            (current + (seconds * 1000)).coerceAtLeast(0)
        }
        seekTo(target)
        _uiState.value = _uiState.value.copy(
            showSkipLeft = seconds < 0,
            showSkipRight = seconds > 0
        )
        showControlsTemporarily()
        viewModelScope.launch {
            delay(700)
            _uiState.value = _uiState.value.copy(
                showSkipLeft = false,
                showSkipRight = false
            )
        }
    }

    fun cycleAspectRatio() {
        val next = when (_uiState.value.aspectRatioMode) {
            "contain" -> "cover"
            "cover" -> "fill"
            else -> "contain"
        }
        _uiState.value = _uiState.value.copy(aspectRatioMode = next)
        showControlsTemporarily()
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.playbackParameters = PlaybackParameters(speed)
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
        showControlsTemporarily()
    }

    fun toggleMute() {
        val newMuted = !_uiState.value.isMuted
        exoPlayer.volume = if (newMuted) 0f else 1f
        _uiState.value = _uiState.value.copy(isMuted = newMuted)
        showControlsTemporarily()
    }

    fun toggleFullscreen() {
        _uiState.value = _uiState.value.copy(isFullscreen = !_uiState.value.isFullscreen)
    }

    fun toggleControls() {
        val next = !_uiState.value.showControls
        _uiState.value = _uiState.value.copy(showControls = next)
        if (next && _uiState.value.isPlaying) {
            scheduleHideControls()
        }
    }

    fun showControlsTemporarily() {
        _uiState.value = _uiState.value.copy(showControls = true)
        scheduleHideControls()
    }

    private fun scheduleHideControls() {
        controlsHideJob?.cancel()
        controlsHideJob = viewModelScope.launch {
            delay(3500)
            if (_uiState.value.isPlaying) {
                _uiState.value = _uiState.value.copy(showControls = false)
            }
        }
    }

    fun toggleBookmark() {
        val current = _uiState.value.currentMedia ?: return
        viewModelScope.launch {
            mediaRepository.toggleBookmark(current)
            val updated = mediaRepository.getMediaDirect(current.url)
            _uiState.value = _uiState.value.copy(
                currentMedia = updated,
                isBookmarked = updated?.isBookmarked ?: !_uiState.value.isBookmarked
            )
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressTrackingJob = viewModelScope.launch {
            while (isActive) {
                val currentPos = exoPlayer.currentPosition
                val duration = exoPlayer.duration.coerceAtLeast(0)
                val buffered = exoPlayer.bufferedPosition
                val url = _uiState.value.currentUrl
                val cached = if (url.isNotBlank()) FluxplayMediaCache.getCachedBytesForUrl(getApplication(), url) else 0L
                val totalCache = FluxplayMediaCache.getTotalCacheSize(getApplication())

                _uiState.value = _uiState.value.copy(
                    currentPositionMs = currentPos,
                    durationMs = duration,
                    bufferedPositionMs = buffered,
                    cachedBytes = cached,
                    totalCacheSizeBytes = totalCache
                )

                if (url.isNotBlank() && duration > 0) {
                    mediaRepository.updatePlaybackProgress(
                        url = url,
                        progressSec = currentPos / 1000,
                        durationSec = duration / 1000
                    )
                }
                delay(500)
            }
        }
    }

    fun clearMediaCache() {
        FluxplayMediaCache.clearAllCache(getApplication())
        _uiState.value = _uiState.value.copy(
            cachedBytes = 0L,
            totalCacheSizeBytes = 0L
        )
    }

    private fun stopProgressTracker() {
        progressTrackingJob?.cancel()
        progressTrackingJob = null
    }

    private fun createDefaultMediaFromUrl(url: String): MediaItemEntity {
        var filename = "Direct Stream"
        try {
            val uri = Uri.parse(url)
            val path = uri.path
            if (!path.isNullOrBlank()) {
                val rawName = path.substringAfterLast("/")
                filename = URLDecoder.decode(rawName, "UTF-8")
                if (filename.length > 40) {
                    filename = filename.take(37) + "..."
                }
            }
        } catch (e: Exception) {
            filename = "Direct Stream"
        }
        return MediaItemEntity(
            url = url,
            title = filename.ifBlank { "Direct Stream" },
            source = "Direct"
        )
    }

    private fun notifyPlaybackService(isPlaying: Boolean) {
        val state = _uiState.value
        if (state.currentUrl.isBlank()) return
        if (!settingsRepository.settings.value.backgroundPlayback) {
            PlaybackService.stop(getApplication())
            return
        }

        val title = state.currentMedia?.title?.ifBlank { "Fluxplay Stream" } ?: "Fluxplay Stream"
        val subtitle = when {
            state.currentUrl.contains(".m3u8", ignoreCase = true) -> "HLS Live Stream"
            state.currentUrl.contains(".mpd", ignoreCase = true) -> "DASH Stream"
            else -> "Media3 Core"
        }

        PlaybackService.start(
            context = getApplication(),
            title = title,
            subtitle = subtitle,
            isPlaying = isPlaying,
            positionMs = state.currentPositionMs,
            durationMs = state.durationMs
        )
    }

    override fun onCleared() {
        super.onCleared()
        val currentUrl = _uiState.value.currentUrl
        if (currentUrl.isNotBlank()) {
            FluxplayMediaCache.removeResourceForUrl(getApplication(), currentUrl)
        }
        stopProgressTracker()
        controlsHideJob?.cancel()
        PlaybackService.stop(getApplication())
        PlaybackService.setMediaSession(null)
        PlaybackService.setPlaybackControlListener(null)
        try {
            mediaSession?.release()
            mediaSession = null
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Error releasing MediaSession", e)
        }
        exoPlayer.release()
    }
}
