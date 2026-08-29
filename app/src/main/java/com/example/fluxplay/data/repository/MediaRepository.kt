package com.example.fluxplay.data.repository

import com.example.fluxplay.data.db.MediaDao
import com.example.fluxplay.data.model.MediaItemEntity
import com.example.fluxplay.util.MediaTitleFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MediaRepository(private val mediaDao: MediaDao) {

    fun getAllStreams(): Flow<List<MediaItemEntity>> = mediaDao.getAllStreams()

    fun getWatchHistory(): Flow<List<MediaItemEntity>> = mediaDao.getWatchHistory()

    fun getBookmarks(): Flow<List<MediaItemEntity>> = mediaDao.getBookmarks()

    fun getMediaByUrl(url: String): Flow<MediaItemEntity?> = mediaDao.getMediaByUrl(url)

    suspend fun getMediaDirect(url: String): MediaItemEntity? = mediaDao.getMediaDirect(url)

    suspend fun saveOrUpdateMedia(item: MediaItemEntity) = mediaDao.insertOrUpdate(item)

    suspend fun updateProgress(url: String, progress: Long, duration: Long) {
        val now = System.currentTimeMillis()
        mediaDao.updateProgress(url, progress, duration, now)
    }

    suspend fun toggleBookmark(item: MediaItemEntity) {
        val newBookmarkState = !item.isBookmarked
        val bookmarkedAt = if (newBookmarkState) System.currentTimeMillis() else 0L
        mediaDao.setBookmarked(item.url, newBookmarkState, bookmarkedAt)
    }

    suspend fun deleteMedia(url: String) = mediaDao.deleteByUrl(url)

    suspend fun clearHistory() = mediaDao.clearHistory()

    suspend fun clearBookmarks() = mediaDao.clearBookmarks()

    suspend fun clearAll() = mediaDao.clearAll()

    suspend fun importM3uPlaylist(content: String): Int = withContext(Dispatchers.IO) {
        val lines = content.lines()
        val items = mutableListOf<MediaItemEntity>()
        var currentTitle = ""
        var currentLogo = ""
        var currentGroup = "General"

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:")) {
                // Parse #EXTINF:-1 tvg-logo="url" group-title="group",Title
                val commaIndex = trimmed.lastIndexOf(',')
                currentTitle = if (commaIndex != -1 && commaIndex < trimmed.length - 1) {
                    trimmed.substring(commaIndex + 1).trim()
                } else {
                    "Stream ${items.size + 1}"
                }

                // Try to extract logo if available
                val logoMatch = Regex("""tvg-logo="([^"]+)"""").find(trimmed)
                currentLogo = logoMatch?.groupValues?.getOrNull(1) ?: ""

                // Try to extract group if available
                val groupMatch = Regex("""group-title="([^"]+)"""").find(trimmed)
                currentGroup = groupMatch?.groupValues?.getOrNull(1) ?: "Playlist"
            } else if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("rtsp://") || trimmed.startsWith("content://") || trimmed.startsWith("file://")) {
                    val streamTitle = MediaTitleFormatter.extractCleanTitle(currentTitle, trimmed)
                    items.add(
                        MediaItemEntity(
                            url = trimmed,
                            title = streamTitle,
                            poster = currentLogo,
                            year = "IPTV",
                            type = if (trimmed.contains(".m3u8")) "HLS Live" else "Direct Video",
                            source = "Playlist",
                            provider = "playlist",
                            providerId = "m3u_${System.currentTimeMillis()}_${items.size}",
                            synopsis = "Imported channel: $streamTitle ($currentGroup)"
                        )
                    )
                    currentTitle = ""
                    currentLogo = ""
                    currentGroup = "General"
                }
            }
        }

        if (items.isNotEmpty()) {
            mediaDao.insertAll(items)
        }
        items.size
    }
}
