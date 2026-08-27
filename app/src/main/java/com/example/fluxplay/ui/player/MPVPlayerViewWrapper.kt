package com.example.fluxplay.ui.player

import android.content.Context
import android.util.AttributeSet
import `is`.xyz.mpv.BaseMPVView

class MPVPlayerViewWrapper @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseMPVView(context, attrs) {

    override fun initOptions() {
        mpv.setOptionString("hwdec", "auto")
        mpv.setOptionString("vo", "gpu")
    }

    override fun postInitOptions() {}

    override fun observeProperties() {}
}
