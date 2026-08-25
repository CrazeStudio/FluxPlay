package com.example.fluxplay.data.repository

import com.example.fluxplay.data.db.MediaDao
import com.example.fluxplay.data.model.MediaItemEntity
import kotlinx.coroutines.flow.Flow

class MediaRepository(private val mediaDao: MediaDao) {

    val watchHistory: Flow<List<MediaItemEntity>> = mediaDao.getWatchHistory()
    val bookmarks: Flow<List<MediaItemEntity>> = mediaDao.getBookmarks()

    fun getMediaByUrl(url: String): Flow<MediaItemEntity?> = mediaDao.getMediaByUrl(url)

    suspend fun getMediaDirect(url: String): MediaItemEntity? = mediaDao.getMediaDirect(url)

    suspend fun recordWatch(media: MediaItemEntity) {
        val existing = mediaDao.getMediaDirect(media.url)
        val updated = if (existing != null) {
            existing.copy(
                lastWatchedAt = System.currentTimeMillis(),
                title = if (media.title.isNotBlank() && media.title != "Video") media.title else existing.title,
                poster = media.poster.ifBlank { existing.poster },
                year = media.year.ifBlank { existing.year },
                type = if (media.type != "Video") media.type else existing.type,
                rating = media.rating.ifBlank { existing.rating },
                source = media.source.ifBlank { existing.source },
                provider = media.provider.ifBlank { existing.provider },
                synopsis = media.synopsis.ifBlank { existing.synopsis },
                genres = if (media.genres.isNotEmpty()) media.genres else existing.genres,
                cast = if (media.cast.isNotEmpty()) media.cast else existing.cast,
                studios = if (media.studios.isNotEmpty()) media.studios else existing.studios,
                sourceUrl = media.sourceUrl.ifBlank { existing.sourceUrl }
            )
        } else {
            media.copy(lastWatchedAt = System.currentTimeMillis())
        }
        mediaDao.insertOrUpdate(updated)
    }

    suspend fun updatePlaybackProgress(url: String, progressSec: Long, durationSec: Long) {
        if (url.isBlank()) return
        val existing = mediaDao.getMediaDirect(url)
        if (existing != null) {
            mediaDao.updateProgress(url, progressSec, durationSec, System.currentTimeMillis())
        } else {
            mediaDao.insertOrUpdate(
                MediaItemEntity(
                    url = url,
                    progressSeconds = progressSec,
                    durationSeconds = durationSec,
                    lastWatchedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun toggleBookmark(media: MediaItemEntity) {
        val existing = mediaDao.getMediaDirect(media.url)
        if (existing != null) {
            val nextState = !existing.isBookmarked
            mediaDao.setBookmarked(
                url = media.url,
                isBookmarked = nextState,
                bookmarkedAt = if (nextState) System.currentTimeMillis() else 0
            )
        } else {
            mediaDao.insertOrUpdate(
                media.copy(
                    isBookmarked = true,
                    bookmarkedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteMedia(url: String) {
        mediaDao.deleteByUrl(url)
    }

    suspend fun clearHistory() {
        mediaDao.clearHistory()
    }

    suspend fun clearBookmarks() {
        mediaDao.clearBookmarks()
    }

    suspend fun clearAll() {
        mediaDao.clearAll()
    }

    suspend fun insertAll(items: List<MediaItemEntity>) {
        mediaDao.insertAll(items)
    }
}
