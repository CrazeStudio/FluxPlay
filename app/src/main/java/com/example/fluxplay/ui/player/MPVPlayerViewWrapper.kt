package com.example.fluxplay.ui.player

import android.content.Context
import android.util.AttributeSet
import `is`.xyz.mpv.BaseMPVView
import java.io.File

class MPVPlayerViewWrapper @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BaseMPVView(context, attrs) {

    private var onMpvReadyListener: ((MPVPlayerViewWrapper) -> Unit)? = null

    fun setOnMpvReadyListener(listener: (MPVPlayerViewWrapper) -> Unit) {
        onMpvReadyListener = listener
    }

    fun initPlayer() {
        val configDir = File(context.filesDir, "mpv_config").absolutePath
        val cacheDir = File(context.cacheDir, "mpv_cache").absolutePath
        File(configDir).mkdirs()
        File(cacheDir).mkdirs()
        initialize(configDir, cacheDir)
    }

    override fun initOptions() {
        // Pre-init options
    }

    override fun postInitOptions() {
        try {
            mpv.setOptionString("vo", "gpu")
            mpv.setOptionString("gpu-context", "android")
            mpv.setOptionString("opengl-es", "yes")
            mpv.setOptionString("hwdec", "auto")
            mpv.setOptionString("keep-open", "yes")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        onMpvReadyListener?.invoke(this)
    }

    override fun observeProperties() {
        // Can observe mpv properties
    }
}
