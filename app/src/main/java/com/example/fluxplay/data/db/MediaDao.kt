package com.example.fluxplay.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fluxplay.data.model.MediaItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: MediaItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MediaItemEntity>)

    @Query("UPDATE media_items SET progressSeconds = :progress, durationSeconds = :duration, lastWatchedAt = :watchedAt WHERE url = :url")
    suspend fun updateProgress(url: String, progress: Long, duration: Long, watchedAt: Long)

    @Query("UPDATE media_items SET isBookmarked = :isBookmarked, bookmarkedAt = :bookmarkedAt WHERE url = :url")
    suspend fun setBookmarked(url: String, isBookmarked: Boolean, bookmarkedAt: Long)

    @Query("DELETE FROM media_items WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("UPDATE media_items SET lastWatchedAt = 0 WHERE lastWatchedAt > 0")
    suspend fun clearHistory()

    @Query("UPDATE media_items SET isBookmarked = 0")
    suspend fun clearBookmarks()

    @Query("DELETE FROM media_items")
    suspend fun clearAll()

    @Query("SELECT * FROM media_items WHERE lastWatchedAt > 0 ORDER BY lastWatchedAt DESC LIMIT 100")
    fun getWatchHistory(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE isBookmarked = 1 ORDER BY bookmarkedAt DESC")
    fun getBookmarks(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items ORDER BY lastWatchedAt DESC, url ASC")
    fun getAllStreams(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE url = :url LIMIT 1")
    fun getMediaByUrl(url: String): Flow<MediaItemEntity?>

    @Query("SELECT * FROM media_items WHERE url = :url LIMIT 1")
    suspend fun getMediaDirect(url: String): MediaItemEntity?
}
