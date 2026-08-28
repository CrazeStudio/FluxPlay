package com.example.fluxplay.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class PlayerEngine {
    EXOPLAYER,
    LIBMPV
}

enum class MediaType {
    LOCAL_VIDEO,
    HLS_STREAM,
    DASH_STREAM,
    DIRECT_URL,
    M3U_CHANNEL
}

enum class ResizeMode {
    FIT,
    FILL,
    ZOOM,
    ORIGINAL_16_9,
    ORIGINAL_4_3
}

@Serializable
@Entity(tableName = "media_items")
data class MediaItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val uri: String,
    val mediaType: MediaType = MediaType.DIRECT_URL,
    val durationMs: Long = 0L,
    val lastPositionMs: Long = 0L,
    val thumbnailUri: String? = null,
    val groupTitle: String? = null,
    val isBookmark: Boolean = false,
    val lastPlayedTimestamp: Long = System.currentTimeMillis(),
    val playCount: Int = 1
)

@Serializable
data class MediaTrackInfo(
    val id: String,
    val label: String,
    val language: String? = null,
    val isSelected: Boolean = false,
    val mimeType: String? = null
)

@Serializable
data class AppSettings(
    val defaultEngine: PlayerEngine = PlayerEngine.EXOPLAYER,
    val hardwareAcceleration: Boolean = true,
    val autoPlayNext: Boolean = true,
    val backgroundPlay: Boolean = false,
    val selectedTheme: String = "Dark",
    val defaultBufferMs: Int = 50000,
    val rememberLastPosition: Boolean = true,
    val gestureBrightness: Boolean = true,
    val gestureVolume: Boolean = true,
    val gestureSeek: Boolean = true,
    val doubleTapSeekSeconds: Int = 10,
    val customM3uUrl: String = ""
)
