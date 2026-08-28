package com.example.fluxplay

import android.app.Application
import com.example.fluxplay.data.db.FluxplayDatabase
import com.example.fluxplay.data.repository.MediaRepository
import com.example.fluxplay.data.repository.MetadataRepository
import com.example.fluxplay.data.repository.SettingsRepository

class FluxplayApp : Application() {

    lateinit var database: FluxplayDatabase
        private set

    lateinit var mediaRepository: MediaRepository
        private set

    lateinit var metadataRepository: MetadataRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = FluxplayDatabase.getInstance(this)
        mediaRepository = MediaRepository(database.mediaDao())
        metadataRepository = MetadataRepository()
        settingsRepository = SettingsRepository(this)
    }

    companion object {
        lateinit var instance: FluxplayApp
            private set
    }
}
