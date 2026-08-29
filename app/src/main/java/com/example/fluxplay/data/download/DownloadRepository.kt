package com.example.fluxplay.data.download

import android.content.Context
import android.os.Environment
import com.example.fluxplay.data.db.DownloadDao
import com.example.fluxplay.data.db.MediaDao
import com.example.fluxplay.data.model.DownloadItemEntity
import com.example.fluxplay.data.model.DownloadStatus
import com.example.fluxplay.data.model.MediaItemEntity
import com.example.fluxplay.util.MediaTitleFormatter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DownloadRepository(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val mediaDao: MediaDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    val allDownloads: Flow<List<DownloadItemEntity>> = downloadDao.getAllDownloads()
    val completedDownloads: Flow<List<DownloadItemEntity>> = downloadDao.getCompletedDownloads()

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun getDownloadsDir(): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "downloads")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun startDownload(url: String, customTitle: String? = null, poster: String = ""): String {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return ""

        val rawTitle = if (!customTitle.isNullOrBlank()) customTitle else MediaTitleFormatter.extractCleanTitle(null, cleanUrl, context)
        val cleanTitle = MediaTitleFormatter.extractCleanTitle(rawTitle, cleanUrl, context)

        // Determine extension
        val ext = when {
            cleanUrl.contains(".mp4", ignoreCase = true) -> ".mp4"
            cleanUrl.contains(".mkv", ignoreCase = true) -> ".mkv"
            cleanUrl.contains(".webm", ignoreCase = true) -> ".webm"
            cleanUrl.contains(".ts", ignoreCase = true) -> ".ts"
            cleanUrl.contains(".mov", ignoreCase = true) -> ".mov"
            cleanUrl.contains(".m4v", ignoreCase = true) -> ".m4v"
            else -> ".mp4"
        }

        val safeFileName = cleanTitle
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(60)
            .ifBlank { "video_${System.currentTimeMillis()}" }

        val id = UUID.randomUUID().toString()
        val targetFile = File(getDownloadsDir(), "${safeFileName}_${id.take(6)}$ext")

        val downloadItem = DownloadItemEntity(
            id = id,
            url = cleanUrl,
            title = cleanTitle,
            filePath = targetFile.absolutePath,
            poster = poster,
            status = DownloadStatus.DOWNLOADING,
            format = ext.removePrefix(".").uppercase(),
            createdAt = System.currentTimeMillis()
        )

        scope.launch {
            downloadDao.insertOrUpdate(downloadItem)
            executeDownload(downloadItem, targetFile)
        }

        return id
    }

    private fun executeDownload(item: DownloadItemEntity, destinationFile: File) {
        val job = scope.launch {
            var outputStream: FileOutputStream? = null
            var totalBytesRead = 0L
            var contentLength = -1L

            try {
                val request = Request.Builder()
                    .url(item.url)
                    .header("User-Agent", "FluxPlay/2.0 (Android; Universal Media Player)")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw Exception("HTTP error code: ${response.code}")
                }

                val body = response.body ?: throw Exception("Empty response body from server")
                contentLength = body.contentLength()

                outputStream = FileOutputStream(destinationFile)
                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                val inputStream = body.byteStream()

                var lastUpdateTime = System.currentTimeMillis()
                var bytesSinceLastUpdate = 0L
                var currentSpeed = "0 KB/s"

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (!isActive) {
                        outputStream.close()
                        destinationFile.delete()
                        downloadDao.updateStatus(item.id, DownloadStatus.CANCELLED)
                        return@launch
                    }

                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    bytesSinceLastUpdate += bytesRead

                    val now = System.currentTimeMillis()
                    val timeDiff = now - lastUpdateTime

                    if (timeDiff >= 400) {
                        val speedBps = (bytesSinceLastUpdate * 1000) / timeDiff.coerceAtLeast(1)
                        currentSpeed = formatSpeed(speedBps)

                        val percent = if (contentLength > 0) {
                            ((totalBytesRead * 100) / contentLength).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }

                        downloadDao.updateProgress(
                            id = item.id,
                            downloaded = totalBytesRead,
                            total = if (contentLength > 0) contentLength else totalBytesRead,
                            percent = percent,
                            speed = currentSpeed,
                            status = DownloadStatus.DOWNLOADING
                        )

                        lastUpdateTime = now
                        bytesSinceLastUpdate = 0L
                    }
                }

                outputStream.flush()
                outputStream.close()
                outputStream = null

                // Completed successfully!
                val finalSize = destinationFile.length()
                downloadDao.updateProgress(
                    id = item.id,
                    downloaded = finalSize,
                    total = finalSize,
                    percent = 100,
                    speed = "Complete",
                    status = DownloadStatus.COMPLETED
                )
                downloadDao.updateStatus(
                    id = item.id,
                    status = DownloadStatus.COMPLETED,
                    completedAt = System.currentTimeMillis(),
                    error = null
                )

                // Also save to Media Items table as local video for seamless playback
                val mediaEntity = MediaItemEntity(
                    url = destinationFile.absolutePath,
                    title = item.title,
                    poster = item.poster,
                    year = "Offline",
                    type = "Offline Download",
                    source = "Downloaded File",
                    provider = "local",
                    providerId = "download_${item.id}",
                    synopsis = "Downloaded video (${formatSize(finalSize)})"
                )
                mediaDao.insertOrUpdate(mediaEntity)

            } catch (e: CancellationException) {
                try {
                    outputStream?.close()
                    destinationFile.delete()
                } catch (_: Exception) {}
                downloadDao.updateStatus(item.id, DownloadStatus.CANCELLED)
            } catch (e: Exception) {
                try {
                    outputStream?.close()
                } catch (_: Exception) {}
                downloadDao.updateStatus(
                    id = item.id,
                    status = DownloadStatus.FAILED,
                    error = e.localizedMessage ?: "Download failed"
                )
            } finally {
                activeJobs.remove(item.id)
            }
        }

        activeJobs[item.id] = job
    }

    fun cancelDownload(id: String) {
        val job = activeJobs.remove(id)
        job?.cancel()
        scope.launch {
            val item = downloadDao.getDownloadDirect(id)
            if (item != null) {
                try {
                    File(item.filePath).delete()
                } catch (_: Exception) {}
                downloadDao.updateStatus(id, DownloadStatus.CANCELLED)
            }
        }
    }

    fun deleteDownload(item: DownloadItemEntity) {
        cancelDownload(item.id)
        scope.launch {
            try {
                val file = File(item.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {}
            downloadDao.deleteById(item.id)
            mediaDao.deleteByUrl(item.filePath)
            mediaDao.deleteByUrl("file://${item.filePath}")
        }
    }

    fun deleteAllDownloads() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()

        scope.launch {
            try {
                val dir = getDownloadsDir()
                dir.listFiles()?.forEach { it.delete() }
            } catch (_: Exception) {}
            downloadDao.clearAll()
        }
    }

    fun getTotalDownloadsSizeBytes(): Long {
        var total = 0L
        try {
            val dir = getDownloadsDir()
            dir.listFiles()?.forEach {
                if (it.isFile) {
                    total += it.length()
                }
            }
        } catch (_: Exception) {}
        return total
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0))
            bytesPerSecond >= 1024 -> String.format("%.0f KB/s", bytesPerSecond / 1024.0)
            else -> "$bytesPerSecond B/s"
        }
    }

    fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
