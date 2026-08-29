sed -i '/fun togglePlayPause()/i \
    fun pause() {\
        if (_uiState.value.selectedEngine == PlayerEngine.LIBMPV) {\
            mpvPlayer?.setPropertyBoolean("pause", true)\
            setControlsVisibility(true)\
            return\
        }\
        exoPlayer?.pause()\
        setControlsVisibility(true)\
    }\
' app/src/main/java/com/example/fluxplay/ui/player/PlayerViewModel.kt
