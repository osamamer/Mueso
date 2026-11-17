package com.akshay.musicplayer.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class SpotifyTrackInfo(
    val title: String,
    val artist: String,
    val durationMs: Long = 0L,
    val spotifyId: String? = null
)

/** A playlist in the signed-in user's Spotify library, with its own cover image. */
data class SpotifyAccountPlaylist(
    val id: String,
    val name: String,
    val coverUrl: String?
)

data class SpotifyPlaylistData(
    val name: String,
    val description: String?,
    val artworkUrl: String?,
    val tracks: List<SpotifyTrackInfo>
)

class SpotifyImportRepository {

    /**
     * In-memory cookie jar so OkHttp stores/sends session cookies automatically.
     */
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()
    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = url.host
            cookieStore.getOrPut(host) { mutableListOf() }.apply {
                // Replace existing cookies with same name
                cookies.forEach { newCookie ->
                    removeAll { it.name == newCookie.name }
                    add(newCookie)
                }
            }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    companion object {
        private const val TAG = "MUESO_SPOTIFY"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        private const val PLAYLIST_QUERY_HASH = "21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf"
        private const val PREVIOUS_PLAYLIST_QUERY_HASH = "8964e8eafb21aa992a7d951d256d83285c04be2105d209262901de70cb97584a"
        private val SPOTIFY_IMAGE_REGEX = Regex(
            """https://(?:i\.scdn\.co|mosaic\.scdn\.co|image-cdn-[a-z0-9-]+\.spotifycdn\.com)/image/[A-Za-z0-9]+"""
        )

        /**
         * True for a playlist's own cover on Spotify's CDN: an uploaded cover
         * (image IDs starting "ab67706c"/"ab67706f") or Spotify's generated
         * mosaic. Album art ("ab67616d…", what song thumbnails use) is not a
         * playlist cover, even though it comes from the same CDN.
         */
        fun isSpotifyCoverUrl(url: String?): Boolean =
            url != null && (url.contains("mosaic.scdn.co/") ||
                url.contains("/image/ab67706c") || url.contains("/image/ab67706f"))

        /** Accepts a playlist URL, a spotify:playlist: URI, or a bare 22-character ID. */
        fun normalizePlaylistId(value: String?): String? {
            val trimmed = value?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            Regex("""playlist[/:]([A-Za-z0-9]{22})""").find(trimmed)?.let { return it.groupValues[1] }
            return trimmed.takeIf { Regex("""[A-Za-z0-9]{22}""").matches(it) }
        }
    }

