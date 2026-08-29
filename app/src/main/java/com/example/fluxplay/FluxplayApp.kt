package com.example.fluxplay

import android.app.Application
import com.example.fluxplay.data.db.FluxplayDatabase
import com.example.fluxplay.data.download.DownloadRepository
import com.example.fluxplay.data.repository.MediaRepository
import com.example.fluxplay.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FluxplayApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { FluxplayDatabase.getDatabase(this) }
    val mediaRepository by lazy { MediaRepository(database.mediaDao()) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val downloadRepository by lazy { DownloadRepository(this, database.downloadDao(), database.mediaDao()) }

    override fun onCreate() {
        super.onCreate()
    }
}
