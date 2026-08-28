package com.example.fluxplay.ui.player

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.fluxplay.data.cache.FluxplayMediaCache
import com.example.fluxplay.data.model.AppSettings
import com.example.fluxplay.data.model.MediaItemEntity
import com.example.fluxplay.data.model.MediaTrackInfo
import com.example.fluxplay.data.model.PlayerEngine
import com.example.fluxplay.data.model.ResizeMode
import com.example.fluxplay.data.repository.MediaRepository
import com.example.fluxplay.data.repository.SettingsRepository
import com.example.fluxplay.player.mpv.MpvEngineBridge
import `is`.xyz.mpv.MPV
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val currentMedia: MediaItemEntity? = null,
    val selectedEngine: PlayerEngine = PlayerEngine.EXOPLAYER,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val audioTracks: List<MediaTrackInfo> = emptyList(),
    val subtitleTracks: List<MediaTrackInfo> = emptyList(),
    val selectedAudioTrackId: String? = null,
    val selectedSubtitleTrackId: String? = null,
    val resizeMode: ResizeMode = ResizeMode.FIT,
    val isFullscreen: Boolean = false,
    val showControls: Boolean = true,
    val volumeLevel: Float = 1.0f,
    val brightnessLevel: Float = -1.0f, // -1 means system default
    val errorMessage: String? = null,
    val isBookmarked: Boolean = false,
    val settings: AppSettings = AppSettings()
)

