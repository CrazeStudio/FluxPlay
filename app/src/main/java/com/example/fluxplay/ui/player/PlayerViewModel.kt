package com.example.fluxplay.ui.player

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.fluxplay.data.model.DiscoverItem
import com.example.fluxplay.data.model.MediaItemEntity
import com.example.fluxplay.data.repository.MediaRepository
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
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
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
    val showControls: Boolean = true,
    val showSkipLeft: Boolean = false,
    val showSkipRight: Boolean = false,
    val statusText: String = "Ready",
    val statusType: String = "", // "", "live", "warning", "error"
    val isDownloadBuffering: Boolean = false,
    val downloadProgressText: String = "",
    val error: String? = null
)

class PlayerViewModel(
    application: Application,
    private val mediaRepository: MediaRepository
) : AndroidViewModel(application) {

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(application).build()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var progressTrackingJob: Job? = null
    private var controlsHideJob: Job? = null
    private var downloadJob: Job? = null
    private val okHttpClient = OkHttpClient()

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(
                    isPlaying = isPlaying,
                    statusText = if (isPlaying) "Playing" else "Paused",
                    statusType = if (isPlaying) "live" else ""
                )
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
                    }
                    Player.STATE_ENDED -> {
                        _uiState.value = _uiState.value.copy(
                            isPlaying = false,
                            isBuffering = false,
                            statusText = "Ended",
                            statusType = ""
                        )
                    }
                    Player.STATE_IDLE -> {
                        _uiState.value = _uiState.value.copy(isBuffering = false)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _uiState.value = _uiState.value.copy(
                    isBuffering = false,
                    isPlaying = false,
                    statusText = "Video Error",
                    statusType = "error",
                    error = error.localizedMessage
                )
            }
        })
    }

    fun playUrl(url: String, customMedia: MediaItemEntity? = null) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return

        val media = customMedia ?: createDefaultMediaFromUrl(trimmed)

        viewModelScope.launch {
            // Check if saved in db
            val existing = mediaRepository.getMediaDirect(trimmed)
            val activeMedia = existing ?: media

            _uiState.value = _uiState.value.copy(
                currentUrl = trimmed,
                currentMedia = activeMedia,
                isBuffering = true,
                statusText = "Loading...",
                statusType = "",
                error = null,
                showControls = true
            )

            // Save to history
            mediaRepository.recordWatch(activeMedia)

            val mediaItem = MediaItem.fromUri(Uri.parse(trimmed))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()

            // Resume progress if exists
            if (activeMedia.progressSeconds > 0) {
                val resumeMs = activeMedia.progressSeconds * 1000
                exoPlayer.seekTo(resumeMs)
            }

            exoPlayer.play()
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
        val clamped = positionMs.coerceIn(0, exoPlayer.duration.coerceAtLeast(0))
        exoPlayer.seekTo(clamped)
        _uiState.value = _uiState.value.copy(currentPositionMs = clamped)
        showControlsTemporarily()
    }

    fun skip(seconds: Int) {
        val current = exoPlayer.currentPosition
        val target = (current + (seconds * 1000)).coerceIn(0, exoPlayer.duration.coerceAtLeast(0))
        exoPlayer.seekTo(target)
        _uiState.value = _uiState.value.copy(
            currentPositionMs = target,
            showSkipLeft = seconds < 0,
            showSkipRight = seconds > 0
        )
        showControlsTemporarily()

        viewModelScope.launch {
            delay(600)
            _uiState.value = _uiState.value.copy(
                showSkipLeft = false,
                showSkipRight = false
            )
        }
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
        if (next && exoPlayer.isPlaying) {
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
            if (exoPlayer.isPlaying) {
                _uiState.value = _uiState.value.copy(showControls = false)
            }
        }
    }

    fun toggleBookmark() {
        val current = _uiState.value.currentMedia ?: return
        viewModelScope.launch {
            mediaRepository.toggleBookmark(current)
            val updated = mediaRepository.getMediaDirect(current.url)
            _uiState.value = _uiState.value.copy(currentMedia = updated)
        }
    }

    fun bufferForSeeking(url: String) {
        val targetUrl = url.trim().ifBlank { _uiState.value.currentUrl }
        if (targetUrl.isBlank()) return

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                isDownloadBuffering = true,
                downloadProgressText = "Downloading 0 MB...",
                statusText = "Buffering video...",
                statusType = "warning"
            )

            try {
                val request = Request.Builder().url(targetUrl).build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")

                val body = response.body ?: throw Exception("Empty body")
                val totalLength = body.contentLength()
                val cacheFile = File(getApplication<Application>().cacheDir, "buffered_stream_${System.currentTimeMillis()}.mp4")

                body.byteStream().use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (!isActive) break
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead

                            val mb = String.format("%.1f", totalRead / (1024.0 * 1024.0))
                            val progressStr = if (totalLength > 0) {
                                val totalMb = String.format("%.1f", totalLength / (1024.0 * 1024.0))
                                val pct = (totalRead * 100 / totalLength).toInt()
                                "Downloading $mb MB / $totalMb MB ($pct%)"
                            } else {
                                "Downloading $mb MB..."
                            }

                            withContext(Dispatchers.Main) {
                                _uiState.value = _uiState.value.copy(downloadProgressText = progressStr)
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isDownloadBuffering = false,
                        downloadProgressText = "Fully buffered — seeking works normally.",
                        statusText = "Playing (buffered)",
                        statusType = "live"
                    )

                    val resumePos = exoPlayer.currentPosition
                    val fileUri = Uri.fromFile(cacheFile)
                    exoPlayer.setMediaItem(MediaItem.fromUri(fileUri))
                    exoPlayer.prepare()
                    exoPlayer.seekTo(resumePos)
                    exoPlayer.play()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isDownloadBuffering = false,
                        downloadProgressText = "Buffer failed: ${e.message}",
                        statusText = "Buffer Failed",
                        statusType = "error"
                    )
                }
            }
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressTrackingJob = viewModelScope.launch {
            while (isActive) {
                val currentPos = exoPlayer.currentPosition
                val duration = exoPlayer.duration.coerceAtLeast(0)
                val buffered = exoPlayer.bufferedPosition

                _uiState.value = _uiState.value.copy(
                    currentPositionMs = currentPos,
                    durationMs = duration,
                    bufferedPositionMs = buffered
                )

                // Save to repository periodically
                val url = _uiState.value.currentUrl
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

    private fun stopProgressTracker() {
        progressTrackingJob?.cancel()
        progressTrackingJob = null
    }

    private fun createDefaultMediaFromUrl(url: String): MediaItemEntity {
        var filename = "Video"
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
            filename = "Video"
        }

        return MediaItemEntity(
            url = url,
            title = filename.ifBlank { "Video" },
            source = "Direct Stream"
        )
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressTracker()
        controlsHideJob?.cancel()
        downloadJob?.cancel()
        exoPlayer.release()
    }
}
