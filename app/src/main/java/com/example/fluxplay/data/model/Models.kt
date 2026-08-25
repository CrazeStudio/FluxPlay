package com.example.fluxplay.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "media_items")
data class MediaItemEntity(
    @PrimaryKey
    val url: String,
    val title: String = "Video",
    val poster: String = "",
    val year: String = "",
    val type: String = "Video",
    val rating: String = "",
    val source: String = "Direct",
    val provider: String = "",
    val providerId: String = "",
    val synopsis: String = "",
    val duration: String = "",
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val studios: List<String> = emptyList(),
    val sourceUrl: String = "",
    val trailerUrl: String = "",
    val progressSeconds: Long = 0,
    val durationSeconds: Long = 0,
    val isBookmarked: Boolean = false,
    val bookmarkedAt: Long = 0,
    val lastWatchedAt: Long = 0
)

@Serializable
data class AppSettings(
    val theme: String = "dark", // "dark", "light", "system"
    val primaryColorHex: String = "#A78BFA",
    val accentColorHex: String = "#F43F5E",
    val tmdbKey: String = "",
    val lbxUsername: String = "",
    val lbxClientId: String = "",
    val lbxClientSecret: String = ""
)

@Serializable
data class DiscoverItem(
    val id: String,
    val provider: String,
    val source: String = provider,
    val title: String,
    val nativeTitle: String = "",
    val year: String = "",
    val type: String = "Movie",
    val rating: String = "",
    val poster: String = "",
    val synopsis: String = "",
    val episodes: String = "",
    val duration: String = "",
    val status: String = "",
    val genres: List<String> = emptyList(),
    val studios: List<String> = emptyList(),
    val characters: List<String> = emptyList(),
    val sourceUrl: String = "",
    val trailerUrl: String = ""
)

data class DiscoverSection(
    val title: String,
    val provider: String,
    val items: List<DiscoverItem>
)

@Serializable
data class BackupData(
    val app: String = "Fluxplay",
    val version: String = "2.0",
    val createdAt: Long = System.currentTimeMillis(),
    val history: List<MediaItemEntity> = emptyList(),
    val bookmarks: List<MediaItemEntity> = emptyList(),
    val settings: AppSettings = AppSettings()
)
