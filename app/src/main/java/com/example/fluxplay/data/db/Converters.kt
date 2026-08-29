package com.example.fluxplay.data.db

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return gson.toJson(value ?: emptyList<String>())
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        val listType = object : TypeToken<List<String>>() {}.type
        return try {
            gson.fromJson(value, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromDownloadStatus(status: com.example.fluxplay.data.model.DownloadStatus?): String {
        return (status ?: com.example.fluxplay.data.model.DownloadStatus.PENDING).name
    }

    @TypeConverter
    fun toDownloadStatus(value: String?): com.example.fluxplay.data.model.DownloadStatus {
        return try {
            if (value != null) com.example.fluxplay.data.model.DownloadStatus.valueOf(value) else com.example.fluxplay.data.model.DownloadStatus.PENDING
        } catch (_: Exception) {
            com.example.fluxplay.data.model.DownloadStatus.PENDING
        }
    }
}
