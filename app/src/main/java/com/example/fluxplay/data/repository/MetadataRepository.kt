package com.example.fluxplay.data.repository

import com.example.fluxplay.data.model.MediaItemEntity
import com.example.fluxplay.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class MetadataRepository {

    val defaultSampleStreams: List<MediaItemEntity> = listOf(
        MediaItemEntity(
            id = "sample_big_buck_bunny",
            title = "Big Buck Bunny (HLS 1080p)",
            uri = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            mediaType = MediaType.HLS_STREAM,
            thumbnailUri = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=400",
            groupTitle = "Test Streams"
        ),
        MediaItemEntity(
            id = "sample_sintel_dash",
            title = "Sintel (DASH 4K Multi-Audio)",
            uri = "https://dash.akamaized.net/akamai/test/caption_test/ElephantsDream/elephants_dream_480p_heaac5_1.mpd",
            mediaType = MediaType.DASH_STREAM,
            thumbnailUri = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=400",
            groupTitle = "Animation"
        ),
        MediaItemEntity(
            id = "sample_tears_of_steel",
            title = "Tears of Steel (MP4 4K)",
            uri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            mediaType = MediaType.DIRECT_URL,
            thumbnailUri = "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=400",
            groupTitle = "Sci-Fi"
        ),
        MediaItemEntity(
            id = "sample_nasa_live",
            title = "NASA TV Live Feed (HLS)",
            uri = "https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-HLS/master.m3u8",
            mediaType = MediaType.HLS_STREAM,
            thumbnailUri = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=400",
            groupTitle = "Live News & Science"
        ),
        MediaItemEntity(
            id = "sample_we_are_going_on_bullrun",
            title = "We Are Going on Bullrun (MP4 1080p)",
            uri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
            mediaType = MediaType.DIRECT_URL,
            thumbnailUri = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=400",
            groupTitle = "Action"
        ),
        MediaItemEntity(
            id = "sample_iptv_stream",
            title = "Red Bull TV (HLS Multi-Bitrate)",
            uri = "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8",
            mediaType = MediaType.HLS_STREAM,
            thumbnailUri = "https://images.unsplash.com/photo-1517649763962-0c623266ddc0?w=400",
            groupTitle = "Sports & Live"
        )
    )

    suspend fun parseM3uPlaylist(urlStr: String): List<MediaItemEntity> = withContext(Dispatchers.IO) {
        val result = mutableListOf<MediaItemEntity>()
        try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            val reader = BufferedReader(InputStreamReader(connection.inputStream))

            var line: String?
            var currentTitle = ""
            var currentGroup = "M3U Playlist"
            var currentLogo: String? = null

            while (reader.readLine().also { line = it } != null) {
                val trimmed = line?.trim() ?: continue
                if (trimmed.startsWith("#EXTINF:")) {
                    val titleIndex = trimmed.lastIndexOf(',')
                    currentTitle = if (titleIndex != -1 && titleIndex < trimmed.length - 1) {
                        trimmed.substring(titleIndex + 1).trim()
                    } else {
                        "Channel ${result.size + 1}"
                    }

                    val groupRegex = Regex("group-title=\"([^\"]+)\"")
                    val groupMatch = groupRegex.find(trimmed)
                    if (groupMatch != null) {
                        currentGroup = groupMatch.groupValues[1]
                    }

                    val logoRegex = Regex("tvg-logo=\"([^\"]+)\"")
                    val logoMatch = logoRegex.find(trimmed)
                    if (logoMatch != null) {
                        currentLogo = logoMatch.groupValues[1]
                    }
                } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    val mediaType = when {
                        trimmed.endsWith(".m3u8", ignoreCase = true) -> MediaType.HLS_STREAM
                        trimmed.endsWith(".mpd", ignoreCase = true) -> MediaType.DASH_STREAM
                        else -> MediaType.M3U_CHANNEL
                    }
                    result.add(
                        MediaItemEntity(
                            id = UUID.nameUUIDFromBytes(trimmed.toByteArray()).toString(),
                            title = currentTitle.ifEmpty { "Stream ${result.size + 1}" },
                            uri = trimmed,
                            mediaType = mediaType,
                            thumbnailUri = currentLogo,
                            groupTitle = currentGroup
                        )
                    )
                    currentTitle = ""
                    currentGroup = "M3U Playlist"
                    currentLogo = null
                }
            }
            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }
}
