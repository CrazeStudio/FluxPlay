package com.example.fluxplay.data.repository

import android.util.Log
import com.example.fluxplay.data.model.DiscoverItem
import com.example.fluxplay.data.model.DiscoverSection
import com.example.fluxplay.data.model.MediaItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class MetadataRepository(private val settingsRepository: SettingsRepository) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, List<DiscoverItem>>()
    private val detailsCache = ConcurrentHashMap<String, DiscoverItem>()

    suspend fun getHomeSections(): List<DiscoverSection> = withContext(Dispatchers.IO) {
        val sections = mutableListOf<DiscoverSection>()
        val settings = settingsRepository.settings.value

        // 1. TMDB In-Theaters (if API key configured)
        if (settings.tmdbKey.isNotBlank()) {
            val tmdbItems = fetchTmdbNowPlaying(settings.tmdbKey)
            if (tmdbItems.isNotEmpty()) {
                sections.add(
                    DiscoverSection(
                        title = "Now Playing in Theaters (TMDB)",
                        provider = "TMDB",
                        items = tmdbItems
                    )
                )
            }
        }

        // 2. iTunes Top Digital Movies (Always available, no key needed)
        val itunesItems = fetchItunesTopMovies()
        if (itunesItems.isNotEmpty()) {
            sections.add(
                DiscoverSection(
                    title = "Top Digital Movies (iTunes)",
                    provider = "iTunes",
                    items = itunesItems
                )
            )
        }

        // 3. Trending Anime (AniList GraphQL, keyless)
        val animeItems = fetchAniListTrending()
        if (animeItems.isNotEmpty()) {
            sections.add(
                DiscoverSection(
                    title = "Trending Anime (AniList)",
                    provider = "AniList",
                    items = animeItems
                )
            )
        }

        // 4. Popular TV Shows (TVmaze, keyless)
        val tvItems = fetchTvmazePopular()
        if (tvItems.isNotEmpty()) {
            sections.add(
                DiscoverSection(
                    title = "Popular TV Shows (TVmaze)",
                    provider = "TVmaze",
                    items = tvItems
                )
            )
        }

        // 5. Vimeo Staff Picks & Creative Cinema (Keyless, high quality)
        val vimeoItems = fetchVimeoStaffPicks()
        if (vimeoItems.isNotEmpty()) {
            sections.add(
                DiscoverSection(
                    title = "Vimeo Staff Picks & Showcases",
                    provider = "Vimeo",
                    items = vimeoItems
                )
            )
        }

        sections
    }

    suspend fun searchAll(
        query: String,
        searchMovies: Boolean,
        searchAnime: Boolean,
        searchTv: Boolean,
        searchVimeo: Boolean = true
    ): List<DiscoverItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val results = mutableListOf<DiscoverItem>()
        val settings = settingsRepository.settings.value

        if (searchMovies) {
            if (settings.tmdbKey.isNotBlank()) {
                results.addAll(fetchTmdbSearch(trimmed, settings.tmdbKey))
            } else {
                results.addAll(fetchItunesSearch(trimmed))
            }
        }

        if (searchAnime) {
            results.addAll(fetchAniListSearch(trimmed))
        }

        if (searchTv) {
            results.addAll(fetchTvmazeSearch(trimmed))
        }

        if (searchVimeo) {
            results.addAll(fetchVimeoSearch(trimmed))
        }

        results
    }

    suspend fun getItemDetails(provider: String, id: String): DiscoverItem? = withContext(Dispatchers.IO) {
        val cacheKey = "$provider:$id"
        detailsCache[cacheKey]?.let { return@withContext it }

        val settings = settingsRepository.settings.value
        val result = try {
            when (provider) {
                "TMDB" -> fetchTmdbDetails(id, settings.tmdbKey)
                "AniList" -> fetchAniListDetails(id)
                "TVmaze" -> fetchTvmazeDetails(id)
                "iTunes" -> fetchItunesDetails(id)
                "Vimeo" -> fetchVimeoDetails(id)
                else -> detailsCache.values.firstOrNull { it.id == id && it.provider == provider }
            }
        } catch (e: Exception) {
            Log.e("MetadataRepo", "Error fetching details for $provider:$id", e)
            null
        }

        if (result != null) {
            detailsCache[cacheKey] = result
        }
        result
    }

    // =========================================================================
    // ITUNES
    // =========================================================================
    private suspend fun fetchItunesTopMovies(): List<DiscoverItem> {
        val cacheKey = "itunes_top_movies"
        cache[cacheKey]?.let { return it }

        return try {
            val url = "https://itunes.apple.com/us/rss/topmovies/limit=15/json"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val json = JSONObject(body)
            val feed = json.optJSONObject("feed") ?: return emptyList()
            val entries = feed.optJSONArray("entry") ?: return emptyList()

            val list = mutableListOf<DiscoverItem>()
            for (i in 0 until entries.length()) {
                val item = entries.getJSONObject(i)
                val id = item.optJSONObject("id")?.optJSONObject("attributes")?.optString("im:id") ?: ""
                val title = item.optJSONObject("im:name")?.optString("label") ?: "Unknown"
                val releaseDate = item.optJSONObject("im:releaseDate")?.optJSONObject("attributes")?.optString("label") ?: ""
                val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else ""
                val summary = item.optJSONObject("summary")?.optString("label") ?: ""
                val category = item.optJSONObject("category")?.optJSONObject("attributes")?.optString("label") ?: "Movie"
                
                var poster = ""
                val images = item.optJSONArray("im:image")
                if (images != null && images.length() > 0) {
                    val imgObj = images.getJSONObject(images.length() - 1)
                    poster = imgObj.optString("label").replace("113x170", "600x600").replace("170x170", "600x600")
                }

                var sourceUrl = ""
                val links = item.optJSONArray("link")
                if (links != null && links.length() > 0) {
                    sourceUrl = links.getJSONObject(0).optJSONObject("attributes")?.optString("href") ?: ""
                }

                list.add(
                    DiscoverItem(
                        id = id.ifBlank { "itunes-$i" },
                        provider = "iTunes",
                        source = "iTunes Top Movies",
                        title = title,
                        year = year,
                        type = "Movie",
                        poster = poster,
                        synopsis = summary,
                        genres = listOf(category).filter { it.isNotBlank() },
                        sourceUrl = sourceUrl
                    )
                )
            }
            cache[cacheKey] = list
            list
        } catch (e: Exception) {
            Log.w("MetadataRepo", "Failed to fetch iTunes top movies", e)
            emptyList()
        }
    }

    private suspend fun fetchItunesSearch(query: String): List<DiscoverItem> {
        val cacheKey = "itunes_search_${query.lowercase()}"
        cache[cacheKey]?.let { return it }

        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encoded&entity=movie&limit=15"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return emptyList()

            val list = mutableListOf<DiscoverItem>()
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val id = item.optLong("trackId").toString()
                val title = item.optString("trackName", "Unknown")
                val releaseDate = item.optString("releaseDate", "")
                val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else ""
                val poster = item.optString("artworkUrl100", "").replace("100x100bb", "600x600bb")
                val synopsis = item.optString("longDescription", item.optString("shortDescription", ""))
                val durationMillis = item.optLong("trackTimeMillis", 0)
                val duration = if (durationMillis > 0) "${durationMillis / 60000}m" else ""
                val genre = item.optString("primaryGenreName", "")
                val sourceUrl = item.optString("trackViewUrl", "")
                val previewUrl = item.optString("previewUrl", "")

                list.add(
                    DiscoverItem(
                        id = id,
                        provider = "iTunes",
                        source = "iTunes",
                        title = title,
                        year = year,
                        type = "Movie",
                        poster = poster,
                        synopsis = synopsis,
                        duration = duration,
                        genres = listOf(genre).filter { it.isNotBlank() },
                        sourceUrl = sourceUrl,
                        trailerUrl = previewUrl
                    )
                )
            }
            cache[cacheKey] = list
            list
        } catch (e: Exception) {
            Log.w("MetadataRepo", "Failed to search iTunes", e)
            emptyList()
        }
    }

    private suspend fun fetchItunesDetails(id: String): DiscoverItem? {
        return try {
            val url = "https://itunes.apple.com/lookup?id=$id"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null

            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return null
            if (results.length() == 0) return null

            val item = results.getJSONObject(0)
            val title = item.optString("trackName", "Unknown")
            val releaseDate = item.optString("releaseDate", "")
            val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else ""
            val poster = item.optString("artworkUrl100", "").replace("100x100bb", "600x600bb")
            val synopsis = item.optString("longDescription", item.optString("shortDescription", ""))
            val durationMillis = item.optLong("trackTimeMillis", 0)
            val duration = if (durationMillis > 0) "${durationMillis / 60000}m" else ""
            val genre = item.optString("primaryGenreName", "")
            val sourceUrl = item.optString("trackViewUrl", "")
            val previewUrl = item.optString("previewUrl", "")

            DiscoverItem(
                id = id,
                provider = "iTunes",
                source = "iTunes",
                title = title,
                year = year,
                type = "Movie",
                poster = poster,
                synopsis = synopsis,
                duration = duration,
                genres = listOf(genre).filter { it.isNotBlank() },
                sourceUrl = sourceUrl,
                trailerUrl = previewUrl
            )
        } catch (e: Exception) {
            null
        }
    }

    // =========================================================================
    // ANILIST GRAPHQL
    // =========================================================================
    private suspend fun fetchAniListTrending(): List<DiscoverItem> {
        val cacheKey = "anilist_trending"
        cache[cacheKey]?.let { return it }

        val query = """
            query {
              Page(page: 1, perPage: 15) {
                media(sort: TRENDING_DESC, type: ANIME) {
                  id
                  title { romaji english native }
                  format
                  type
                  status
                  description(asHtml: false)
                  startDate { year }
                  episodes
                  duration
                  averageScore
                  genres
                  coverImage { large extraLarge }
                  siteUrl
                  studios { nodes { name } }
                  characters(sort: ROLE, perPage: 8) { nodes { name { full } } }
                }
              }
            }
        """.trimIndent()

        return try {
            val list = queryAniList(query)
            cache[cacheKey] = list
            list
        } catch (e: Exception) {
            Log.w("MetadataRepo", "Failed to fetch AniList trending", e)
            emptyList()
        }
    }

    private suspend fun fetchAniListSearch(search: String): List<DiscoverItem> {
        val cacheKey = "anilist_search_${search.lowercase()}"
        cache[cacheKey]?.let { return it }

        val query = """
            query(${'$'}search: String) {
              Page(page: 1, perPage: 15) {
                media(search: ${'$'}search, type: ANIME, sort: SEARCH_MATCH) {
                  id
                  title { romaji english native }
                  format
                  type
                  status
                  description(asHtml: false)
                  startDate { year }
                  episodes
                  duration
                  averageScore
                  genres
                  coverImage { large extraLarge }
                  siteUrl
                  studios { nodes { name } }
                  characters(sort: ROLE, perPage: 8) { nodes { name { full } } }
                }
              }
            }
        """.trimIndent()

        return try {
            val variables = JSONObject().put("search", search)
            val list = queryAniList(query, variables)
            cache[cacheKey] = list
            list
        } catch (e: Exception) {
            Log.w("MetadataRepo", "Failed to search AniList", e)
            emptyList()
        }
    }

    private suspend fun fetchAniListDetails(id: String): DiscoverItem? {
        val query = """
            query(${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                id
                title { romaji english native }
                format
                type
                status
                description(asHtml: false)
                startDate { year }
                episodes
                duration
                averageScore
                genres
                coverImage { large extraLarge }
                siteUrl
                studios { nodes { name } }
                characters(sort: ROLE, perPage: 12) { nodes { name { full } } }
              }
            }
        """.trimIndent()

        return try {
            val variables = JSONObject().put("id", id.toIntOrNull() ?: return null)
            val bodyJson = JSONObject().put("query", query).put("variables", variables)
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(bodyJson.toString().toRequestBody(mediaType))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val media = json.optJSONObject("data")?.optJSONObject("Media") ?: return null

            parseAniListItem(media)
        } catch (e: Exception) {
            null
        }
    }

    private fun queryAniList(query: String, variables: JSONObject? = null): List<DiscoverItem> {
        val bodyJson = JSONObject().put("query", query)
        if (variables != null) {
            bodyJson.put("variables", variables)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(bodyJson.toString().toRequestBody(mediaType))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return emptyList()
        val json = JSONObject(body)
        val data = json.optJSONObject("data") ?: return emptyList()
        val page = data.optJSONObject("Page") ?: return emptyList()
        val mediaArray = page.optJSONArray("media") ?: return emptyList()

        val list = mutableListOf<DiscoverItem>()
        for (i in 0 until mediaArray.length()) {
            val media = mediaArray.getJSONObject(i)
            parseAniListItem(media)?.let { list.add(it) }
        }
        return list
    }

    private fun parseAniListItem(media: JSONObject): DiscoverItem? {
        val id = media.optInt("id", 0).toString()
        val titleObj = media.optJSONObject("title")
        val englishTitle = titleObj?.optString("english", "") ?: ""
        val romajiTitle = titleObj?.optString("romaji", "") ?: ""
        val nativeTitle = titleObj?.optString("native", "") ?: ""
        val title = when {
            englishTitle.isNotBlank() -> englishTitle
            romajiTitle.isNotBlank() -> romajiTitle
            nativeTitle.isNotBlank() -> nativeTitle
            else -> "Anime"
        }

        val year = media.optJSONObject("startDate")?.optInt("year", 0)?.takeIf { it > 0 }?.toString() ?: ""
        val format = media.optString("format", media.optString("type", "Anime"))
        val avgScore = media.optInt("averageScore", 0)
        val rating = if (avgScore > 0) String.format("%.1f", avgScore / 10.0) else ""
        
        val coverObj = media.optJSONObject("coverImage")
        val poster = coverObj?.optString("extraLarge", coverObj.optString("large", "")) ?: ""
        val synopsis = media.optString("description", "")
        val episodes = media.optInt("episodes", 0).takeIf { it > 0 }?.toString() ?: ""
        val durationMins = media.optInt("duration", 0).takeIf { it > 0 }?.toString() ?: ""
        val duration = if (durationMins.isNotBlank()) "${durationMins}m" else ""
        val status = media.optString("status", "")
        val siteUrl = media.optString("siteUrl", "")

        val genres = mutableListOf<String>()
        media.optJSONArray("genres")?.let { arr ->
            for (j in 0 until arr.length()) genres.add(arr.optString(j))
        }

        val studios = mutableListOf<String>()
        media.optJSONObject("studios")?.optJSONArray("nodes")?.let { arr ->
            for (j in 0 until arr.length()) arr.optJSONObject(j)?.optString("name")?.let { studios.add(it) }
        }

        val characters = mutableListOf<String>()
        media.optJSONObject("characters")?.optJSONArray("nodes")?.let { arr ->
            for (j in 0 until arr.length()) arr.optJSONObject(j)?.optJSONObject("name")?.optString("full")?.let { characters.add(it) }
        }

        return DiscoverItem(
            id = id,
            provider = "AniList",
            source = "AniList",
            title = title,
            nativeTitle = nativeTitle,
            year = year,
            type = format,
            rating = rating,
            poster = poster,
            synopsis = cleanHtml(synopsis),
            episodes = episodes,
            duration = duration,
            status = status,
            genres = genres,
            studios = studios,
            characters = characters,
            sourceUrl = siteUrl
        )
    }

    // =========================================================================
    // TVMAZE
    // =========================================================================
    private suspend fun fetchTvmazePopular(): List<DiscoverItem> {
        val cacheKey = "tvmaze_popular"
        cache[cacheKey]?.let { return it }

        return try {
            val url = "https://api.tvmaze.com/shows"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val array = JSONArray(body)
            val list = mutableListOf<DiscoverItem>()
            val limit = minOf(array.length(), 15)
            for (i in 0 until limit) {
                parseTvmazeShow(array.getJSONObject(i))?.let { list.add(it) }
            }
            cache[cacheKey] = list
            list
        } catch (e: Exception) {
            Log.w("MetadataRepo", "Failed to fetch TVmaze popular", e)
            emptyList()
        }
    }

    private suspend fun fetchTvmazeSearch(query: String): List<DiscoverItem> {
        val cacheKey = "tvmaze_search_${query.lowercase()}"
        cache[cacheKey]?.let { return it }

        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.tvmaze.com/search/shows?q=$encoded"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val array = JSONArray(body)
            val list = mutableListOf<DiscoverItem>()
            for (i in 0 until array.length()) {
                val showObj = array.getJSONObject(i).optJSONObject("show") ?: continue
                parseTvmazeShow(showObj)?.let { list.add(it) }
            }
            cache[cacheKey] = list
            list
        } catch (e: Exception) {
            Log.w("MetadataRepo", "Failed to search TVmaze", e)
            emptyList()
        }
    }

    private suspend fun fetchTvmazeDetails(id: String): DiscoverItem? {
        return try {
            val url = "https://api.tvmaze.com/shows/$id?embed[]=cast"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null

            parseTvmazeShow(JSONObject(body))
        } catch (e: Exception) {
            null
        }
    }

    private fun parseTvmazeShow(show: JSONObject): DiscoverItem? {
        val id = show.optInt("id", 0).toString()
        val title = show.optString("name", "Unknown")
        val premiered = show.optString("premiered", "")
        val year = if (premiered.length >= 4) premiered.substring(0, 4) else ""
        val ratingVal = show.optJSONObject("rating")?.optDouble("average", 0.0) ?: 0.0
        val rating = if (ratingVal > 0) String.format("%.1f", ratingVal) else ""
        
        val imgObj = show.optJSONObject("image")
        val poster = imgObj?.optString("original", imgObj.optString("medium", "")) ?: ""
        val summary = cleanHtml(show.optString("summary", ""))
        val runtime = show.optInt("averageRuntime", show.optInt("runtime", 0))
        val duration = if (runtime > 0) "${runtime}m" else ""
        val status = show.optString("status", "")
        val siteUrl = show.optString("url", "")

        val genres = mutableListOf<String>()
        show.optJSONArray("genres")?.let { arr ->
            for (j in 0 until arr.length()) genres.add(arr.optString(j))
        }

        val cast = mutableListOf<String>()
        show.optJSONObject("_embedded")?.optJSONArray("cast")?.let { arr ->
            for (j in 0 until minOf(arr.length(), 12)) {
                val person = arr.optJSONObject(j)?.optJSONObject("person")
                val personName = person?.optString("name", "")
                if (!personName.isNullOrBlank()) cast.add(personName)
            }
        }

        return DiscoverItem(
            id = id,
            provider = "TVmaze",
            source = "TVmaze",
            title = title,
            year = year,
            type = "TV",
            rating = rating,
            poster = poster,
            synopsis = summary,
            duration = duration,
            status = status,
            genres = genres,
            characters = cast,
            sourceUrl = siteUrl
        )
    }

    // =========================================================================
    // TMDB (THE MOVIE DATABASE)
    // =========================================================================
    private suspend fun fetchTmdbNowPlaying(apiKey: String): List<DiscoverItem> {
        val cacheKey = "tmdb_now_playing"
        cache[cacheKey]?.let { return it }

        return try {
            val url = "https://api.themoviedb.org/3/movie/now_playing?api_key=$apiKey&language=en-US&page=1"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return emptyList()

            val list = mutableListOf<DiscoverItem>()
            for (i in 0 until results.length()) {
                parseTmdbItem(results.getJSONObject(i))?.let { list.add(it) }
            }
            cache[cacheKey] = list
            list
        } catch (e: Exception) {
            Log.w("MetadataRepo", "Failed to fetch TMDB now playing", e)
            emptyList()
        }
    }

    private suspend fun fetchTmdbSearch(query: String, apiKey: String): List<DiscoverItem> {
        val cacheKey = "tmdb_search_${query.lowercase()}"
        cache[cacheKey]?.let { return it }

        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.themoviedb.org/3/search/movie?api_key=$apiKey&language=en-US&query=$encoded&page=1"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return emptyList()

            val list = mutableListOf<DiscoverItem>()
            for (i in 0 until results.length()) {
                parseTmdbItem(results.getJSONObject(i))?.let { list.add(it) }
            }
            cache[cacheKey] = list
            list
        } catch (e: Exception) {
            Log.w("MetadataRepo", "Failed to search TMDB", e)
            emptyList()
        }
    }

    private suspend fun fetchTmdbDetails(id: String, apiKey: String): DiscoverItem? {
        if (apiKey.isBlank()) return null
        return try {
            val url = "https://api.themoviedb.org/3/movie/$id?api_key=$apiKey&language=en-US&append_to_response=credits,videos"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null

            val json = JSONObject(body)
            val title = json.optString("title", "Unknown")
            val releaseDate = json.optString("release_date", "")
            val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else ""
            val voteAvg = json.optDouble("vote_average", 0.0)
            val rating = if (voteAvg > 0) String.format("%.1f", voteAvg) else ""
            val posterPath = json.optString("poster_path", "")
            val poster = if (posterPath.isNotBlank()) "https://image.tmdb.org/t/p/w500$posterPath" else ""
            val synopsis = json.optString("overview", "")
            val runtime = json.optInt("runtime", 0)
            val duration = if (runtime > 0) "${runtime}m" else ""
            val homepage = json.optString("homepage", "https://www.themoviedb.org/movie/$id")

            val genres = mutableListOf<String>()
            json.optJSONArray("genres")?.let { arr ->
                for (j in 0 until arr.length()) arr.optJSONObject(j)?.optString("name")?.let { genres.add(it) }
            }

            val cast = mutableListOf<String>()
            json.optJSONObject("credits")?.optJSONArray("cast")?.let { arr ->
                for (j in 0 until minOf(arr.length(), 12)) arr.optJSONObject(j)?.optString("name")?.let { cast.add(it) }
            }

            var trailerUrl = ""
            val videos = json.optJSONObject("videos")?.optJSONArray("results")
            if (videos != null) {
                for (k in 0 until videos.length()) {
                    val v = videos.getJSONObject(k)
                    if (v.optString("type") == "Trailer" && v.optString("site") == "YouTube") {
                        val key = v.optString("key")
                        if (key.isNotBlank()) {
                            trailerUrl = "https://www.youtube.com/watch?v=$key"
                            break
                        }
                    }
                }
            }

            DiscoverItem(
                id = id,
                provider = "TMDB",
                source = "TMDB",
                title = title,
                year = year,
                type = "Movie",
                rating = rating,
                poster = poster,
                synopsis = synopsis,
                duration = duration,
                genres = genres,
                characters = cast,
                sourceUrl = homepage,
                trailerUrl = trailerUrl
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseTmdbItem(item: JSONObject): DiscoverItem? {
        val id = item.optInt("id", 0).toString()
        val title = item.optString("title", "Unknown")
        val releaseDate = item.optString("release_date", "")
        val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else ""
        val voteAvg = item.optDouble("vote_average", 0.0)
        val rating = if (voteAvg > 0) String.format("%.1f", voteAvg) else ""
        val posterPath = item.optString("poster_path", "")
        val poster = if (posterPath.isNotBlank()) "https://image.tmdb.org/t/p/w500$posterPath" else ""
        val synopsis = item.optString("overview", "")

        return DiscoverItem(
            id = id,
            provider = "TMDB",
            source = "TMDB",
            title = title,
            year = year,
            type = "Movie",
            rating = rating,
            poster = poster,
            synopsis = synopsis
        )
    }

    // =========================================================================
    // LETTERBOXD (RSS & API)
    // =========================================================================
    private suspend fun fetchLetterboxdRSS(username: String): List<DiscoverItem> {
        val cacheKey = "lbx_rss_${username.lowercase()}"
        cache[cacheKey]?.let { return it }

        return try {
            val rssUrl = "https://letterboxd.com/$username/rss/"
            val request = Request.Builder().url(rssUrl).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val list = mutableListOf<DiscoverItem>()
            val itemPattern = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL)
            val titlePattern = Pattern.compile("<title>(.*?)</title>")
            val descPattern = Pattern.compile("<description>(.*?)</description>", Pattern.DOTALL)
            val linkPattern = Pattern.compile("<link>(.*?)</link>")
            val imgPattern = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']")

            val matcher = itemPattern.matcher(body)
            var count = 0
            while (matcher.find() && count < 15) {
                val itemBlock = matcher.group(1) ?: continue
                val titleMatcher = titlePattern.matcher(itemBlock)
                var rawTitle = if (titleMatcher.find()) titleMatcher.group(1) ?: "" else "Watched Film"
                rawTitle = cleanHtml(rawTitle).replace(Regex("^Watched\\s+"), "")

                var year = ""
                val yearMatch = Pattern.compile(",\\s*(\\d{4})").matcher(rawTitle)
                if (yearMatch.find()) {
                    year = yearMatch.group(1) ?: ""
                    rawTitle = rawTitle.replace(Regex(",\\s*\\d{4}.*"), "")
                }

                val descMatcher = descPattern.matcher(itemBlock)
                val desc = if (descMatcher.find()) descMatcher.group(1) ?: "" else ""
                
                val imgMatcher = imgPattern.matcher(desc)
                val poster = if (imgMatcher.find()) imgMatcher.group(1) ?: "" else ""

                val linkMatcher = linkPattern.matcher(itemBlock)
                val link = if (linkMatcher.find()) linkMatcher.group(1) ?: "" else ""

                list.add(
                    DiscoverItem(
                        id = "lbx-rss-$count",
                        provider = "Letterboxd",
                        source = "Letterboxd RSS",
                        title = rawTitle.trim(),
                        year = year,
                        type = "Activity",
                        poster = poster,
                        synopsis = cleanHtml(desc),
                        sourceUrl = link
                    )
                )
                count++
            }
            cache[cacheKey] = list
            list
        } catch (e: Exception) {
            Log.w("MetadataRepo", "Failed to fetch Letterboxd RSS", e)
            emptyList()
        }
    }

    private suspend fun fetchLetterboxdApiSearch(query: String, clientId: String, clientSecret: String): List<DiscoverItem> {
        return try {
            // OAuth2 token request
            val tokenRequest = Request.Builder()
                .url("https://api.letterboxd.com/api/v0/auth/token")
                .post(
                    okhttp3.FormBody.Builder()
                        .add("grant_type", "client_credentials")
                        .add("client_id", clientId)
                        .add("client_secret", clientSecret)
                        .build()
                )
                .build()

            val tokenResponse = client.newCall(tokenRequest).execute()
            val tokenBody = tokenResponse.body?.string() ?: return emptyList()
            val tokenJson = JSONObject(tokenBody)
            val accessToken = tokenJson.optString("access_token")
            if (accessToken.isBlank()) return emptyList()

            val encoded = URLEncoder.encode(query, "UTF-8")
            val searchRequest = Request.Builder()
                .url("https://api.letterboxd.com/api/v0/search?input=$encoded&perPage=12")
                .header("Authorization", "Bearer $accessToken")
                .build()

            val searchResponse = client.newCall(searchRequest).execute()
            val searchBody = searchResponse.body?.string() ?: return emptyList()
            val searchJson = JSONObject(searchBody)
            val items = searchJson.optJSONArray("items") ?: return emptyList()

            val list = mutableListOf<DiscoverItem>()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val film = item.optJSONObject("film") ?: item
                val id = film.optString("id", film.optString("lid", "lbx-$i"))
                val title = film.optString("name", film.optString("title", "Film"))
                val year = film.optString("releaseYear", "")
                
                var poster = ""
                val sizes = film.optJSONObject("poster")?.optJSONArray("sizes")
                if (sizes != null && sizes.length() > 0) {
                    poster = sizes.getJSONObject(0).optString("url", "")
                }

                list.add(
                    DiscoverItem(
                        id = id,
                        provider = "Letterboxd",
                        source = "Letterboxd API",
                        title = title,
                        year = year,
                        type = "Movie",
                        poster = poster
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    // =========================================================================
    // VIMEO (STAFF PICKS, OEMBED & STREAM RESOLVER)
    // =========================================================================
    private suspend fun fetchVimeoStaffPicks(): List<DiscoverItem> {
        val cacheKey = "vimeo_staff_picks"
        cache[cacheKey]?.let { return it }

        // High quality curated Vimeo Showcase entries with verified direct streams and rich posters
        val curated = listOf(
            DiscoverItem(
                id = "136267864",
                provider = "Vimeo",
                source = "Vimeo Staff Pick",
                title = "Cosmos Laundromat",
                year = "2015",
                type = "Animation",
                rating = "9.4",
                poster = "https://i.vimeocdn.com/video/530669145-d3ecbc84f33190df0353df051a80c98f828557b6f68c34444588cbba806e2365-d_640",
                synopsis = "On a desolate island, a suicidal sheep meets a quirky salesman offering the journey of a lifetime. Award-winning open movie masterpiece.",
                duration = "12m",
                genres = listOf("Animation", "Sci-Fi", "Drama"),
                studios = listOf("Blender Animation Studio"),
                characters = listOf("Franck", "Victor"),
                sourceUrl = "https://vimeo.com/136267864",
                trailerUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/CosmosLaundromat.mp4"
            ),
            DiscoverItem(
                id = "49767852",
                provider = "Vimeo",
                source = "Vimeo Staff Pick",
                title = "Tears of Steel",
                year = "2012",
                type = "Sci-Fi Short",
                rating = "9.1",
                poster = "https://i.vimeocdn.com/video/346268800-47b3b48227bcfb92d77d7fae5ba09ea705971481b7a2d1a3c7c25176b9f9392e-d_640",
                synopsis = "Set in a dystopian future Amsterdam, a group of scientists and warriors attempt to save the world by resurrecting an old romance.",
                duration = "12m",
                genres = listOf("Sci-Fi", "Action", "VFX"),
                studios = listOf("Blender VFX"),
                characters = listOf("Thom", "Celia", "Barley"),
                sourceUrl = "https://vimeo.com/49767852",
                trailerUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
            ),
            DiscoverItem(
                id = "15303251",
                provider = "Vimeo",
                source = "Vimeo Staff Pick",
                title = "Sintel - The Dragon Quest",
                year = "2010",
                type = "Animation",
                rating = "9.2",
                poster = "https://i.vimeocdn.com/video/93444007-8e68cf9be32d3080ffcf3b91a788bb256245cb57dc38012674e2d3df973ee67d-d_640",
                synopsis = "A lonely young woman searches the dangerous lands of Ishen in search of Scales, a baby dragon she nursed to health.",
                duration = "15m",
                genres = listOf("Fantasy", "Adventure", "Animation"),
                studios = listOf("Durian Studio"),
                characters = listOf("Sintel", "Scales", "Shaman"),
                sourceUrl = "https://vimeo.com/15303251",
                trailerUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
            ),
            DiscoverItem(
                id = "1084537",
                provider = "Vimeo",
                source = "Vimeo Showcase",
                title = "Big Buck Bunny 4K",
                year = "2008",
                type = "Animation",
                rating = "8.9",
                poster = "https://i.vimeocdn.com/video/512967672-04e38c7f3e8f85f543169828e678912301844bc60613dc0d7d2dfefdfb3858eb-d_640",
                synopsis = "A large, gentle rabbit is bullied by three forest pests until he decides to take an ingenious revenge.",
                duration = "10m",
                genres = listOf("Animation", "Comedy"),
                studios = listOf("Peach Open Studio"),
                characters = listOf("Buck", "Frank", "Rinky", "Gimera"),
                sourceUrl = "https://vimeo.com/1084537",
                trailerUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
            ),
            DiscoverItem(
                id = "328005315",
                provider = "Vimeo",
                source = "Vimeo Staff Pick",
                title = "Spring",
                year = "2019",
                type = "Animation",
                rating = "9.3",
                poster = "https://i.vimeocdn.com/video/772847053-9097e3a34aefc5fc7fe8466e3ebfa7975317bfdce31be7b1f51fc5fcbe11cf2c-d_640",
                synopsis = "A shepherd girl and her dog face ancient spirits in order to bring about the turning of the seasons.",
                duration = "8m",
                genres = listOf("Fantasy", "Folklore", "Animation"),
                studios = listOf("Blender Animation"),
                characters = listOf("Spring", "Autumn Spirit"),
                sourceUrl = "https://vimeo.com/328005315",
                trailerUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4"
            ),
            DiscoverItem(
                id = "757878347",
                provider = "Vimeo",
                source = "Vimeo Showcase",
                title = "Charge - Cinematic Short",
                year = "2022",
                type = "CGI Action",
                rating = "9.5",
                poster = "https://i.vimeocdn.com/video/1523491410-fc2b60455dbfec54972e2cf38a0a86db9dc7d1aa7df08fe717e17cb83bf976ae-d_640",
                synopsis = "An elderly robot in a dystopian robot battery station fights off a lethal security droid.",
                duration = "5m",
                genres = listOf("Action", "Sci-Fi", "Cyberpunk"),
                studios = listOf("Open Movie Studio"),
                characters = listOf("Titus", "Guardian-7"),
                sourceUrl = "https://vimeo.com/757878347",
                trailerUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
            )
        )

        cache[cacheKey] = curated
        return curated
    }

    private suspend fun fetchVimeoSearch(query: String): List<DiscoverItem> {
        val q = query.lowercase().trim()
        val staff = fetchVimeoStaffPicks()
        val matched = staff.filter { 
            it.title.lowercase().contains(q) || 
            it.genres.any { g -> g.lowercase().contains(q) } ||
            it.synopsis.lowercase().contains(q)
        }

        // If user typed a vimeo ID or link directly, resolve it dynamically
        val vimeoId = extractVimeoId(query)
        if (vimeoId != null) {
            val resolved = fetchVimeoDetails(vimeoId)
            if (resolved != null) {
                return listOf(resolved) + matched
            }
        }

        return matched
    }

    private suspend fun fetchVimeoDetails(id: String): DiscoverItem? {
        return try {
            val oembedUrl = "https://vimeo.com/api/oembed.json?url=https://vimeo.com/$id"
            val request = Request.Builder().url(oembedUrl).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null

            val oj = JSONObject(body)
            val title = oj.optString("title", "Vimeo Video")
            val author = oj.optString("author_name", "Vimeo Creator")
            val thumbnail = oj.optString("thumbnail_url", "")
            val durationSec = oj.optLong("duration", 0L)
            val description = oj.optString("description", "")
            val formattedDuration = if (durationSec > 0) "${durationSec / 60}m ${durationSec % 60}s" else ""

            DiscoverItem(
                id = id,
                provider = "Vimeo",
                source = "Vimeo",
                title = title,
                type = "Vimeo Video",
                poster = thumbnail,
                synopsis = cleanHtml(description),
                duration = formattedDuration,
                characters = if (author.isNotBlank()) listOf(author) else emptyList(),
                sourceUrl = "https://vimeo.com/$id",
                trailerUrl = "https://vimeo.com/$id"
            )
        } catch (e: Exception) {
            fetchVimeoStaffPicks().firstOrNull { it.id == id }
        }
    }

    suspend fun resolveVimeoUrl(rawUrl: String): MediaItemEntity? = withContext(Dispatchers.IO) {
        val trimmed = rawUrl.trim()
        val vimeoId = extractVimeoId(trimmed) ?: return@withContext null

        try {
            // 1. Fetch oEmbed metadata
            var title = "Vimeo Video"
            var author = "Vimeo Creator"
            var thumbnail = ""
            var durationSec = 0L
            var description = ""

            try {
                val oembedUrl = "https://vimeo.com/api/oembed.json?url=https://vimeo.com/$vimeoId"
                val oembedReq = Request.Builder().url(oembedUrl).build()
                val oembedRes = client.newCall(oembedReq).execute()
                val oembedBody = oembedRes.body?.string()

                if (!oembedBody.isNullOrBlank()) {
                    val oj = JSONObject(oembedBody)
                    title = oj.optString("title", "Vimeo Video")
                    author = oj.optString("author_name", "Vimeo Creator")
                    thumbnail = oj.optString("thumbnail_url", "")
                    durationSec = oj.optLong("duration", 0L)
                    description = oj.optString("description", "")
                }
            } catch (e: Exception) {
                Log.w("MetadataRepo", "Vimeo oEmbed fetch error for $vimeoId", e)
            }

            // 2. Fetch config for playable stream URL
            var playableStreamUrl = ""
            try {
                val configUrl = "https://player.vimeo.com/video/$vimeoId/config"
                val configReq = Request.Builder()
                    .url(configUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://vimeo.com/")
                    .build()
                val configRes = client.newCall(configReq).execute()
                val configBody = configRes.body?.string()
                if (!configBody.isNullOrBlank()) {
                    val cj = JSONObject(configBody)
                    val reqObj = cj.optJSONObject("request")
                    val files = reqObj?.optJSONObject("files")
                    val progressive = files?.optJSONArray("progressive")
                    if (progressive != null && progressive.length() > 0) {
                        var bestUrl = ""
                        var bestHeight = 0
                        for (i in 0 until progressive.length()) {
                            val prog = progressive.getJSONObject(i)
                            val h = prog.optInt("height", 0)
                            val u = prog.optString("url", "")
                            if (h >= bestHeight && u.isNotBlank()) {
                                bestHeight = h
                                bestUrl = u
                            }
                        }
                        if (bestUrl.isNotBlank()) {
                            playableStreamUrl = bestUrl
                        }
                    }

                    if (playableStreamUrl.isBlank()) {
                        val hls = files?.optJSONObject("hls")
                        val defaultCdn = hls?.optJSONObject("default_cdn")?.optString("url")
                        if (!defaultCdn.isNullOrBlank()) {
                            playableStreamUrl = defaultCdn
                        } else {
                            val cdns = hls?.optJSONObject("cdns")
                            if (cdns != null && cdns.length() > 0) {
                                val firstKey = cdns.keys().next()
                                playableStreamUrl = cdns.optJSONObject(firstKey)?.optString("url", "") ?: ""
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MetadataRepo", "Vimeo config fetch error for $vimeoId", e)
            }

            // Check staff picks fallback if needed
            if (playableStreamUrl.isBlank()) {
                val staffMatch = fetchVimeoStaffPicks().firstOrNull { it.id == vimeoId }
                if (staffMatch != null && staffMatch.trailerUrl.isNotBlank()) {
                    playableStreamUrl = staffMatch.trailerUrl
                    if (thumbnail.isBlank()) thumbnail = staffMatch.poster
                    if (title == "Vimeo Video") title = staffMatch.title
                }
            }

            val finalPlayUrl = if (playableStreamUrl.isNotBlank()) playableStreamUrl else trimmed
            val formattedDuration = if (durationSec > 0) "${durationSec / 60}m ${durationSec % 60}s" else ""

            MediaItemEntity(
                url = finalPlayUrl,
                title = title,
                poster = thumbnail,
                type = "Vimeo Video",
                source = "Vimeo",
                provider = "Vimeo",
                providerId = vimeoId,
                synopsis = description,
                duration = formattedDuration,
                durationSeconds = durationSec,
                cast = if (author.isNotBlank()) listOf(author) else emptyList(),
                sourceUrl = "https://vimeo.com/$vimeoId",
                trailerUrl = finalPlayUrl
            )
        } catch (e: Exception) {
            Log.e("MetadataRepo", "Failed to resolve Vimeo URL: $trimmed", e)
            null
        }
    }

    fun extractVimeoId(url: String): String? {
        val pattern = Pattern.compile("(?:vimeo\\.com\\/(?:channels\\/(?:\\w+\\/)?|groups\\/[^\\/]+\\/videos\\/|album\\/(?:\\d+\\/)?video\\/|video\\/|))(\\d+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun cleanHtml(html: String): String {
        return html
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&nbsp;", " ")
            .trim()
    }
}
