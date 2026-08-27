package com.example.fluxplay.data.repository

import com.example.fluxplay.data.model.DiscoveryCategory
import com.example.fluxplay.data.model.MediaItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MetadataRepository {

    suspend fun getCategories(): List<DiscoveryCategory> = withContext(Dispatchers.IO) {
        emptyList()
    }

    suspend fun search(query: String, filterProvider: String? = null): List<MediaItemEntity> = withContext(Dispatchers.IO) {
        emptyList()
    }
}
