package com.example.fluxplay.data.db

import androidx.room.TypeConverter
import com.example.fluxplay.data.model.MediaType

class Converters {
    @TypeConverter
    fun fromMediaType(value: MediaType): String = value.name

    @TypeConverter
    fun toMediaType(value: String): MediaType {
        return try {
            MediaType.valueOf(value)
        } catch (e: Exception) {
            MediaType.DIRECT_URL
        }
    }
}
