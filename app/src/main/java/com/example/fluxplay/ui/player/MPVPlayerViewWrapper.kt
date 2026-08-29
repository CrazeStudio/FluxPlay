package com.example.fluxplay.ui.player

import android.content.Context
import android.util.AttributeSet
import `is`.xyz.mpv.BaseMPVView

class MPVPlayerViewWrapper @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseMPVView(context, attrs) {

    override fun initOptions() {
        mpv.setOptionString("hwdec", "auto-safe")
        mpv.setOptionString("vo", "gpu")
        mpv.setOptionString("gpu-context", "android")
        mpv.setOptionString("demuxer-max-bytes", "67108864")
        mpv.setOptionString("demuxer-max-back-bytes", "33554432")
        mpv.setOptionString("sub-auto", "fuzzy")
        mpv.setOptionString("audio-file-auto", "fuzzy")
        mpv.setOptionString("keep-open", "yes")
        mpv.setOptionString("hr-seek", "yes")
    }

    override fun postInitOptions() {}

    override fun observeProperties() {}
}
