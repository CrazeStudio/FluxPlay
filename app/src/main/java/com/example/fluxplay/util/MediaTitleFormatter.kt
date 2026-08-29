package com.example.fluxplay.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.net.URLDecoder

object MediaTitleFormatter {

    /**
     * Extracts a clean, human-readable file name or title for any video,
     * stream, content URI, or local file. Never returns a raw link.
     */
    fun extractCleanTitle(rawTitle: String?, url: String?, context: Context? = null): String {
        val candidate = rawTitle?.trim()
        if (!candidate.isNullOrBlank() && !isRawUrlOrUri(candidate) && !isGenericPlaceholder(candidate)) {
            return decodeIfUrlEncoded(candidate)
        }

        if (url.isNullOrBlank()) {
            return "Video"
        }

        val trimmedUrl = url.trim()

        // Handle Content URIs (Storage Access Framework, DocumentsProvider, MediaStore)
        if (trimmedUrl.startsWith("content://")) {
            val uri = Uri.parse(trimmedUrl)
            if (context != null) {
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0) {
                                val name = cursor.getString(nameIndex)
                                if (!name.isNullOrBlank() && !isRawUrlOrUri(name)) {
                                    return decodeIfUrlEncoded(name)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            val lastSegment = uri.lastPathSegment
            if (!lastSegment.isNullOrBlank()) {
                val cut = lastSegment.substringAfterLast("/").substringAfterLast(":")
                if (cut.isNotBlank() && !isRawUrlOrUri(cut)) {
                    return decodeIfUrlEncoded(cut)
                }
            }
            return "Offline Video"
        }

        // Handle File URIs or Local Paths
        if (trimmedUrl.startsWith("file://") || trimmedUrl.startsWith("/")) {
            val path = if (trimmedUrl.startsWith("file://")) Uri.parse(trimmedUrl).path ?: trimmedUrl else trimmedUrl
            val fileName = path.substringAfterLast("/")
            if (fileName.isNotBlank()) {
                return decodeIfUrlEncoded(fileName)
            }
            return "Offline Video"
        }

        // Handle HTTP / HTTPS / RTSP / RTMP Network Streams
        try {
            val uri = Uri.parse(trimmedUrl)

            // Check if title or filename is in query parameters (e.g. ?title=MovieName or ?file=video.mp4)
            val queryTitle = uri.getQueryParameter("title")
                ?: uri.getQueryParameter("name")
                ?: uri.getQueryParameter("filename")
                ?: uri.getQueryParameter("file")
                ?: uri.getQueryParameter("video")
            if (!queryTitle.isNullOrBlank() && !isRawUrlOrUri(queryTitle)) {
                return decodeIfUrlEncoded(queryTitle)
            }

            val path = uri.path?.trimEnd('/') ?: ""
            if (path.isNotEmpty()) {
                val segments = path.split('/').filter { it.isNotBlank() }
                if (segments.isNotEmpty()) {
                    val lastSegment = segments.last()
                    val decodedSegment = decodeIfUrlEncoded(lastSegment)

                    // If the last segment is something generic like index.m3u8, master.m3u8, live.m3u8, manifest.mpd
                    if (isGenericStreamManifest(decodedSegment) && segments.size > 1) {
                        val parentSegment = decodeIfUrlEncoded(segments[segments.size - 2])
                        if (parentSegment.isNotBlank() && !isGenericStreamManifest(parentSegment)) {
                            return cleanFormattedTitle(parentSegment)
                        }
                    }
                    if (decodedSegment.isNotBlank()) {
                        return cleanFormattedTitle(decodedSegment)
                    }
                }
            }

            // Fallback to host/domain if available
            val host = uri.host
            if (!host.isNullOrBlank()) {
                val cleanHost = host.removePrefix("www.")
                return "Stream ($cleanHost)"
            }
        } catch (_: Exception) {
            val noQuery = trimmedUrl.substringBefore("?").substringBefore("#").trimEnd('/')
            val afterSlash = noQuery.substringAfterLast('/')
            if (afterSlash.isNotBlank()) {
                return cleanFormattedTitle(decodeIfUrlEncoded(afterSlash))
            }
        }

        return "Video Stream"
    }

    private fun isRawUrlOrUri(text: String): Boolean {
        val lower = text.lowercase().trim()
        return lower.startsWith("http://") ||
                lower.startsWith("https://") ||
                lower.startsWith("content://") ||
                lower.startsWith("file://") ||
                lower.startsWith("rtsp://") ||
                lower.startsWith("rtmp://")
    }

    private fun isGenericPlaceholder(text: String): Boolean {
        val lower = text.lowercase().trim()
        return lower == "offline video" ||
                lower == "offline" ||
                lower == "direct stream" ||
                lower == "custom stream" ||
                lower == "video" ||
                lower == "stream" ||
                lower == "untitled" ||
                lower == "fluxplay stream"
    }

    private fun isGenericStreamManifest(name: String): Boolean {
        val lower = name.lowercase().trim()
        return lower in listOf(
            "index.m3u8", "master.m3u8", "playlist.m3u8", "manifest.mpd",
            "manifest.m3u8", "live.m3u8", "stream.m3u8", "video.m3u8",
            "chunklist.m3u8", "index.mpd", "master.mpd", "stream.mpd",
            "video.mp4", "sample.mp4", "output.mp4"
        )
    }

    private fun cleanFormattedTitle(text: String): String {
        return text.trim()
    }

    fun decodeIfUrlEncoded(text: String): String {
        var current = text
        try {
            var iterations = 0
            while (current.contains("%") && iterations < 3) {
                val decoded = URLDecoder.decode(current, "UTF-8")
                if (decoded == current) break
                current = decoded
                iterations++
            }
        } catch (_: Exception) {}
        return current.trim()
    }
}
