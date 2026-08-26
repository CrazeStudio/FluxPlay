package com.example.fluxplay.data.cache

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class)
object FluxplayMediaCache {

    private const val TAG = "FluxplayMediaCache"
    private const val MAX_CACHE_BYTES = 1536L * 1024L * 1024L // 1.5 GB Cache Limit

    @Volatile
    private var simpleCache: SimpleCache? = null

    @Volatile
    private var databaseProvider: StandaloneDatabaseProvider? = null

    private var activeDownloadJob: Job? = null
    private var activeCacheWriter: CacheWriter? = null

    data class CacheProgress(
        val isDownloading: Boolean = false,
        val url: String = "",
        val cachedBytes: Long = 0,
        val totalBytes: Long = 0,
        val percentage: Int = 0,
        val isComplete: Boolean = false,
        val error: String? = null
    )

    private val _downloadProgress = MutableStateFlow(CacheProgress())
    val downloadProgress: StateFlow<CacheProgress> = _downloadProgress.asStateFlow()

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheDir = File(context.applicationContext.cacheDir, "fluxplay_media_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
            val dbProvider = StandaloneDatabaseProvider(context.applicationContext)
            databaseProvider = dbProvider
            simpleCache = SimpleCache(cacheDir, evictor, dbProvider)
            Log.i(TAG, "SimpleCache initialized at ${cacheDir.absolutePath} with max size: 1.5GB")
        }
        return simpleCache!!
    }

    fun createCacheDataSourceFactory(
        context: Context,
        upstreamFactory: DataSource.Factory
    ): CacheDataSource.Factory {
        val cache = getCache(context)
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(
                CacheDataSink.Factory()
                    .setCache(cache)
                    .setFragmentSize(CacheDataSink.DEFAULT_FRAGMENT_SIZE)
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Start active background caching/downloading of the stream while playing
     */
    fun startDownloadAndCache(
        context: Context,
        url: String,
        scope: CoroutineScope
    ) {
        if (url.isBlank() || !url.startsWith("http")) return

        // Cancel any existing active caching job
        cancelDownload()

        _downloadProgress.value = CacheProgress(
            isDownloading = true,
            url = url,
            percentage = 0
        )

        activeDownloadJob = scope.launch(Dispatchers.IO) {
            try {
                val cache = getCache(context)
                val httpFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile; Fluxplay Native Engine)")
                    .setConnectTimeoutMs(8000)
                    .setReadTimeoutMs(15000)
                    .setAllowCrossProtocolRedirects(true)

                val cacheDataSource = CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(httpFactory)
                    .setCacheWriteDataSinkFactory(
                        CacheDataSink.Factory()
                            .setCache(cache)
                            .setFragmentSize(CacheDataSink.DEFAULT_FRAGMENT_SIZE)
                    )
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                    .createDataSource()

                val uri = Uri.parse(url)
                val dataSpec = DataSpec.Builder()
                    .setUri(uri)
                    .setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
                    .build()

                val progressListener = CacheWriter.ProgressListener { requestLength, bytesCached, _ ->
                    val percent = if (requestLength > 0) {
                        ((bytesCached.toFloat() / requestLength.toFloat()) * 100).toInt().coerceIn(0, 100)
                    } else {
                        0
                    }
                    _downloadProgress.value = CacheProgress(
                        isDownloading = true,
                        url = url,
                        cachedBytes = bytesCached,
                        totalBytes = requestLength,
                        percentage = percent,
                        isComplete = (requestLength > 0 && bytesCached >= requestLength)
                    )
                }

                val cacheWriter = CacheWriter(cacheDataSource, dataSpec, null, progressListener)
                activeCacheWriter = cacheWriter

                Log.d(TAG, "Starting CacheWriter for URL: $url")
                cacheWriter.cache()

                _downloadProgress.value = CacheProgress(
                    isDownloading = false,
                    url = url,
                    isComplete = true,
                    percentage = 100
                )
                Log.d(TAG, "Completed caching for URL: $url")
            } catch (e: Exception) {
                Log.e(TAG, "CacheWriter download error", e)
                _downloadProgress.value = CacheProgress(
                    isDownloading = false,
                    url = url,
                    error = e.message
                )
            } finally {
                activeCacheWriter = null
            }
        }
    }

    fun cancelDownload() {
        try {
            activeCacheWriter?.cancel()
            activeDownloadJob?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling CacheWriter", e)
        }
        activeCacheWriter = null
        activeDownloadJob = null
        _downloadProgress.value = _downloadProgress.value.copy(isDownloading = false)
    }

    fun getCachedBytesForUrl(context: Context, url: String): Long {
        return try {
            val cache = getCache(context)
            cache.getCachedBytes(url, 0, -1)
        } catch (e: Exception) {
            0L
        }
    }

    fun getTotalCacheSize(context: Context): Long {
        return try {
            getCache(context).cacheSpace
        } catch (e: Exception) {
            0L
        }
    }

    fun removeResourceForUrl(context: Context, url: String) {
        if (url.isBlank()) return
        try {
            val cache = getCache(context)
            cache.removeResource(url)
            val uri = Uri.parse(url)
            val uriKey = uri.toString()
            cache.removeResource(uriKey)
            for (key in cache.keys.toList()) {
                if (key == url || key == uriKey || key.contains(url) || url.contains(key)) {
                    cache.removeResource(key)
                }
            }
            Log.i(TAG, "Successfully removed cache for watched video: $url")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing video cache for $url", e)
        }
    }

    fun clearAllCache(context: Context) {
        try {
            cancelDownload()
            val cache = getCache(context)
            val keys = cache.keys
            for (key in keys) {
                cache.removeResource(key)
            }
            Log.i(TAG, "Fluxplay media cache cleared.")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }
}
