sed -i 's/exoPlayer?.release()/exoPlayer?.release()\n        mpvPlayer?.destroy()\n        mpvPlayer = null/' app/src/main/java/com/example/fluxplay/ui/player/PlayerViewModel.kt
