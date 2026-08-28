package com.example.fluxplay.data.db

import androidx.room.*
import com.example.fluxplay.data.model.MediaItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items ORDER BY lastPlayedTimestamp DESC")
    fun getAllHistory(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE isBookmark = 1 ORDER BY lastPlayedTimestamp DESC")
    fun getAllBookmarks(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
    suspend fun getMediaById(id: String): MediaItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(media: MediaItemEntity)

    @Query("UPDATE media_items SET lastPositionMs = :positionMs, lastPlayedTimestamp = :timestamp WHERE id = :id")
    suspend fun updatePlaybackPosition(id: String, positionMs: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE media_items SET isBookmark = :isBookmark WHERE id = :id")
    suspend fun setBookmark(id: String, isBookmark: Boolean)

    @Delete
    suspend fun delete(media: MediaItemEntity)

    @Query("DELETE FROM media_items WHERE isBookmark = 0")
    suspend fun clearHistory()

    @Query("DELETE FROM media_items WHERE isBookmark = 1")
    suspend fun clearBookmarks()
}
