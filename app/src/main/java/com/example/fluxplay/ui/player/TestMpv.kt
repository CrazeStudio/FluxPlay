package com.example.fluxplay.ui.player

import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.BaseMPVView

fun test(mpvView: BaseMPVView) {
    mpvView.initialize("/tmp/config", "/tmp/cache")
    mpvView.playFile("http://example.com/video.mp4")
}
