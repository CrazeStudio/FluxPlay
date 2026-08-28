package com.example.fluxplay.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.fluxplay.data.model.MediaItemEntity

@Database(entities = [MediaItemEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class FluxplayDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao

    companion object {
        @Volatile
        private var INSTANCE: FluxplayDatabase? = null

        fun getInstance(context: Context): FluxplayDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FluxplayDatabase::class.java,
                    "fluxplay_database.db"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
