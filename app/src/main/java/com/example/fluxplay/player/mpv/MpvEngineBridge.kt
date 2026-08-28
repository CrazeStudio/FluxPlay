package com.example.fluxplay.player.mpv

import android.content.Context
import android.util.Log
import `is`.xyz.mpv.MPV
import java.io.File
import java.io.FileOutputStream

class MpvEngineBridge(private val context: Context) {
    private var mpvInstance: MPV? = null
    private var isInitialized = false

    fun getMpv(): MPV? = mpvInstance

    fun setMpvInstance(mpv: MPV) {
        this.mpvInstance = mpv
    }

    fun initializeMpv(mpv: MPV, hardwareAcceleration: Boolean = true) {
        this.mpvInstance = mpv
        try {
            val configDir = File(context.filesDir, "mpv_config")
            if (!configDir.exists()) configDir.mkdirs()

            // Prepare basic mpv.conf
            val confFile = File(configDir, "mpv.conf")
            if (!confFile.exists()) {
                val defaultConf = buildString {
                    appendLine("vo=gpu")
                    appendLine("gpu-context=android")
                    appendLine("opengl-es=yes")
                    if (hardwareAcceleration) {
                        appendLine("hwdec=auto")
                    } else {
                        appendLine("hwdec=no")
                    }
                    appendLine("cache=yes")
                    appendLine("demuxer-max-bytes=64MiB")
                    appendLine("demuxer-max-back-bytes=32MiB")
                }
                FileOutputStream(confFile).use { it.write(defaultConf.toByteArray()) }
            }

            mpv.setOptionString("vo", "gpu")
            mpv.setOptionString("gpu-context", "android")
            mpv.setOptionString("opengl-es", "yes")
            if (hardwareAcceleration) {
                mpv.setOptionString("hwdec", "auto")
            } else {
                mpv.setOptionString("hwdec", "no")
            }
            mpv.setOptionString("keep-open", "yes")
            isInitialized = true
        } catch (e: Exception) {
            Log.e("MpvEngineBridge", "Failed to configure MPV options", e)
        }
    }

    fun loadMedia(uri: String, startPositionSec: Double = 0.0) {
        val mpv = mpvInstance ?: return
        try {
            if (startPositionSec > 0) {
                mpv.command("loadfile", uri, "replace", "start=$startPositionSec")
            } else {
                mpv.command("loadfile", uri, "replace")
            }
            mpv.setPropertyBoolean("pause", false)
        } catch (e: Exception) {
            Log.e("MpvEngineBridge", "Failed to load media in MPV", e)
        }
    }

    fun play() {
        mpvInstance?.setPropertyBoolean("pause", false)
    }

    fun pause() {
        mpvInstance?.setPropertyBoolean("pause", true)
    }

    fun stop() {
        try {
            mpvInstance?.command("stop")
        } catch (e: Exception) {
            Log.e("MpvEngineBridge", "Error stopping MPV", e)
        }
    }

    fun seekTo(seconds: Double) {
        try {
            mpvInstance?.command("seek", seconds.toString(), "absolute")
        } catch (e: Exception) {
            Log.e("MpvEngineBridge", "Seek error in MPV", e)
        }
    }

    fun setSpeed(speed: Double) {
        try {
            mpvInstance?.setPropertyDouble("speed", speed)
        } catch (e: Exception) {
            Log.e("MpvEngineBridge", "Speed set error in MPV", e)
        }
    }

    fun setAudioTrack(trackId: Int) {
        try {
            mpvInstance?.setPropertyInt("aid", trackId)
        } catch (e: Exception) {
            Log.e("MpvEngineBridge", "Set audio track error in MPV", e)
        }
    }

    fun setSubtitleTrack(trackId: Int) {
        try {
            mpvInstance?.setPropertyInt("sid", trackId)
        } catch (e: Exception) {
            Log.e("MpvEngineBridge", "Set subtitle track error in MPV", e)
        }
    }

    fun getDuration(): Double {
        return mpvInstance?.getPropertyDouble("duration") ?: 0.0
    }

    fun getTimePos(): Double {
        return mpvInstance?.getPropertyDouble("time-pos") ?: 0.0
    }

    fun isPaused(): Boolean {
        return mpvInstance?.getPropertyBoolean("pause") ?: true
    }
}