    fun extractPlaylistId(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.startsWith("spotify:playlist:")) {
            return trimmed.removePrefix("spotify:playlist:").takeIf { it.isNotBlank() }
        }
        val regex = Regex("""open\.spotify\.com/playlist/([a-zA-Z0-9]+)""")
        return regex.find(trimmed)?.groupValues?.getOrNull(1)
    }

    /**
     * Step 1: Visit the Spotify homepage to establish session cookies.
     * Step 2: Use those cookies to fetch an anonymous access token.
     */
    private suspend fun fetchAnonymousToken(): String? = withContext(Dispatchers.IO) {
        try {
            // Step 1: Establish session by visiting the Spotify home page
            Log.d(TAG, "[TOKEN] Step 1: Visiting open.spotify.com to establish session cookies...")
            val homeRequest = Request.Builder()
                .url("https://open.spotify.com/")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .get()
                .build()

            val homeCode = httpClient.newCall(homeRequest).execute().use { resp -> resp.code }
            Log.d(TAG, "[TOKEN] Step 1 done: HTTP $homeCode, cookies: ${cookieStore.values.flatten().map { it.name }}")

            // Step 2: Now request the anonymous token with established cookies
            Log.d(TAG, "[TOKEN] Step 2: Fetching anonymous access token...")
            val tokenRequest = Request.Builder()
                .url("https://open.spotify.com/get_access_token?reason=transport&productType=web_player")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("Referer", "https://open.spotify.com/")
                .get()
                .build()

            val tokenBody = httpClient.newCall(tokenRequest).execute().use { tokenResponse ->
                if (!tokenResponse.isSuccessful) {
                    Log.d(TAG, "[TOKEN] Anonymous token endpoint returned HTTP ${tokenResponse.code}; falling back to embed parser")
                    return@withContext null
                }
                tokenResponse.body?.string()
            }

            if (tokenBody.isNullOrBlank()) {
                Log.e(TAG, "[TOKEN] Empty token response body")
                return@withContext null
            }

            val json = JSONObject(tokenBody)
            val token = json.optString("accessToken", "").takeIf { it.isNotBlank() }
            if (token != null) {
                Log.d(TAG, "[TOKEN] Successfully obtained anonymous token (${token.take(20)}...)")
            } else {
                Log.e(TAG, "[TOKEN] Response missing accessToken: ${tokenBody.take(300)}")
            }
            token
        } catch (e: Exception) {
            Log.w(TAG, "[TOKEN] Exception fetching anonymous token: ${e.message}")
            null
        }
    }

    /**
     * Spotify's public REST API only exposes playlist contents for playlists
     * owned by or shared with the authenticated user. The web player query can
     * read public playlists and supports offset pagination.
     */
    private suspend fun fetchViaPathfinder(playlistId: String, token: String): SpotifyPlaylistData? = withContext(Dispatchers.IO) {
        try {
            val allTracks = mutableListOf<SpotifyTrackInfo>()
            var playlistName = "Imported Playlist"
            var playlistDescription: String? = null
            var artworkUrl: String? = null
            var offset = 0
            var totalCount = Int.MAX_VALUE
            var activeQueryHash = PLAYLIST_QUERY_HASH

            while (offset < totalCount) {
                var pageJson: JSONObject? = null
                val hashesToTry = if (activeQueryHash == PLAYLIST_QUERY_HASH) {
                    listOf(PLAYLIST_QUERY_HASH, PREVIOUS_PLAYLIST_QUERY_HASH)
                } else {
                    listOf(activeQueryHash)
                }

                for (queryHash in hashesToTry) {
                    val variables = JSONObject()
                        .put("uri", "spotify:playlist:$playlistId")
                        .put("offset", offset)
                        .put("limit", 100)
                        .put("enableWatchFeedEntrypoint", false)
                    val requestBody = JSONObject()
                        .put("operationName", "fetchPlaylist")
                        .put("variables", variables)
                        .put("extensions", JSONObject().put(
                            "persistedQuery",
                            JSONObject().put("version", 1).put("sha256Hash", queryHash)
                        ))
                        .toString()
                        .toRequestBody("application/json; charset=utf-8".toMediaType())

                    val request = Request.Builder()
                        .url("https://api-partner.spotify.com/pathfinder/v2/query")
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/json")
                        .header("app-platform", "WebPlayer")
                        .header("Origin", "https://open.spotify.com")
                        .header("Referer", "https://open.spotify.com/")
                        .post(requestBody)
                        .build()

                    val (statusCode, responseBody) = httpClient.newCall(request).execute().use { response ->
                        response.code to response.body?.string()
                    }

                    if (responseBody.isNullOrBlank()) {
                        Log.w(TAG, "[PATHFINDER] Empty response for playlist page (HTTP $statusCode)")
                        continue
                    }
                    val candidateJson = try {
                        JSONObject(responseBody)
                    } catch (_: Exception) {
                        Log.w(TAG, "[PATHFINDER] Invalid JSON for playlist page (HTTP $statusCode)")
                        continue
                    }
                    val hasPlaylist = candidateJson.optJSONObject("data")?.optJSONObject("playlistV2")
                        ?.optJSONObject("content") != null
                    if (statusCode in 200..299 && hasPlaylist) {
                        pageJson = candidateJson
                        activeQueryHash = queryHash
                        break
                    }
                    Log.w(TAG, "[PATHFINDER] Query failed for playlist page: HTTP $statusCode")
                }

                val responseJson = pageJson ?: return@withContext null
                val playlist = responseJson.optJSONObject("data")?.optJSONObject("playlistV2")
                    ?: run {
                        Log.w(TAG, "[PATHFINDER] Response did not contain playlist data")
                        return@withContext null
                    }
                val content = playlist.optJSONObject("content")
                    ?: run {
                        Log.w(TAG, "[PATHFINDER] Playlist response did not contain content")
                        return@withContext null
                    }
                val items = content.optJSONArray("items")
                    ?: run {
                        Log.w(TAG, "[PATHFINDER] Playlist content did not contain items")
                        return@withContext null
                    }

                if (offset == 0) {
                    playlistName = playlist.optString("name", playlistName).ifBlank { playlistName }
                    playlistDescription = playlist.optString("description", "").takeIf { it.isNotBlank() }
                    val imageGroups = playlist.optJSONObject("images")?.optJSONArray("items")
                    artworkUrl = imageGroups?.optJSONObject(0)?.optJSONArray("sources")
                        ?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() }
                }

                for (i in 0 until items.length()) {
                    val entry = items.optJSONObject(i) ?: continue
                    val itemData = entry.optJSONObject("itemV2")?.optJSONObject("data")
                        ?: entry.optJSONObject("item")
                        ?: continue
                    val name = itemData.optString("name", "").takeIf { it.isNotBlank() } ?: continue

                    val gqlArtists = itemData.optJSONObject("artists")?.optJSONArray("items")
                    val artistNames = if (gqlArtists != null) {
                        (0 until gqlArtists.length()).mapNotNull { index ->
                            val artist = gqlArtists.optJSONObject(index) ?: return@mapNotNull null
                            artist.optJSONObject("profile")?.optString("name")
                                ?.takeIf { it.isNotBlank() }
                                ?: artist.optString("name", "").takeIf { it.isNotBlank() }
                        }
                    } else {
                        val artists = itemData.optJSONArray("artists")
                        if (artists == null) emptyList() else (0 until artists.length()).mapNotNull { index ->
                            artists.optJSONObject(index)?.optString("name")?.takeIf { it.isNotBlank() }
                        }
                    }
                    allTracks.add(SpotifyTrackInfo(name, artistNames.joinToString(", ").ifBlank { "Unknown Artist" }))
                }

                val pageItemCount = items.length()
                totalCount = content.optInt("totalCount", totalCount)
                if (pageItemCount == 0) break
                offset += pageItemCount
                Log.d(TAG, "[PATHFINDER] ${allTracks.size}/$totalCount tracks fetched")
            }

            if (allTracks.isEmpty()) null
            else SpotifyPlaylistData(playlistName, playlistDescription, artworkUrl, allTracks)
        } catch (e: Exception) {
            Log.w(TAG, "[PATHFINDER] Exception; falling back to REST/embed: ${e.message}")
            null
        }
    }

    /**
     * Fallback: use Spotify's REST API. The public playlist items endpoint may
     * reject playlists the current user does not own or collaborate on.
     */
    private suspend fun fetchViaApi(playlistId: String, token: String): SpotifyPlaylistData? = withContext(Dispatchers.IO) {
        try {
            // Fetch playlist metadata
            val metaRequest = Request.Builder()
                .url("https://api.spotify.com/v1/playlists/$playlistId?fields=name,description,images")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .get()
                .build()

            val metaBody = httpClient.newCall(metaRequest).execute().use { metaResponse ->
                if (!metaResponse.isSuccessful) {
                    Log.e(TAG, "[API] Metadata fetch failed: HTTP ${metaResponse.code}")
                    return@withContext null
                }
                metaResponse.body?.string()
            }

            if (metaBody.isNullOrBlank()) {
                return@withContext null
            }

            val metaJson = JSONObject(metaBody)
            val playlistName = metaJson.optString("name", "Imported Playlist")
            val playlistDesc = metaJson.optString("description", "").takeIf { it.isNotBlank() }
            val images = metaJson.optJSONArray("images")
            val artworkUrl = images?.optJSONObject(0)?.optString("url")

            Log.d(TAG, "[API] Playlist: \"$playlistName\"")

            // Spotify's current playlist endpoint is paginated with a maximum
            // page size of 50. Keep following every page so long playlists are
            // not silently truncated at the first response.
            val allTracks = mutableListOf<SpotifyTrackInfo>()
            val visitedPageUrls = mutableSetOf<String>()
            var nextUrl: String? = "https://api.spotify.com/v1/playlists/$playlistId/items?fields=items(item(name,artists(name))),next,total,offset&limit=50&offset=0"
            var fallbackOffset = 0

            while (nextUrl != null) {
                if (!visitedPageUrls.add(nextUrl)) {
                    Log.e(TAG, "[API] Spotify repeated a playlist page URL; refusing to return a partial playlist")
                    return@withContext null
                }

                val tracksRequest = Request.Builder()
                    .url(nextUrl)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .get()
                    .build()

                val tracksResponse = httpClient.newCall(tracksRequest).execute()
                val tracksBody = tracksResponse.use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "[API] Playlist page failed: HTTP ${response.code}")
                        return@withContext null
                    }
                    response.body?.string()
                }

                if (tracksBody.isNullOrBlank()) {
                    Log.e(TAG, "[API] Playlist page returned an empty response")
                    return@withContext null
                }

                val tracksJson = JSONObject(tracksBody)
                val items = tracksJson.optJSONArray("items")
                    ?: run {
                        Log.e(TAG, "[API] Playlist page did not contain an items array")
                        return@withContext null
                    }

                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    // The current /items endpoint returns `item`; keep `track`
                    // as a fallback for older Spotify response formats.
                    val track = item.optJSONObject("item") ?: item.optJSONObject("track") ?: continue
                    val name = track.optString("name", "").takeIf { it.isNotBlank() } ?: continue
                    val artists = track.optJSONArray("artists")
                    val artistName = if (artists != null && artists.length() > 0) {
                        (0 until artists.length()).mapNotNull { idx ->
                            artists.optJSONObject(idx)?.optString("name")
                        }.joinToString(", ")
                    } else "Unknown Artist"
                    allTracks.add(SpotifyTrackInfo(title = name, artist = artistName))
                }

                val pageOffset = tracksJson.optInt("offset", fallbackOffset)
                fallbackOffset = pageOffset + items.length()
                val next = tracksJson.optString("next", "null")
                    .takeIf { it != "null" && it.isNotBlank() }
                val total = tracksJson.optInt("total", -1)

                nextUrl = next ?: if (total >= 0 && fallbackOffset < total && items.length() > 0) {
                    // Use offset pagination if Spotify omits `next` despite
                    // reporting additional items.
                    "https://api.spotify.com/v1/playlists/$playlistId/items?fields=items(item(name,artists(name))),next,total,offset&limit=50&offset=$fallbackOffset"
                } else {
                    null
                }
                Log.d(TAG, "[API] ${allTracks.size} tracks fetched so far (page offset=$pageOffset, total=$total)")
            }

            if (allTracks.isEmpty()) return@withContext null

            SpotifyPlaylistData(name = playlistName, description = playlistDesc, artworkUrl = artworkUrl, tracks = allTracks)
        } catch (e: Exception) {
            Log.e(TAG, "[API] Exception", e)
            null
        }
    }

    /**
     * Fallback: scrape the Spotify embed page HTML which contains track data.
     */
    private suspend fun fetchViaEmbed(playlistId: String): SpotifyPlaylistData? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[EMBED] Fetching embed page for playlist $playlistId...")
            val embedRequest = Request.Builder()
                .url("https://open.spotify.com/embed/playlist/$playlistId")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .get()
                .build()

            val html = httpClient.newCall(embedRequest).execute().use { embedResponse ->
                if (!embedResponse.isSuccessful) {
                    Log.e(TAG, "[EMBED] Failed: HTTP ${embedResponse.code}")
                    return@withContext null
                }
                embedResponse.body?.string()
            }

            if (html.isNullOrBlank()) {
                Log.e(TAG, "[EMBED] Empty HTML response")
                return@withContext null
            }

            Log.d(TAG, "[EMBED] Got HTML (${html.length} chars), parsing...")

            // Try to find __NEXT_DATA__ JSON block
            val nextDataRegex = Regex("""<script\s+id="__NEXT_DATA__"\s+type="application/json">\s*(\{.*?\})\s*</script>""", RegexOption.DOT_MATCHES_ALL)
            val nextDataMatch = nextDataRegex.find(html)

            if (nextDataMatch != null) {
                val jsonStr = nextDataMatch.groupValues[1]
                Log.d(TAG, "[EMBED] Found __NEXT_DATA__ (${jsonStr.length} chars)")
                return@withContext parseNextData(jsonStr)
            }

            // Alternative: look for embedded JSON state in a script tag
            val stateRegex = Regex("""<script[^>]*>\s*window\.__SPOTIFY_STATE__\s*=\s*(\{.*?\});\s*</script>""", RegexOption.DOT_MATCHES_ALL)
            val stateMatch = stateRegex.find(html)
            if (stateMatch != null) {
                Log.d(TAG, "[EMBED] Found __SPOTIFY_STATE__")
                return@withContext parseNextData(stateMatch.groupValues[1])
            }

            // Final fallback: extract track info from HTML meta tags / structured data
            Log.d(TAG, "[EMBED] No JSON data found in embed page, trying meta tag extraction...")
            return@withContext parseEmbedHtmlMeta(html, playlistId)

        } catch (e: Exception) {
            Log.e(TAG, "[EMBED] Exception", e)
            null
        }
    }

    private fun parseNextData(jsonStr: String): SpotifyPlaylistData? {
        return try {
            val root = JSONObject(jsonStr)
            Log.d(TAG, "[EMBED_JSON] Parsing __NEXT_DATA__ JSON (${jsonStr.length} chars)")
            
            val props = root.optJSONObject("props")
            val pageProps = props?.optJSONObject("pageProps")

            var name: String? = null
            var description: String? = null
            var artworkUrl: String? = null

            // Try explicit entity locations
            val entityObj = pageProps?.optJSONObject("state")?.optJSONObject("data")?.optJSONObject("entity")
                ?: pageProps?.optJSONObject("entity")
                ?: pageProps?.optJSONObject("playlist")
                ?: pageProps?.optJSONObject("data")?.optJSONObject("playlist")
                ?: pageProps?.optJSONObject("initialState")?.optJSONObject("playlist")

            if (entityObj != null) {
                name = entityObj.optString("name", "").takeIf { it.isNotBlank() }
                    ?: entityObj.optString("title", "").takeIf { it.isNotBlank() }
                description = entityObj.optString("subtitle", "")
                    .ifBlank { entityObj.optString("description", "") }
                    .takeIf { it.isNotBlank() }
                artworkUrl = entityObj.optString("coverArt", "")
                    .ifBlank { entityObj.optString("images", "") }
                    .ifBlank { entityObj.optJSONArray("images")?.optJSONObject(0)?.optString("url", "") ?: "" }
                    .takeIf { it.isNotBlank() }
            }

            val tracks = mutableListOf<SpotifyTrackInfo>()

            // Try explicit track lists
            val trackListItems = entityObj?.optJSONObject("trackList")?.optJSONArray("items")
                ?: entityObj?.optJSONArray("tracks")
                ?: entityObj?.optJSONObject("tracks")?.optJSONArray("items")
                ?: pageProps?.optJSONObject("state")?.optJSONObject("data")?.optJSONObject("trackList")?.optJSONArray("items")

            if (trackListItems != null && trackListItems.length() > 0) {
                for (i in 0 until trackListItems.length()) {
                    val item = trackListItems.optJSONObject(i) ?: continue
                    val tName = item.optString("title", "")
                        .ifBlank { item.optString("name", "") }
                        .ifBlank { item.optJSONObject("track")?.optString("name", "") ?: "" }
                        .takeIf { it.isNotBlank() } ?: continue

                    val tArtist = item.optString("subtitle", "")
                        .ifBlank { item.optString("artist", "") }
                        .ifBlank {
                            val artistsArr = item.optJSONArray("artists") ?: item.optJSONObject("track")?.optJSONArray("artists")
                            if (artistsArr != null && artistsArr.length() > 0) {
                                (0 until artistsArr.length()).mapNotNull { idx ->
                                    artistsArr.optJSONObject(idx)?.optString("name")
                                }.joinToString(", ")
                            } else ""
                        }
                        .ifBlank { "Unknown Artist" }

                    tracks.add(SpotifyTrackInfo(title = tName, artist = tArtist))
                }
            }

            // Deep JSON search if explicit paths produced no tracks
            if (tracks.isEmpty()) {
                Log.d(TAG, "[EMBED_JSON] Explicit paths empty. Running deep JSON tree search...")
                findTracksDeep(root, tracks)
            }

            if (name.isNullOrBlank()) {
                name = pageProps?.optString("name", "").takeIf { it!!.isNotBlank() }
                    ?: "Imported Playlist"
            }

            // Remove any track item that matches the playlist name itself or generic site titles
            val finalPlaylistName = name
            tracks.removeAll {
                it.title.equals(finalPlaylistName, ignoreCase = true) ||
                it.title.equals("Spotify", ignoreCase = true) ||
                it.title.equals("Imported Playlist", ignoreCase = true) ||
                it.title.startsWith("http", ignoreCase = true)
            }

            if (tracks.isEmpty()) {
                Log.e(TAG, "[EMBED_JSON] Deep JSON search found 0 valid tracks.")
                return null
            }

            Log.d(TAG, "[EMBED_JSON] SUCCESS: Extracted playlist \"$name\" with ${tracks.size} tracks")
            SpotifyPlaylistData(name = name, description = description, artworkUrl = artworkUrl, tracks = tracks)
        } catch (e: Exception) {
            Log.e(TAG, "[EMBED_JSON] Error parsing JSON", e)
            null
        }
    }

    private fun findTracksDeep(obj: Any?, outTracks: MutableList<SpotifyTrackInfo>, depth: Int = 0) {
        if (depth > 12 || obj == null) return

        if (obj is JSONObject) {
            val title = obj.optString("title", "").ifBlank { obj.optString("name", "") }.trim()
            val subtitle = obj.optString("subtitle", "").ifBlank { obj.optString("artist", "") }.trim()
            val uri = obj.optString("uri", "")

            val isTrackUri = uri.contains("spotify:track:") || obj.has("duration_ms") || obj.has("duration") || obj.has("trackList")

            if (title.isNotBlank() && (subtitle.isNotBlank() || isTrackUri)) {
                var artistName = subtitle
                if (artistName.isBlank()) {
                    val artistsArr = obj.optJSONArray("artists")
                    if (artistsArr != null && artistsArr.length() > 0) {
                        artistName = (0 until artistsArr.length()).mapNotNull { idx ->
                            artistsArr.optJSONObject(idx)?.optString("name")
                        }.joinToString(", ")
                    }
                }
                if (artistName.isBlank()) artistName = "Unknown Artist"

                // Filter out non-track strings like "Spotify" or URL strings
                if (title != "Spotify" && title != "Imported Playlist" && !title.startsWith("http") && !title.contains("open.spotify") && !title.contains("playlist", ignoreCase = true)) {
                    val candidate = SpotifyTrackInfo(title = title, artist = artistName)
                    if (!outTracks.any { it.title.equals(title, ignoreCase = true) && it.artist.equals(artistName, ignoreCase = true) }) {
                        outTracks.add(candidate)
                    }
                }
            }

            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                findTracksDeep(obj.opt(key), outTracks, depth + 1)
            }
        } else if (obj is JSONArray) {
            for (i in 0 until obj.length()) {
                findTracksDeep(obj.opt(i), outTracks, depth + 1)
            }
        }
    }

    private fun parseEmbedHtmlMeta(html: String, playlistId: String): SpotifyPlaylistData? {
        val titleRegex = Regex("""<title>(.*?)</title>""")
        val title = titleRegex.find(html)?.groupValues?.get(1)?.replace(" | Spotify", "")?.replace(" - playlist by", "")?.trim() ?: "Imported Playlist"

        // Match song title / artist patterns from HTML tags
        val trackRegex = Regex("""(?i)"title"\s*:\s*"([^"]+)"\s*,\s*"subtitle"\s*:\s*"([^"]+)"""")
        val matches = trackRegex.findAll(html).toList()

        val tracks = matches.mapNotNull { m ->
            val trackName = m.groupValues[1].trim()
            val artistName = m.groupValues[2].trim()
            if (trackName.isNotBlank() && trackName != "Spotify") {
                SpotifyTrackInfo(title = trackName, artist = artistName.ifBlank { "Unknown Artist" })
            } else null
        }.distinctBy { "${it.title}:${it.artist}".lowercase() }

        if (tracks.isNotEmpty()) {
            Log.d(TAG, "[EMBED_META] Extracted ${tracks.size} tracks via regex")
            return SpotifyPlaylistData(name = title, description = null, artworkUrl = null, tracks = tracks)
        }

        Log.e(TAG, "[EMBED_META] Could not extract tracks from HTML meta tags")
        return null
    }

    /**
     * Looks up a playlist's cover image without fetching its tracks. Uses the
     * public oEmbed endpoint first and the embed page as a fallback; neither
     * needs a token.
     */
    suspend fun fetchPlaylistCoverUrl(playlistId: String): String? = withContext(Dispatchers.IO) {
        fetchOEmbed(playlistId)?.second?.let { return@withContext it }

        try {
            val request = Request.Builder()
                .url("https://open.spotify.com/embed/playlist/$playlistId")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .get()
                .build()
            val html = httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            } ?: return@withContext null
            SPOTIFY_IMAGE_REGEX.find(html)?.value
        } catch (e: Exception) {
            Log.w(TAG, "[COVER] Embed fallback failed for $playlistId: ${e.message}")
            null
        }
    }

    /** Title and cover of a public playlist from Spotify's oEmbed endpoint (no token needed). */
    private fun fetchOEmbed(playlistId: String): Pair<String?, String?>? {
        return try {
            val oembedUrl = HttpUrl.Builder()
                .scheme("https").host("open.spotify.com").addPathSegment("oembed")
                .addQueryParameter("url", "https://open.spotify.com/playlist/$playlistId")
                .build()
            val request = Request.Builder()
                .url(oembedUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .get()
                .build()
            val body = httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            } ?: return null
            val json = JSONObject(body)
            json.optString("title", "").takeIf { it.isNotBlank() } to
                json.optString("thumbnail_url", "").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "[COVER] oEmbed failed for $playlistId: ${e.message}")
            null
        }
    }

    /**
     * Spotify doesn't publish a playlist's creation date, so this returns the
     * earliest "added at" time among its first page of tracks, which is when the
     * playlist started being filled. Returns null when the web-player token is
     * unavailable. [token] can be reused across calls via [obtainAnonymousToken].
     */
    suspend fun fetchPlaylistEarliestAddedAt(playlistId: String, token: String): Long? = withContext(Dispatchers.IO) {
        try {
            for (queryHash in listOf(PLAYLIST_QUERY_HASH, PREVIOUS_PLAYLIST_QUERY_HASH)) {
                val variables = JSONObject()
                    .put("uri", "spotify:playlist:$playlistId")
                    .put("offset", 0)
                    .put("limit", 100)
                    .put("enableWatchFeedEntrypoint", false)
                val requestBody = JSONObject()
                    .put("operationName", "fetchPlaylist")
                    .put("variables", variables)
                    .put("extensions", JSONObject().put(
                        "persistedQuery",
                        JSONObject().put("version", 1).put("sha256Hash", queryHash)
                    ))
                    .toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url("https://api-partner.spotify.com/pathfinder/v2/query")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .header("app-platform", "WebPlayer")
                    .header("Origin", "https://open.spotify.com")
                    .header("Referer", "https://open.spotify.com/")
                    .post(requestBody)
                    .build()
                val body = httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() else null
                } ?: continue
                val items = JSONObject(body).optJSONObject("data")?.optJSONObject("playlistV2")
                    ?.optJSONObject("content")?.optJSONArray("items") ?: continue
                return@withContext (0 until items.length()).mapNotNull { index ->
                    val addedAt = items.optJSONObject(index)?.optJSONObject("addedAt")
                    SpotifyDates.parse(addedAt?.optString("isoString"))
                }.minOrNull()
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "[DATES] Failed to read added dates for $playlistId: ${e.message}")
            null
        }
    }

    /** Fetches a fresh anonymous web-player token, or null if Spotify refuses. */
    suspend fun obtainAnonymousToken(): String? {
        cookieStore.clear()
        return fetchAnonymousToken()
    }

    /**
     * Main entry point: try API method first, fall back to embed scraping & oEmbed.
     */
    suspend fun fetchPlaylist(url: String): Result<SpotifyPlaylistData> = withContext(Dispatchers.IO) {
        val playlistId = extractPlaylistId(url)
        if (playlistId == null) {
            return@withContext Result.failure(IllegalArgumentException("Invalid Spotify playlist URL. Please paste a link like:\nhttps://open.spotify.com/playlist/..."))
        }

        Log.d(TAG, "[FETCH] Starting playlist fetch for ID: $playlistId")
        cookieStore.clear()

        // Method 1: Anonymous web-player token + playlist pagination
        val token = fetchAnonymousToken()
        if (token != null) {
            Log.d(TAG, "[FETCH] Got token, trying Spotify web-player pagination...")
            val pathfinderResult = fetchViaPathfinder(playlistId, token)
            if (pathfinderResult != null && pathfinderResult.tracks.isNotEmpty()) {
                Log.d(TAG, "[FETCH] Web-player method succeeded: ${pathfinderResult.tracks.size} tracks")
                return@withContext Result.success(pathfinderResult)
            }

            Log.w(TAG, "[FETCH] Web-player method returned no results, trying REST API...")
            val apiResult = fetchViaApi(playlistId, token)
            if (apiResult != null && apiResult.tracks.isNotEmpty()) {
                Log.d(TAG, "[FETCH] API method succeeded: ${apiResult.tracks.size} tracks")
                return@withContext Result.success(apiResult)
            }
            Log.w(TAG, "[FETCH] API method returned no results, trying embed fallback...")
        } else {
            Log.w(TAG, "[FETCH] Could not get anonymous token, trying embed fallback...")
        }

        // Method 2: Embed page JSON scraping
        val embedResult = fetchViaEmbed(playlistId)
        if (embedResult != null && embedResult.tracks.isNotEmpty()) {
            Log.d(TAG, "[FETCH] Embed method succeeded: ${embedResult.tracks.size} tracks")
            return@withContext Result.success(embedResult)
        }

        Log.e(TAG, "[FETCH] All methods failed for playlist $playlistId")
        Result.failure(RuntimeException("Could not fetch playlist. Please make sure:\n• The playlist is set to Public\n• The link is correct\n• You have internet connection"))
    }
}

/** Parses the date formats Spotify and export tools use. minSdk 24 rules out java.time. */
object SpotifyDates {
    private val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )

    fun parse(value: Any?): Long? {
        when (value) {
            null, JSONObject.NULL -> return null
            is Number -> return fromEpoch(value.toDouble())
        }
        val text = value.toString().trim()
        if (text.isEmpty() || text == "null") return null
        text.toDoubleOrNull()?.let { return fromEpoch(it) }
        // SimpleDateFormat's XXX accepts "Z", but not fractional seconds beyond millis.
        val normalized = text.replace(Regex("""(\.\d{3})\d+"""), "$1")
        for (pattern in patterns) {
            try {
                val format = java.text.SimpleDateFormat(pattern, java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                    isLenient = false
                }
                val position = java.text.ParsePosition(0)
                val date = format.parse(normalized, position)
                if (date != null && position.index == normalized.length) return date.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun fromEpoch(number: Double): Long? {
        if (number <= 0) return null
        // Values below ~1e11 are seconds (that's the year 5138 in millis).
        return if (number < 1e11) (number * 1000).toLong() else number.toLong()
    }
}
