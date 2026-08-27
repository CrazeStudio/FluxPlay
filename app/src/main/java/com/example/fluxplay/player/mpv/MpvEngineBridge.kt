package com.example.fluxplay.player.mpv

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MpvEngineBridge(private val context: Context) {

    private val _isInitialized = MutableStateFlow(true)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _hwDec = MutableStateFlow("mediacodec-copy")
    val hwDec: StateFlow<String> = _hwDec.asStateFlow()

    private val _audioTracks = MutableStateFlow(listOf("Track 1 (Stereo AAC)", "Track 2 (5.1 Dolby Digital)", "Track 3 (Director Commentary)"))
    val audioTracks: StateFlow<List<String>> = _audioTracks.asStateFlow()

    private val _selectedAudioTrack = MutableStateFlow(0)
    val selectedAudioTrack: StateFlow<Int> = _selectedAudioTrack.asStateFlow()

    private val _subtitleTracks = MutableStateFlow(listOf("Off", "English (SDH)", "Spanish", "French", "Japanese"))
    val subtitleTracks: StateFlow<List<String>> = _subtitleTracks.asStateFlow()

    private val _selectedSubtitleTrack = MutableStateFlow(1)
    val selectedSubtitleTrack: StateFlow<Int> = _selectedSubtitleTrack.asStateFlow()

    private var currentSurface: Surface? = null

    fun attachSurface(surfaceTexture: SurfaceTexture) {
        currentSurface = Surface(surfaceTexture)
    }

    fun detachSurface() {
        currentSurface?.release()
        currentSurface = null
    }

    fun selectAudioTrack(index: Int) {
        if (index in _audioTracks.value.indices) {
            _selectedAudioTrack.value = index
        }
    }

    fun selectSubtitleTrack(index: Int) {
        if (index in _subtitleTracks.value.indices) {
            _selectedSubtitleTrack.value = index
        }
    }

    fun setHwDec(enabled: Boolean) {
        _hwDec.value = if (enabled) "mediacodec-copy" else "no (software)"
    }
}
