package com.example.fluxplay.data.db

import androidx.room.*
import com.example.fluxplay.data.model.DownloadItemEntity
import com.example.fluxplay.data.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: DownloadItemEntity)

    @Query("SELECT * FROM download_items ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadItemEntity>>

    @Query("SELECT * FROM download_items WHERE status = 'COMPLETED' ORDER BY completedAt DESC, createdAt DESC")
    fun getCompletedDownloads(): Flow<List<DownloadItemEntity>>

    @Query("SELECT * FROM download_items WHERE id = :id LIMIT 1")
    fun getDownloadById(id: String): Flow<DownloadItemEntity?>

    @Query("SELECT * FROM download_items WHERE id = :id LIMIT 1")
    suspend fun getDownloadDirect(id: String): DownloadItemEntity?

    @Query("SELECT * FROM download_items WHERE url = :url LIMIT 1")
    suspend fun getDownloadByUrl(url: String): DownloadItemEntity?

    @Query("UPDATE download_items SET downloadedBytes = :downloaded, totalBytes = :total, progressPercent = :percent, speedFormatted = :speed, status = :status WHERE id = :id")
    suspend fun updateProgress(id: String, downloaded: Long, total: Long, percent: Int, speed: String, status: DownloadStatus)

    @Query("UPDATE download_items SET status = :status, completedAt = :completedAt, errorMessage = :error WHERE id = :id")
    suspend fun updateStatus(id: String, status: DownloadStatus, completedAt: Long = 0L, error: String? = null)

    @Query("DELETE FROM download_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM download_items")
    suspend fun clearAll()
}
