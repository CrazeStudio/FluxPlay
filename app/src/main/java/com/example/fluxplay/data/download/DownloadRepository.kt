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
            .connectionPool(okhttp3.ConnectionPool(8, 2, TimeUnit.MINUTES))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun getDownloadsDir(): File {
        val candidates = listOf(
            try { context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) } catch (_: Exception) { null },
            try { context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) } catch (_: Exception) { null },
            try { File(context.filesDir, "downloads") } catch (_: Exception) { null }
        )
        for (candidate in candidates) {
            if (candidate != null) {
                if (!candidate.exists()) {
                    candidate.mkdirs()
                }
                if (candidate.exists() && candidate.canWrite()) {
                    return candidate
                }
            }
        }
        val fallback = File(context.filesDir, "downloads")
        fallback.mkdirs()
        return fallback
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
            cleanUrl.contains(".m3u8", ignoreCase = true) -> ".mp4"
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
                // Ensure parent directory exists
                destinationFile.parentFile?.mkdirs()

                val request = Request.Builder()
                    .url(item.url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "*/*")
                    .header("Connection", "keep-alive")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    throw Exception("HTTP ${response.code}: ${response.message}")
                }

                val body = response.body ?: run {
                    response.close()
                    throw Exception("Empty response body from server")
                }
                contentLength = body.contentLength()

                outputStream = FileOutputStream(destinationFile)
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                val inputStream = body.byteStream()

                var lastUpdateTime = System.currentTimeMillis()
                var bytesSinceLastUpdate = 0L
                var currentSpeed = "0 KB/s"

                try {
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (!isActive) {
                            try {
                                outputStream.close()
                            } catch (_: Exception) {}
                            try {
                                destinationFile.delete()
                            } catch (_: Exception) {}
                            downloadDao.updateStatus(item.id, DownloadStatus.CANCELLED)
                            return@launch
                        }

                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        bytesSinceLastUpdate += bytesRead

                        val now = System.currentTimeMillis()
                        val timeDiff = now - lastUpdateTime

                        if (timeDiff >= 800) {
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
                } finally {
                    try { inputStream.close() } catch (_: Exception) {}
                    try { response.close() } catch (_: Exception) {}
                }

                try {
                    outputStream.flush()
                    outputStream.close()
                } catch (_: Exception) {}
                outputStream = null

                val finalSize = destinationFile.length()
                if (finalSize <= 0) {
                    throw Exception("Downloaded file is empty (0 bytes)")
                }

                // Completed successfully!
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
                    if (destinationFile.exists() && destinationFile.length() == 0L) {
                        destinationFile.delete()
                    }
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
                    val file = File(item.filePath)
                    if (file.exists()) {
                        file.delete()
                    }
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
            // Clean from database
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
                dir.listFiles()?.forEach { 
                    try {
                        it.delete()
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
            // Also check internal files dir
            try {
                val internalDir = File(context.filesDir, "downloads")
                internalDir.listFiles()?.forEach {
                    try {
                        it.delete()
                    } catch (_: Exception) {}
                }
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
            val internalDir = File(context.filesDir, "downloads")
            if (internalDir != dir && internalDir.exists()) {
                internalDir.listFiles()?.forEach {
                    if (it.isFile) {
                        total += it.length()
                    }
                }
            }
        } catch (_: Exception) {}
        return total
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        return try {
            when {
                bytesPerSecond >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0))
                bytesPerSecond >= 1024 -> String.format(java.util.Locale.US, "%.0f KB/s", bytesPerSecond / 1024.0)
                else -> "$bytesPerSecond B/s"
            }
        } catch (_: Exception) {
            "$bytesPerSecond B/s"
        }
    }

    fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = try {
            (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        } catch (_: Exception) {
            0
        }
        return try {
            String.format(java.util.Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        } catch (_: Exception) {
            "$size B"
        }
    }
}