@OptIn(UnstableApi::class)
class PlayerViewModel(
    application: Application,
    private val mediaRepository: MediaRepository,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    var exoPlayer: ExoPlayer? = null
        private set

    val mpvBridge = MpvEngineBridge(application)

    private var positionTrackerJob: Job? = null
    private var controlsHideJob: Job? = null

    init {
        initExoPlayer()
        observeSettings()
        startPositionTracker()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    private fun initExoPlayer() {
        val context = getApplication<Application>()
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setUserAgent("Fluxplay-Android/2.0")

        val cache = FluxplayMediaCache.getCache(context)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(cacheDataSourceFactory)

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true)
        }

        exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()
            .apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val isBuffering = playbackState == Player.STATE_BUFFERING
                        val isEnded = playbackState == Player.STATE_ENDED
                        _uiState.update {
                            it.copy(
                                isLoading = isBuffering,
                                isPlaying = if (isEnded) false else this@apply.isPlaying,
                                durationMs = if (duration > 0 && duration != C.TIME_UNSET) duration else it.durationMs
                            )
                        }
                        if (isEnded) {
                            onMediaEnded()
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (_uiState.value.selectedEngine == PlayerEngine.EXOPLAYER) {
                            _uiState.update { it.copy(isPlaying = isPlaying) }
                        }
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        extractExoTracks(tracks)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("PlayerViewModel", "ExoPlayer error", error)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "ExoPlayer Error: ${error.localizedMessage ?: "Stream unavailable"}"
                            )
                        }
                    }
                })
            }
    }

    fun registerMpv(mpv: MPV) {
        mpvBridge.initializeMpv(mpv, _uiState.value.settings.hardwareAcceleration)
    }

    private fun extractExoTracks(tracks: Tracks) {
        val audioList = mutableListOf<MediaTrackInfo>()
        val subtitleList = mutableListOf<MediaTrackInfo>()
        var selectedAudioId: String? = null
        var selectedSubId: String? = null

        for (group in tracks.groups) {
            when (group.type) {
                C.TRACK_TYPE_AUDIO -> {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val id = format.id ?: "audio_$i"
                        val isSelected = group.isTrackSelected(i)
                        val label = format.label ?: format.language ?: "Audio Track ${audioList.size + 1}"
                        val track = MediaTrackInfo(
                            id = id,
                            label = label,
                            language = format.language,
                            isSelected = isSelected,
                            mimeType = format.sampleMimeType
                        )
                        audioList.add(track)
                        if (isSelected) selectedAudioId = id
                    }
                }
                C.TRACK_TYPE_TEXT -> {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val id = format.id ?: "sub_$i"
                        val isSelected = group.isTrackSelected(i)
                        val label = format.label ?: format.language ?: "Subtitle ${subtitleList.size + 1}"
                        val track = MediaTrackInfo(
                            id = id,
                            label = label,
                            language = format.language,
                            isSelected = isSelected,
                            mimeType = format.sampleMimeType
                        )
                        subtitleList.add(track)
                        if (isSelected) selectedSubId = id
                    }
                }
            }
        }

        _uiState.update {
            it.copy(
                audioTracks = audioList,
                subtitleTracks = subtitleList,
                selectedAudioTrackId = selectedAudioId,
                selectedSubtitleTrackId = selectedSubId
            )
        }
    }

    fun playMedia(media: MediaItemEntity, preferredEngine: PlayerEngine? = null, startPositionMs: Long = 0L) {
        val engine = preferredEngine ?: _uiState.value.selectedEngine
        
        // 1. Strict mutual exclusion: stop all players first to prevent double-play!
        stopAllPlayers()

        _uiState.update {
            it.copy(
                currentMedia = media,
                selectedEngine = engine,
                isLoading = true,
                errorMessage = null,
                currentPositionMs = startPositionMs,
                isBookmarked = media.isBookmark
            )
        }

        viewModelScope.launch {
            mediaRepository.saveMedia(media.copy(lastPlayedTimestamp = System.currentTimeMillis()))
        }

        if (engine == PlayerEngine.EXOPLAYER) {
            playWithExoPlayer(media.uri, startPositionMs)
        } else {
            playWithMpv(media.uri, startPositionMs)
        }

        scheduleControlsHide()
    }

    private fun playWithExoPlayer(uriStr: String, startPositionMs: Long) {
        val player = exoPlayer ?: return
        try {
            val mediaItem = MediaItem.fromUri(Uri.parse(uriStr))
            player.setMediaItem(mediaItem)
            player.prepare()
            if (startPositionMs > 0) {
                player.seekTo(startPositionMs)
            }
            player.play()
            _uiState.update { it.copy(isPlaying = true, isLoading = false) }
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Failed to play in ExoPlayer", e)
            _uiState.update { it.copy(isLoading = false, errorMessage = "Playback error: ${e.message}") }
        }
    }

    private fun playWithMpv(uriStr: String, startPositionMs: Long) {
        try {
            mpvBridge.loadMedia(uriStr, startPositionMs / 1000.0)
            mpvBridge.play()
            _uiState.update { it.copy(isPlaying = true, isLoading = false) }
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Failed to play in MPV", e)
            _uiState.update { it.copy(isLoading = false, errorMessage = "MPV Playback error: ${e.message}") }
        }
    }

    fun switchEngine(newEngine: PlayerEngine) {
        if (_uiState.value.selectedEngine == newEngine) return
        val currentMedia = _uiState.value.currentMedia ?: return
        val currentPos = _uiState.value.currentPositionMs
        val wasPlaying = _uiState.value.isPlaying

        // Stop old engine
        stopAllPlayers()

        _uiState.update {
            it.copy(
                selectedEngine = newEngine,
                isLoading = true
            )
        }

        if (newEngine == PlayerEngine.EXOPLAYER) {
            playWithExoPlayer(currentMedia.uri, currentPos)
            if (!wasPlaying) exoPlayer?.pause()
        } else {
            playWithMpv(currentMedia.uri, currentPos)
            if (!wasPlaying) mpvBridge.pause()
        }
    }

    fun togglePlayPause() {
        val isCurrentlyPlaying = _uiState.value.isPlaying
        if (isCurrentlyPlaying) {
            pause()
        } else {
            play()
        }
        scheduleControlsHide()
    }

    fun play() {
        if (_uiState.value.selectedEngine == PlayerEngine.EXOPLAYER) {
            exoPlayer?.play()
        } else {
            mpvBridge.play()
        }
        _uiState.update { it.copy(isPlaying = true) }
    }

    fun pause() {
        exoPlayer?.pause()
        mpvBridge.pause()
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun stopAllPlayers() {
        try {
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            mpvBridge.stop()
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Error stopping players", e)
        }
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun seekTo(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        if (_uiState.value.selectedEngine == PlayerEngine.EXOPLAYER) {
            exoPlayer?.seekTo(safePos)
        } else {
            mpvBridge.seekTo(safePos / 1000.0)
        }
        _uiState.update { it.copy(currentPositionMs = safePos) }
        scheduleControlsHide()
    }

    fun seekRelative(seconds: Int) {
        val current = _uiState.value.currentPositionMs
        val target = (current + seconds * 1000L).coerceAtLeast(0L)
        seekTo(target)
    }

    fun setPlaybackSpeed(speed: Float) {
        _uiState.update { it.copy(playbackSpeed = speed) }
        exoPlayer?.setPlaybackSpeed(speed)
        mpvBridge.setSpeed(speed.toDouble())
    }

    fun setResizeMode(mode: ResizeMode) {
        _uiState.update { it.copy(resizeMode = mode) }
    }

    fun toggleFullscreen() {
        _uiState.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    fun setFullscreen(fullscreen: Boolean) {
        _uiState.update { it.copy(isFullscreen = fullscreen) }
    }

    fun toggleControls() {
        val willShow = !_uiState.value.showControls
        _uiState.update { it.copy(showControls = willShow) }
        if (willShow) {
            scheduleControlsHide()
        }
    }

    fun showControlsTemporarily() {
        _uiState.update { it.copy(showControls = true) }
        scheduleControlsHide()
    }

    private fun scheduleControlsHide() {
        controlsHideJob?.cancel()
        controlsHideJob = viewModelScope.launch {
            delay(4000)
            if (_uiState.value.isPlaying) {
                _uiState.update { it.copy(showControls = false) }
            }
        }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _uiState.update { it.copy(volumeLevel = clamped) }
        exoPlayer?.volume = clamped
    }

    fun setBrightness(brightness: Float) {
        _uiState.update { it.copy(brightnessLevel = brightness.coerceIn(0.01f, 1f)) }
    }

    fun toggleBookmark() {
        val current = _uiState.value.currentMedia ?: return
        val newBookmark = !_uiState.value.isBookmarked
        _uiState.update { it.copy(isBookmarked = newBookmark) }
        viewModelScope.launch {
            mediaRepository.toggleBookmark(current)
        }
    }

    fun selectAudioTrack(track: MediaTrackInfo) {
        if (_uiState.value.selectedEngine == PlayerEngine.EXOPLAYER) {
            val player = exoPlayer ?: return
            val tracks = player.currentTracks
            for (group in tracks.groups) {
                if (group.type == C.TRACK_TYPE_AUDIO) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        if (format.id == track.id) {
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                .build()
                            break
                        }
                    }
                }
            }
        }
        _uiState.update { it.copy(selectedAudioTrackId = track.id) }
    }

    fun selectSubtitleTrack(track: MediaTrackInfo?) {
        if (_uiState.value.selectedEngine == PlayerEngine.EXOPLAYER) {
            val player = exoPlayer ?: return
            if (track == null) {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            } else {
                val tracks = player.currentTracks
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_TEXT) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            if (format.id == track.id) {
                                player.trackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                    .build()
                                break
                            }
                        }
                    }
                }
            }
        }
        _uiState.update { it.copy(selectedSubtitleTrackId = track?.id) }
    }

    private fun startPositionTracker() {
        positionTrackerJob?.cancel()
        positionTrackerJob = viewModelScope.launch {
            while (isActive) {
                delay(500)
                val state = _uiState.value
                if (state.isPlaying) {
                    if (state.selectedEngine == PlayerEngine.EXOPLAYER) {
                        exoPlayer?.let { player ->
                            val pos = player.currentPosition.coerceAtLeast(0L)
                            val dur = player.duration.let { if (it > 0 && it != C.TIME_UNSET) it else state.durationMs }
                            val buf = player.bufferedPosition.coerceAtLeast(0L)
                            _uiState.update {
                                it.copy(
                                    currentPositionMs = pos,
                                    durationMs = dur,
                                    bufferedPositionMs = buf
                                )
                            }
                            state.currentMedia?.let { media ->
                                mediaRepository.updatePosition(media.id, pos)
                            }
                        }
                    } else {
                        val posSec = mpvBridge.getTimePos()
                        val durSec = mpvBridge.getDuration()
                        val posMs = (posSec * 1000).toLong().coerceAtLeast(0L)
                        val durMs = (durSec * 1000).toLong().coerceAtLeast(0L)
                        _uiState.update {
                            it.copy(
                                currentPositionMs = posMs,
                                durationMs = if (durMs > 0) durMs else it.durationMs
                            )
                        }
                        state.currentMedia?.let { media ->
                            mediaRepository.updatePosition(media.id, posMs)
                        }
                    }
                }
            }
        }
    }

    private fun onMediaEnded() {
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopAllPlayers()
        exoPlayer?.release()
        exoPlayer = null
        positionTrackerJob?.cancel()
        controlsHideJob?.cancel()
    }
}
