package com.example.fluxplay.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "media_items")
data class MediaItemEntity(
    @PrimaryKey
    val url: String,
    val title: String = "",
    val poster: String = "",
    val year: String = "",
    val type: String = "video",
    val rating: String = "",
    val source: String = "Direct",
    val provider: String = "custom",
    val providerId: String = "",
    val synopsis: String = "",
    val duration: String = "",
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val studios: List<String> = emptyList(),
    val sourceUrl: String = "",
    val trailerUrl: String = "",
    val progressSeconds: Long = 0L,
    val durationSeconds: Long = 0L,
    val isBookmarked: Boolean = false,
    val bookmarkedAt: Long = 0L,
    val lastWatchedAt: Long = 0L
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Serializable
@Entity(tableName = "download_items")
data class DownloadItemEntity(
    @PrimaryKey
    val id: String,
    val url: String,
    val title: String = "",
    val filePath: String = "",
    val poster: String = "",
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val progressPercent: Int = 0,
    val speedFormatted: String = "",
    val status: DownloadStatus = DownloadStatus.PENDING,
    val format: String = "MP4",
    val durationMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L,
    val errorMessage: String? = null
)

enum class AppThemeMode(val id: String, val displayName: String, val subtitle: String) {
    AMOLED_MIDNIGHT("amoled", "AMOLED Midnight", "Pitch black background with electric violet & cyan accents"),
    CYBERPUNK_NEON("cyberpunk", "Cyberpunk Neon", "High-tech navy with vibrant neon magenta & cyan highlights"),
    CRIMSON_FLAME("crimson", "Crimson Flame", "Deep obsidian with fiery scarlet & ember accents"),
    EMERALD_MATRIX("emerald", "Emerald Matrix", "Forest carbon with luminous matrix jade green"),
    SUNSET_GOLD("sunset", "Sunset Amber", "Warm espresso with radiant sunset gold & orange"),
    NORDIC_SLATE("nordic", "Nordic Slate", "Deep arctic slate with crisp glacial sky blue")
}

enum class PlayerEngine(val id: String, val displayName: String, val badge: String, val description: String) {
    EXOPLAYER("exoplayer", "Google Media3 Player", "Media3 Core", "Google's official native Android player with adaptive HLS/DASH/MP4 streaming"),
    JWPLAYER("jwplayer", "JW Player (JWX Real Engine)", "JWX Pro", "Authentic JWX ultra-low latency player engine with instant startup, JWX scrubber & buffer telemetry"),
    LIBMPV("libmpv", "libmpv Engine (FFmpeg & ASS)", "libmpv", "High-performance FFmpeg-backed demuxing, stylised ASS subtitle rendering & dual-audio sync"),
    MKV_HARDWARE("mkv_hardware", "Ultra MKV & Codec Engine", "MKV Hardware", "Optimized hardware decoding for Matroska (MKV), HEVC/AV1, multi-audio & ASS subtitles"),
    VIMEO("vimeo", "Vimeo Cinematic", "Minimalist", "Ultra-clean borderless aesthetic with smooth center controls and glassmorphism")
}

data class PlayerSettings(
    val selectedEngine: PlayerEngine = PlayerEngine.EXOPLAYER,
    val selectedTheme: AppThemeMode = AppThemeMode.AMOLED_MIDNIGHT,
    val backgroundPlayEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val hardwareAcceleration: Boolean = true,
    val autoResume: Boolean = true,
    val defaultAudioTrack: String = "Default",
    val defaultSubtitleTrack: String = "Auto",
    val bufferSizeMb: Int = 32,
    val videoAspectMode: String = "Fit Screen",
    val showMediaControlsOverlay: Boolean = true
)

data class DiscoveryCategory(
    val title: String,
    val provider: String,
    val items: List<MediaItemEntity>
)

data class BackupData(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val items: List<MediaItemEntity> = emptyList()
)
