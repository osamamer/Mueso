package com.akshay.musicplayer.ui.viewmodel.managers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.akshay.musicplayer.data.db.OnlinePlaylistDao
import com.akshay.musicplayer.data.db.OnlinePlaylistEntity
import com.akshay.musicplayer.data.db.OnlinePlaylistTrackEntity
import com.akshay.musicplayer.data.remote.OnlineMusicRepository
import com.akshay.musicplayer.data.remote.SpotifyImportRepository
import com.akshay.musicplayer.data.remote.SpotifyPlaylistData
import com.akshay.musicplayer.data.remote.SpotifyTrackInfo
import com.akshay.musicplayer.data.remote.SpotifyDates
import com.akshay.musicplayer.data.remote.SpotifyAccountPlaylist
import com.akshay.musicplayer.data.remote.SpotifyAuth
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.media.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class SpotifyImportState {
    Idle,
    FetchingSpotify,
    MatchingTracks,
    Ready,
    Creating,
    BulkImporting,
    BulkImportDone,
    Done,
    Error
}

data class BulkPlaylistImportProgress(
    val currentPlaylistIndex: Int = 0,
    val totalPlaylists: Int = 0,
    val currentPlaylistName: String = "",
    val tracksProcessed: Int = 0,
    val totalTracks: Int = 0,
    val matchedTracks: Int = 0,
    val unmatchedTracks: Int = 0,
    val completedPlaylists: Int = 0,
    val isCancelled: Boolean = false
)

data class SpotifyMetadataRefreshState(
    val isRunning: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

data class TrackMatchResult(
    val spotifyTrack: SpotifyTrackInfo,
    val matchedTrack: TrackEntity? = null,
    val isSearching: Boolean = false,
    val alternativeResults: List<TrackEntity> = emptyList(),
    val showAlternatives: Boolean = false,
    val isSelected: Boolean = true
)

class SpotifyImportManager(
    private val spotifyRepo: SpotifyImportRepository,
    private val onlineRepo: OnlineMusicRepository,
    private val onlinePlaylistDao: OnlinePlaylistDao,
    private val coroutineScope: CoroutineScope,
    private val markDirty: () -> Unit,
    private val prefs: android.content.SharedPreferences? = null,
    private val onYouTubeRefresh: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "MUESO_SPOTIFY_IMPORT"
        private const val PREF_CLIENT_ID = "spotify_auth_client_id"
        private const val PREF_VERIFIER = "spotify_auth_code_verifier"
        private const val PREF_STATE = "spotify_auth_state"
        private val PLAYLIST_CREATED_KEYS = listOf(
            "created", "createdAt", "created_at", "createdDate", "dateCreated", "date_created",
            "creationDate", "creation_date", "createdTime", "created_time"
        )
        private val PLACEHOLDER_DESCRIPTIONS = setOf("Imported from Spotify JSON", "Imported from Spotify")
        private val TRACK_ADDED_KEYS = listOf(
            "addedAt", "added_at", "addedDate", "added_date", "dateAdded", "date_added", "added"
        )
    }

    private val _metadataRefresh = MutableStateFlow(SpotifyMetadataRefreshState())
    val metadataRefresh: StateFlow<SpotifyMetadataRefreshState> = _metadataRefresh.asStateFlow()

    private val _importState = MutableStateFlow(SpotifyImportState.Idle)
    val importState: StateFlow<SpotifyImportState> = _importState.asStateFlow()

    private val _spotifyPlaylistData = MutableStateFlow<SpotifyPlaylistData?>(null)
    val spotifyPlaylistData: StateFlow<SpotifyPlaylistData?> = _spotifyPlaylistData.asStateFlow()

    private val _matchResults = MutableStateFlow<List<TrackMatchResult>>(emptyList())
    val matchResults: StateFlow<List<TrackMatchResult>> = _matchResults.asStateFlow()

    private val _matchProgress = MutableStateFlow(0)
    val matchProgress: StateFlow<Int> = _matchProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _createdDestination = MutableStateFlow<String>("online")
    val createdDestination: StateFlow<String> = _createdDestination.asStateFlow()

    private val _bulkImportProgress = MutableStateFlow(BulkPlaylistImportProgress())
    val bulkImportProgress: StateFlow<BulkPlaylistImportProgress> = _bulkImportProgress.asStateFlow()

    private var currentSourceUrl: String? = null
    private var syncTargetPlaylistId: Long? = null
    private val _syncTargetPlaylistName = MutableStateFlow<String?>(null)
    val syncTargetPlaylistName: StateFlow<String?> = _syncTargetPlaylistName.asStateFlow()
    val isSyncMode: Boolean get() = syncTargetPlaylistId != null

    private var importJob: Job? = null
    private var lastContext: Context? = null

    private data class SourcePlaylist(
        val name: String,
        val artworkUrl: String?,
        val tracks: List<SpotifyTrackInfo>,
        /** Spotify playlist ID, when the export includes one. */
        val spotifyId: String? = null,
        /** Playlist-level cover image, when the export includes one. */
        val coverUrl: String? = null,
        /** Creation time in millis: explicit field, else the earliest track "added" date. */
        val createdAt: Long? = null,
        /** Spotify's playlist description, null when the export has none. */
        val description: String? = null
    )

    private val genericChannelTerms = setOf(
        "vevo", "records", "music", "tseries", "series", "entertainment", "channel",
        "audio", "media", "production", "productions", "studios", "official", "sound", "sounds"
    )

    private fun cleanString(input: String): String {
        return input.lowercase()
            .replace(Regex("""\s*[\(\[\{].*?[\)\]\}]"""), "")
            .replace(Regex("""\b(feat|ft|featuring|remastered|remaster|version|edit|audio|video|official|lyric|lyrics|full)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[^\p{L}\p{Nd}]+"""), "")
            .trim()
    }

    private fun extractWords(input: String): List<String> {
        return input.lowercase()
            .replace(Regex("""\s*[\(\[\{].*?[\)\]\}]"""), "")
            .replace(Regex("""\b(feat|ft|featuring|remastered|remaster|version|edit|audio|video|official|lyric|lyrics|full|song)\b""", RegexOption.IGNORE_CASE), " ")
            .split(Regex("""[^\p{L}\p{Nd}]+"""))
            .filter { it.length >= 2 }
    }

    private fun isTrackAlreadyPresent(spotifyTrack: SpotifyTrackInfo, existingTracks: List<OnlinePlaylistTrackEntity>): Boolean {
        val cleanSpotifyTitle = cleanString(spotifyTrack.title)
        val spotifyTitleWords = extractWords(spotifyTrack.title)
        val spotifyArtistWords = extractWords(spotifyTrack.artist)

        if (cleanSpotifyTitle.isEmpty()) return false

        return existingTracks.any { existing ->
            val cleanExistingTitle = cleanString(existing.title)
            val cleanExistingArtist = cleanString(existing.artist)
            val existingArtistWords = extractWords(existing.artist)

            // 1. Exact title match
            val isExactTitle = cleanExistingTitle == cleanSpotifyTitle

            // 2. Substring title match
            val isSubstringTitle = (cleanSpotifyTitle.length >= 4 && cleanExistingTitle.contains(cleanSpotifyTitle)) ||
                    (cleanExistingTitle.length >= 4 && cleanSpotifyTitle.contains(cleanExistingTitle))

            // 3. Word token match: all main words of Spotify title are inside the existing title
            val isTokenTitle = spotifyTitleWords.size >= 2 && spotifyTitleWords.all { word ->
                cleanExistingTitle.contains(word)
            }

            val titleMatched = isExactTitle || isSubstringTitle || isTokenTitle
            if (!titleMatched) return@any false

            // Check artist overlap
            val existingFullText = "$cleanExistingTitle $cleanExistingArtist"
            val hasArtistOverlap = spotifyArtistWords.any { artistWord ->
                artistWord.length >= 3 && existingFullText.contains(artistWord)
            }

            if (hasArtistOverlap) return@any true

            // If existing artist is purely a generic channel name (e.g. "T-Series", "SonyMusicSouthVEVO"), trust the title match
            val isGenericChannel = existingArtistWords.isEmpty() || existingArtistWords.all { it in genericChannelTerms }
            if (isGenericChannel && (isExactTitle || isSubstringTitle || isTokenTitle)) {
                return@any true
            }

            false
        }
    }

    fun fetchAndMatch(context: Context, url: String) {
        lastContext = context.applicationContext
        importJob?.cancel()
        currentSourceUrl = url
        syncTargetPlaylistId = null
        _syncTargetPlaylistName.value = null
        importJob = coroutineScope.launch(Dispatchers.IO) {
            _importState.value = SpotifyImportState.FetchingSpotify
            _errorMessage.value = null
            _matchResults.value = emptyList()
            _matchProgress.value = 0

            Log.d(TAG, "Starting Spotify import for URL: $url")

            val result = spotifyRepo.fetchPlaylist(url)

            if (result.isFailure) {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                Log.e(TAG, "Failed to fetch Spotify playlist: $error")
                _errorMessage.value = error
                _importState.value = SpotifyImportState.Error
                return@launch
            }

            val playlistData = result.getOrNull()!!
            _spotifyPlaylistData.value = playlistData

            Log.d(TAG, "Fetched ${playlistData.tracks.size} tracks from Spotify playlist: \"${playlistData.name}\"")

            // Initialize match results with all tracks as unmatched
            _matchResults.value = playlistData.tracks.map { TrackMatchResult(spotifyTrack = it, isSelected = false) }

            _importState.value = SpotifyImportState.MatchingTracks

            // Search YT for each track sequentially
            for (i in playlistData.tracks.indices) {
                val spotifyTrack = playlistData.tracks[i]
                val cleanTitle = OnlineMusicRepository.cleanTitleForLyrics(spotifyTrack.title)
                val primaryArtist = OnlineMusicRepository.getPrimaryArtist(spotifyTrack.artist)
                val query = OnlineMusicRepository.cleanSearchQuery(cleanTitle, primaryArtist)

                Log.d(TAG, "Matching [${i + 1}/${playlistData.tracks.size}]: \"$query\" (raw: \"${spotifyTrack.title}\" - \"${spotifyTrack.artist}\")")

                // Mark this track as searching
                _matchResults.value = _matchResults.value.toMutableList().also { list ->
                    list[i] = list[i].copy(isSearching = true)
                }
                _matchProgress.value = i

                // Post system notification progress
                NotificationHelper.showImportProgress(context.applicationContext, playlistData.name, i, playlistData.tracks.size)

                try {
                    val searchResults = onlineRepo.searchOnlineTracks(query)
                    val ranked = OnlineMusicRepository.rankTracks(searchResults, query)
                    val bestMatch = OnlineMusicRepository.findBestTrackMatch(
                        candidates = ranked,
                        targetTitle = cleanTitle,
                        targetArtist = primaryArtist
                    )

                    _matchResults.value = _matchResults.value.toMutableList().also { list ->
                        list[i] = list[i].copy(
                            matchedTrack = bestMatch,
                            isSearching = false,
                            alternativeResults = ranked.take(8),
                            isSelected = (bestMatch != null)
                        )
                    }

                    if (bestMatch != null) {
                        Log.d(TAG, "  ✓ Matched: \"${bestMatch.title}\" by ${bestMatch.artist}")
                    } else {
                        Log.d(TAG, "  ✗ No match found for \"$query\"")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "  ✗ Search error for \"$query\"", e)
                    _matchResults.value = _matchResults.value.toMutableList().also { list ->
                        list[i] = list[i].copy(isSearching = false)
                    }
                }
            }

            _matchProgress.value = playlistData.tracks.size
            _importState.value = SpotifyImportState.Ready
            val matchedCount = _matchResults.value.count { it.matchedTrack != null }
            NotificationHelper.showImportComplete(context.applicationContext, playlistData.name, matchedCount, playlistData.tracks.size)
            Log.d(TAG, "Matching complete. $matchedCount/${playlistData.tracks.size} tracks matched.")
        }
    }

    /** Imports a Spotify export shaped as { "playlists": [{ "name", "tracks": [...] }] }. */
    fun importPlaylistsFromJson(context: Context, uri: Uri) {
        lastContext = context.applicationContext
        importJob?.cancel()
        currentSourceUrl = null
        syncTargetPlaylistId = null
        _syncTargetPlaylistName.value = null
        importJob = coroutineScope.launch(Dispatchers.IO) {
            _importState.value = SpotifyImportState.BulkImporting
            _errorMessage.value = null
            _spotifyPlaylistData.value = null
            _matchResults.value = emptyList()
            _matchProgress.value = 0
            _bulkImportProgress.value = BulkPlaylistImportProgress()

            try {
                val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: throw IllegalArgumentException("The selected file could not be opened.")
                val sourcePlaylists = parseSpotifyExport(json)
                val totalTracks = sourcePlaylists.sumOf { it.tracks.size }
                _bulkImportProgress.value = BulkPlaylistImportProgress(
                    totalPlaylists = sourcePlaylists.size,
                    totalTracks = totalTracks
                )

                // A Spotify export often repeats the same song across several playlists.
                // Reuse its resolved Mueso match so each unique title/artist pair is searched once.
                val matchCache = mutableMapOf<String, TrackEntity?>()
                var tracksProcessed = 0
                var matchedTracks = 0
                var unmatchedTracks = 0
                var completedPlaylists = 0

                sourcePlaylists.forEachIndexed { playlistIndex, playlist ->
                    currentCoroutineContext().ensureActive()
                    _bulkImportProgress.value = _bulkImportProgress.value.copy(
                        currentPlaylistIndex = playlistIndex + 1,
                        currentPlaylistName = playlist.name
                    )
                    val resolvedTracks = ArrayList<TrackEntity?>(playlist.tracks.size)

                    playlist.tracks.forEachIndexed { trackIndex, spotifyTrack ->
                        currentCoroutineContext().ensureActive()
                        val cleanTitle = OnlineMusicRepository.cleanTitleForLyrics(spotifyTrack.title)
                        val primaryArtist = OnlineMusicRepository.getPrimaryArtist(spotifyTrack.artist)
                        val cacheKey = spotifyTrack.spotifyId?.let { "spotify:$it" }
                            ?: "${cleanString(cleanTitle)}|${cleanString(primaryArtist)}"
                        val wasCached = matchCache.containsKey(cacheKey)
                        val matchedTrack = if (wasCached) {
                            matchCache[cacheKey]
                        } else {
                            val match = try {
                                val query = OnlineMusicRepository.cleanSearchQuery(cleanTitle, primaryArtist)
                                val results = onlineRepo.searchOnlineTracks(query)
                                val ranked = OnlineMusicRepository.rankTracks(results, query)
                                OnlineMusicRepository.findBestTrackMatch(
                                    candidates = ranked,
                                    targetTitle = cleanTitle,
                                    targetArtist = spotifyTrack.artist,
                                    targetDurationMs = spotifyTrack.durationMs
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(TAG, "Search failed for '${spotifyTrack.title}' by '${spotifyTrack.artist}'", e)
                                null
                            }
                            matchCache[cacheKey] = match
                            match
                        }

                        resolvedTracks.add(matchedTrack)
                        tracksProcessed++
                        if (matchedTrack != null) matchedTracks++ else unmatchedTracks++
                        _bulkImportProgress.value = _bulkImportProgress.value.copy(
                            tracksProcessed = tracksProcessed,
                            matchedTracks = matchedTracks,
                            unmatchedTracks = unmatchedTracks
                        )
                        if (!wasCached || trackIndex == 0) {
                            NotificationHelper.showImportProgress(
                                context.applicationContext,
                                "${playlist.name} (${playlistIndex + 1}/${sourcePlaylists.size})",
                                trackIndex,
                                playlist.tracks.size
                            )
                        }
                    }

                    currentCoroutineContext().ensureActive()
                    // Prefer pictures that come from Spotify (the playlist cover, then the first
                    // track's Spotify thumbnail in the file) over the matched YouTube thumbnail.
                    val playlistArtwork = playlist.coverUrl
                        ?: playlist.spotifyId?.let { spotifyRepo.fetchPlaylistCoverUrl(it) }
                        ?: playlist.artworkUrl
                        ?: resolvedTracks.firstNotNullOfOrNull { it?.artworkUrl }
                    val playlistId = onlinePlaylistDao.insertOnlinePlaylist(
                        OnlinePlaylistEntity(
                            name = playlist.name,
                            artworkUrl = playlistArtwork,
                            description = playlist.description,
                            dateCreated = playlist.createdAt ?: System.currentTimeMillis(),
                            spotifyUrl = playlist.spotifyId?.let { "https://open.spotify.com/playlist/$it" }
                        )
                    )
                    val seenTrackIds = mutableSetOf<Long>()
                    var orderIndex = 0
                    resolvedTracks.forEach { track ->
                        if (track == null || !seenTrackIds.add(track.id)) return@forEach
                        onlinePlaylistDao.insertOnlineTrack(
                            OnlinePlaylistTrackEntity(
                                onlinePlaylistId = playlistId,
                                trackId = track.id,
                                title = track.title,
                                artist = track.artist,
                                artworkUrl = track.artworkUrl,
                                filePath = track.filePath,
                                duration = track.duration,
                                orderIndex = orderIndex++
                            )
                        )
                    }
                    markDirty()
                    completedPlaylists++
                    _bulkImportProgress.value = _bulkImportProgress.value.copy(
                        completedPlaylists = completedPlaylists
                    )
                }

                NotificationHelper.dismissImportNotification(context.applicationContext)
                _importState.value = SpotifyImportState.BulkImportDone
                Log.d(
                    TAG,
                    "Imported $completedPlaylists playlists; matched $matchedTracks/$totalTracks tracks"
                )
            } catch (e: CancellationException) {
                _bulkImportProgress.value = _bulkImportProgress.value.copy(isCancelled = true)
                _importState.value = SpotifyImportState.BulkImportDone
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import Spotify playlist JSON", e)
                _errorMessage.value = e.message ?: "The file is not a supported Spotify playlist export."
                _importState.value = SpotifyImportState.Error
            } finally {
                NotificationHelper.dismissImportNotification(context.applicationContext)
            }
        }
    }

    private fun parseSpotifyExport(json: String): List<SourcePlaylist> {
        val playlists = JSONObject(json).optJSONArray("playlists")
            ?: throw IllegalArgumentException("This file doesn't contain a Spotify playlists list.")
        if (playlists.length() == 0) {
            throw IllegalArgumentException("This Spotify export doesn't contain any playlists.")
        }

        return (0 until playlists.length()).map { playlistIndex ->
            val playlist = playlists.optJSONObject(playlistIndex)
                ?: throw IllegalArgumentException("Playlist ${playlistIndex + 1} is not valid JSON.")
            val name = playlist.optString("name", "").trim()
            if (name.isBlank()) {
                throw IllegalArgumentException("Playlist ${playlistIndex + 1} has no name.")
            }
            val tracksJson = playlist.optJSONArray("tracks")
                ?: throw IllegalArgumentException("Playlist '$name' has no tracks list.")
            val tracks = (0 until tracksJson.length()).mapNotNull { trackIndex ->
                val track = tracksJson.optJSONObject(trackIndex) ?: return@mapNotNull null
                val title = track.optString("name", track.optString("title", "")).trim()
                if (title.isBlank()) return@mapNotNull null
                val artist = track.optString("artist", "Unknown Artist")
                    .trim()
                    .ifBlank { "Unknown Artist" }
                val durationMs = (track.optDouble("duration", 0.0).coerceAtLeast(0.0) * 1000.0).toLong()
                val spotifyId = track.optString("uuid", "").takeIf { it.isNotBlank() }
                SpotifyTrackInfo(title = title, artist = artist, durationMs = durationMs, spotifyId = spotifyId)
            }
            val artworkUrl = (0 until tracksJson.length()).asSequence()
                .mapNotNull { tracksJson.optJSONObject(it)?.optString("thumbnail", "")?.takeIf { url -> url.isNotBlank() } }
                .firstOrNull()
            val earliestTrackAdded = (0 until tracksJson.length()).mapNotNull { trackIndex ->
                val track = tracksJson.optJSONObject(trackIndex) ?: return@mapNotNull null
                TRACK_ADDED_KEYS.firstNotNullOfOrNull { key -> SpotifyDates.parse(track.opt(key)) }
            }.minOrNull()
            SourcePlaylist(
                name = name,
                artworkUrl = artworkUrl,
                tracks = tracks,
                spotifyId = playlistIdFromJson(playlist),
                coverUrl = playlistCoverFromJson(playlist),
                description = playlist.optString("description", "").trim()
                    .takeIf { it.isNotBlank() && it != "null" },
                createdAt = PLAYLIST_CREATED_KEYS.firstNotNullOfOrNull { key -> SpotifyDates.parse(playlist.opt(key)) }
                    ?: earliestTrackAdded
            )
        }
    }

    private fun playlistIdFromJson(playlist: JSONObject): String? {
        for (key in listOf("uri", "url", "link", "href", "spotifyUrl", "spotify_url", "spotifyUri", "spotify_uri", "playlistId", "playlist_id", "id", "uuid")) {
            SpotifyImportRepository.normalizePlaylistId(playlist.optString(key, ""))?.let { return it }
        }
        val external = playlist.optJSONObject("external_urls") ?: playlist.optJSONObject("externalUrls")
        return SpotifyImportRepository.normalizePlaylistId(external?.optString("spotify", ""))
    }

    private fun playlistCoverFromJson(playlist: JSONObject): String? {
        fun urlOf(value: Any?): String? = when (value) {
            is String -> value.trim().takeIf { it.startsWith("http") }
            is JSONObject -> urlOf(value.opt("url")) ?: urlOf(value.optJSONArray("sources")?.opt(0))
            is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { urlOf(value.opt(it)) }
            else -> null
        }
        for (key in listOf("image", "imageUrl", "image_url", "images", "cover", "coverUrl", "cover_url", "coverArt", "artwork", "artworkUrl", "picture", "thumbnail")) {
            urlOf(playlist.opt(key))?.let { return it }
        }
        return null
    }

    /**
     * Re-reads a Spotify JSON export and, for playlists already imported (matched
     * by name), updates only their cover and creation date. Tracks are untouched.
     */
    fun refreshImportedPlaylistsFromJson(context: Context, uri: Uri) {
        if (_metadataRefresh.value.isRunning) return
        coroutineScope.launch(Dispatchers.IO) {
            _metadataRefresh.value = SpotifyMetadataRefreshState(isRunning = true, message = "Reading export…")
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: throw IllegalArgumentException("The selected file could not be opened.")
                val sourcePlaylists = parseSpotifyExport(json)

                // Group existing playlists by name. Prefer ones the JSON import created,
                // then oldest first, so duplicate names pair up in file order.
                val candidates = onlinePlaylistDao.getAllOnlinePlaylistsSync()
                    .sortedWith(compareBy<OnlinePlaylistEntity>({ it.description !in PLACEHOLDER_DESCRIPTIONS }, { it.id }))
                    .groupBy { normalizeName(it.name) }
                    .mapValues { (_, list) -> ArrayDeque(list) }
                val matched = sourcePlaylists.mapNotNull { source ->
                    candidates[normalizeName(source.name)]?.removeFirstOrNull()?.let { source to it }
                }
                if (matched.isEmpty()) {
                    throw IllegalArgumentException("None of the playlists in this file match your imported playlists by name.")
                }

                var token: String? = null
                var tokenTried = false
                var coversUpdated = 0
                var datesFound = 0
                // Index in file order -> creation time, null when unknown.
                val createdTimes = ArrayList<Long?>(matched.size)

                matched.forEachIndexed { index, (source, entity) ->
                    currentCoroutineContext().ensureActive()
                    _metadataRefresh.value = SpotifyMetadataRefreshState(
                        isRunning = true,
                        message = "Updating ${index + 1} of ${matched.size}: ${entity.name}"
                    )
                    val spotifyId = source.spotifyId
                        ?: SpotifyImportRepository.normalizePlaylistId(entity.spotifyUrl)

                    // Only a real playlist cover counts. A song thumbnail from the file is not
                    // one, so it's never written here (the list already falls back to song art).
                    val cover = source.coverUrl
                        ?: spotifyId?.let { spotifyRepo.fetchPlaylistCoverUrl(it) }
                    if (cover != null && cover != entity.artworkUrl) {
                        onlinePlaylistDao.updateOnlinePlaylistArtwork(entity.id, cover)
                        coversUpdated++
                    }
                    // Replace the old "Imported from Spotify" placeholder with the real description (or blank).
                    if (entity.description in PLACEHOLDER_DESCRIPTIONS) {
                        onlinePlaylistDao.updateOnlinePlaylistDescription(entity.id, source.description)
                    }
                    if (spotifyId != null && entity.spotifyUrl.isNullOrBlank()) {
                        onlinePlaylistDao.updateOnlinePlaylistSpotifyUrl(entity.id, "https://open.spotify.com/playlist/$spotifyId")
                    }

                    var created = source.createdAt
                    if (created == null && spotifyId != null) {
                        if (!tokenTried) {
                            tokenTried = true
                            token = spotifyRepo.obtainAnonymousToken()
                        }
                        created = token?.let { spotifyRepo.fetchPlaylistEarliestAddedAt(spotifyId, it) }
                    }
                    if (created != null) datesFound++
                    createdTimes.add(created)
                }

                // Playlists without a known date keep their place from the export,
                // which lists them in Spotify library order (newest first): each takes
                // a time just below the playlist above it.
                val now = System.currentTimeMillis()
                var previous = now
                val resolvedTimes = createdTimes.map { time ->
                    val resolved = time ?: (previous - 1)
                    previous = resolved
                    resolved
                }
                matched.forEachIndexed { index, (_, entity) ->
                    onlinePlaylistDao.updateOnlinePlaylistTimestamp(entity.id, resolvedTimes[index])
                }
                markDirty()

                val unmatched = sourcePlaylists.size - matched.size
                val orderNote = when {
                    datesFound == matched.size -> "ordered by Spotify creation date"
                    datesFound == 0 -> "ordered as listed in the export (it has no creation dates)"
                    else -> "ordered by creation date ($datesFound found; the rest keep their export position)"
                }
                _metadataRefresh.value = SpotifyMetadataRefreshState(
                    isRunning = false,
                    message = buildString {
                        append("Updated ${matched.size} playlists: $coversUpdated new covers, $orderNote.")
                        if (coversUpdated == 0 && matched.none { it.first.spotifyId != null || it.first.coverUrl != null }) {
                            append(" This file has no playlist links or cover images, so covers can't come from it; use \"Sign in to Spotify\" instead.")
                        }
                        if (unmatched > 0) append(" $unmatched playlists in the file weren't found in the app.")
                        append(" Sort playlists by Recent to see the Spotify order.")
                    }
                )
            } catch (e: CancellationException) {
                _metadataRefresh.value = SpotifyMetadataRefreshState()
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh Spotify playlist metadata", e)
                _metadataRefresh.value = SpotifyMetadataRefreshState(
                    isRunning = false,
                    message = e.message ?: "Couldn't read this Spotify export.",
                    isError = true
                )
            }
        }
    }

    private val _spotifyClientId = MutableStateFlow(prefs?.getString(PREF_CLIENT_ID, null).orEmpty())
    val spotifyClientId: StateFlow<String> = _spotifyClientId.asStateFlow()

    /**
     * Opens Spotify's sign-in page in the browser. The verifier and state are
     * kept in preferences because Android may stop the app while the browser is
     * open; [handleSpotifyLoginCallback] picks them up again.
     */
    fun startSpotifyLogin(context: Context, clientId: String) {
        val id = clientId.trim()
        if (id.isEmpty() || _metadataRefresh.value.isRunning) return
        val verifier = SpotifyAuth.newRandomString()
        val state = SpotifyAuth.newRandomString()
        prefs?.edit()
            ?.putString(PREF_CLIENT_ID, id)
            ?.putString(PREF_VERIFIER, verifier)
            ?.putString(PREF_STATE, state)
            ?.apply()
        _spotifyClientId.value = id
        try {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, SpotifyAuth.authorizeUri(id, verifier, state))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            _metadataRefresh.value = SpotifyMetadataRefreshState(message = "Finish signing in to Spotify in your browser, then come back here.")
        } catch (e: Exception) {
            _metadataRefresh.value = SpotifyMetadataRefreshState(message = "Couldn't open a browser for Spotify sign-in.", isError = true)
        }
    }

    /**
     * Finishes sign-in from the mueso://spotify-callback redirect, reads the
     * user's playlists and gives each imported playlist with the same name its
     * Spotify cover (and its Spotify link, when it has none). Tracks are untouched.
     */
    fun handleSpotifyLoginCallback(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        coroutineScope.launch(Dispatchers.IO) {
            _metadataRefresh.value = SpotifyMetadataRefreshState(isRunning = true, message = "Signing in to Spotify…")
            val result = try {
                val clientId = prefs?.getString(PREF_CLIENT_ID, null)
                val verifier = prefs?.getString(PREF_VERIFIER, null)
                val expectedState = prefs?.getString(PREF_STATE, null)
                uri.getQueryParameter("error")?.let { error ->
                    throw IllegalStateException(
                        if (error == "access_denied") "Spotify sign-in was cancelled." else "Spotify sign-in failed: $error"
                    )
                }
                val code = uri.getQueryParameter("code")
                if (code.isNullOrBlank() || clientId.isNullOrBlank() || verifier.isNullOrBlank() ||
                    expectedState == null || uri.getQueryParameter("state") != expectedState
                ) {
                    throw IllegalStateException("This Spotify sign-in link has expired. Tap the sign-in button again.")
                }
                // One use only.
                prefs?.edit()?.remove(PREF_VERIFIER)?.remove(PREF_STATE)?.apply()

                val token = SpotifyAuth.exchangeCode(clientId, code, verifier)
                val remote = SpotifyAuth.fetchMyPlaylists(token) { count ->
                    _metadataRefresh.value = SpotifyMetadataRefreshState(isRunning = true, message = "Reading your Spotify playlists… $count")
                }
                if (remote.isEmpty()) throw IllegalStateException("Your Spotify account has no playlists.")
                applySpotifyCovers(remote)
            } catch (e: CancellationException) {
                _metadataRefresh.value = SpotifyMetadataRefreshState()
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Spotify sign-in cover refresh failed", e)
                SpotifyMetadataRefreshState(message = e.message ?: "Spotify sign-in failed.", isError = true)
            }
            _metadataRefresh.value = result
            result.message?.let { message ->
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(appContext, message, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun applySpotifyCovers(remote: List<SpotifyAccountPlaylist>): SpotifyMetadataRefreshState {
        val byName = remote.groupBy { normalizeName(it.name) }.mapValues { (_, list) -> ArrayDeque(list) }
        val local = onlinePlaylistDao.getAllOnlinePlaylistsSync().sortedBy { it.id }

        var matched = 0
        var coversUpdated = 0
        local.forEach { entity ->
            currentCoroutineContext().ensureActive()
            val linkedId = SpotifyImportRepository.normalizePlaylistId(entity.spotifyUrl)
            // Prefer the playlist this one is already linked to, then the next with the same name.
            val source = remote.firstOrNull { it.id == linkedId }
                ?.also { hit -> byName[normalizeName(hit.name)]?.remove(hit) }
                ?: byName[normalizeName(entity.name)]?.removeFirstOrNull()
                ?: return@forEach
            matched++
            val cover = source.coverUrl
            if (cover != null && cover != entity.artworkUrl) {
                onlinePlaylistDao.updateOnlinePlaylistArtwork(entity.id, cover)
                coversUpdated++
            }
            if (entity.spotifyUrl.isNullOrBlank()) {
                onlinePlaylistDao.updateOnlinePlaylistSpotifyUrl(entity.id, "https://open.spotify.com/playlist/${source.id}")
            }
        }
        markDirty()

        return SpotifyMetadataRefreshState(
            message = buildString {
                append("Matched $matched of your ${local.size} playlists to Spotify by name: $coversUpdated covers updated.")
                val missing = local.size - matched
                if (missing > 0) append(" $missing have no Spotify playlist with the same name.")
            },
            isError = matched == 0
        )
    }

    fun clearMetadataRefreshMessage() {
        if (!_metadataRefresh.value.isRunning) _metadataRefresh.value = SpotifyMetadataRefreshState()
    }

    private fun normalizeName(name: String): String = name.trim().lowercase().replace(Regex("""\s+"""), " ")

    fun cancelBulkImport() {
        if (_importState.value != SpotifyImportState.BulkImporting) return
        importJob?.cancel()
    }

    fun retryMatch(index: Int, query: String) {
        coroutineScope.launch(Dispatchers.IO) {
            if (index !in _matchResults.value.indices) return@launch

            val cleanQuery = OnlineMusicRepository.cleanSearchQuery(query)
            Log.d(TAG, "Manual re-search for track $index with query: \"$cleanQuery\" (input: \"$query\")")

            _matchResults.value = _matchResults.value.toMutableList().also { list ->
                list[index] = list[index].copy(isSearching = true, showAlternatives = true)
            }

            try {
                val searchResults = onlineRepo.searchOnlineTracks(cleanQuery)
                val ranked = OnlineMusicRepository.rankTracks(searchResults, cleanQuery)
                _matchResults.value = _matchResults.value.toMutableList().also { list ->
                    list[index] = list[index].copy(
                        isSearching = false,
                        alternativeResults = ranked.take(10),
                        showAlternatives = true
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Manual search error", e)
                _matchResults.value = _matchResults.value.toMutableList().also { list ->
                    list[index] = list[index].copy(isSearching = false)
                }
            }
        }
    }

    fun selectMatch(trackIndex: Int, selectedTrack: TrackEntity) {
        if (trackIndex !in _matchResults.value.indices) return
        _matchResults.value = _matchResults.value.toMutableList().also { list ->
            list[trackIndex] = list[trackIndex].copy(
                matchedTrack = selectedTrack,
                showAlternatives = false,
                isSelected = true
            )
        }
        Log.d(TAG, "Manual match selected for track $trackIndex: \"${selectedTrack.title}\"")
    }

    fun toggleAlternatives(index: Int) {
        if (index !in _matchResults.value.indices) return
        _matchResults.value = _matchResults.value.toMutableList().also { list ->
            list[index] = list[index].copy(showAlternatives = !list[index].showAlternatives)
        }
    }

    fun toggleTrackSelection(index: Int) {
        val current = _matchResults.value.toMutableList()
        if (index in current.indices) {
            val item = current[index]
            if (item.matchedTrack != null) {
                current[index] = item.copy(isSelected = !item.isSelected)
                _matchResults.value = current
            }
        }
    }

    fun selectAllTracks(selected: Boolean) {
        val current = _matchResults.value.map { item ->
            if (item.matchedTrack != null) item.copy(isSelected = selected) else item.copy(isSelected = false)
        }
        _matchResults.value = current
    }

    fun createPlaylist() {
        val playlistData = _spotifyPlaylistData.value ?: return
        val matches = _matchResults.value

        coroutineScope.launch(Dispatchers.IO) {
            _importState.value = SpotifyImportState.Creating
            _createdDestination.value = "online"

            try {
                val playlistId = onlinePlaylistDao.insertOnlinePlaylist(
                    OnlinePlaylistEntity(
                        name = playlistData.name,
                        artworkUrl = playlistData.artworkUrl,
                        description = playlistData.description?.takeIf { it.isNotBlank() },
                        spotifyUrl = currentSourceUrl
                    )
                )

                var orderIndex = 0
                for (match in matches) {
                    if (!match.isSelected) continue
                    val track = match.matchedTrack ?: continue
                    onlinePlaylistDao.insertOnlineTrack(
                        OnlinePlaylistTrackEntity(
                            onlinePlaylistId = playlistId,
                            trackId = track.id,
                            title = track.title,
                            artist = track.artist,
                            artworkUrl = track.artworkUrl,
                            filePath = track.filePath,
                            duration = track.duration,
                            orderIndex = orderIndex++
                        )
                    )
                }

                markDirty()
                _importState.value = SpotifyImportState.Done
                Log.d(TAG, "Created online playlist \"${playlistData.name}\" with $orderIndex tracks (ID: $playlistId)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create playlist", e)
                _errorMessage.value = "Failed to create playlist: ${e.message}"
                _importState.value = SpotifyImportState.Error
            }
        }
    }

    fun syncPlaylist(
        context: Context,
        playlistId: Long,
        playlistName: String,
        spotifyUrl: String,
        onChecking: () -> Unit = {},
        onNoNewTracks: () -> Unit = {},
        onNewTracksFound: (Int) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        lastContext = context.applicationContext
        importJob?.cancel()
        currentSourceUrl = spotifyUrl
        syncTargetPlaylistId = playlistId
        _syncTargetPlaylistName.value = playlistName

        importJob = coroutineScope.launch(Dispatchers.IO) {
            _importState.value = SpotifyImportState.FetchingSpotify
            _errorMessage.value = null
            _matchResults.value = emptyList()
            _matchProgress.value = 0

            withContext(Dispatchers.Main) { onChecking() }
            Log.d(TAG, "Syncing playlist \"$playlistName\" (ID: $playlistId) from Spotify: $spotifyUrl")

            val result = spotifyRepo.fetchPlaylist(spotifyUrl)
            if (result.isFailure) {
                val error = result.exceptionOrNull()?.message ?: "Failed to fetch Spotify playlist"
                Log.e(TAG, "Sync Spotify fetch failed: $error")
                _errorMessage.value = error
                _importState.value = SpotifyImportState.Error
                withContext(Dispatchers.Main) { onError(error) }
                return@launch
            }

            val fullPlaylistData = result.getOrNull()!!
            val existingTracks = onlinePlaylistDao.getOnlinePlaylistTracksSync(playlistId)
            val newTracks = fullPlaylistData.tracks.filter { track ->
                val present = isTrackAlreadyPresent(track, existingTracks)
                if (present) {
                    Log.d(TAG, "  ✓ Skipped already in playlist: \"${track.title}\" - ${track.artist}")
                } else {
                    Log.d(TAG, "  + Found newly added track: \"${track.title}\" - ${track.artist}")
                }
                !present
            }

            Log.d(TAG, "Sync delta: found ${newTracks.size} new track(s) out of ${fullPlaylistData.tracks.size} (existing: ${existingTracks.size})")

            if (newTracks.isEmpty()) {
                _importState.value = SpotifyImportState.Idle
                syncTargetPlaylistId = null
                _syncTargetPlaylistName.value = null
                withContext(Dispatchers.Main) { onNoNewTracks() }
                return@launch
            }

            // New tracks discovered
            withContext(Dispatchers.Main) { onNewTracksFound(newTracks.size) }

            _spotifyPlaylistData.value = fullPlaylistData.copy(tracks = newTracks)
            _matchResults.value = newTracks.map { TrackMatchResult(spotifyTrack = it, isSelected = false) }
            _importState.value = SpotifyImportState.MatchingTracks

            for (i in newTracks.indices) {
                val spotifyTrack = newTracks[i]
                val cleanTitle = OnlineMusicRepository.cleanTitleForLyrics(spotifyTrack.title)
                val primaryArtist = OnlineMusicRepository.getPrimaryArtist(spotifyTrack.artist)
                val query = OnlineMusicRepository.cleanSearchQuery(cleanTitle, primaryArtist)

                Log.d(TAG, "Matching sync track [${i + 1}/${newTracks.size}]: \"$query\" (raw: \"${spotifyTrack.title}\" - \"${spotifyTrack.artist}\")")

                _matchResults.value = _matchResults.value.toMutableList().also { list ->
                    list[i] = list[i].copy(isSearching = true)
                }
                _matchProgress.value = i
                NotificationHelper.showImportProgress(context.applicationContext, "Sync: $playlistName", i, newTracks.size)

                try {
                    val searchResults = onlineRepo.searchOnlineTracks(query)
                    val ranked = OnlineMusicRepository.rankTracks(searchResults, query)
                    val bestMatch = OnlineMusicRepository.findBestTrackMatch(
                        candidates = ranked,
                        targetTitle = cleanTitle,
                        targetArtist = primaryArtist
                    )

                    _matchResults.value = _matchResults.value.toMutableList().also { list ->
                        list[i] = list[i].copy(
                            matchedTrack = bestMatch,
                            isSearching = false,
                            alternativeResults = ranked.take(8),
                            isSelected = (bestMatch != null)
                        )
                    }

                    if (bestMatch != null) {
                        Log.d(TAG, "  ✓ Matched sync track: \"${bestMatch.title}\" by ${bestMatch.artist}")
                    } else {
                        Log.d(TAG, "  ✗ No match found for sync track \"$query\"")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Search error for \"$query\"", e)
                    _matchResults.value = _matchResults.value.toMutableList().also { list ->
                        list[i] = list[i].copy(isSearching = false)
                    }
                }
            }

            _matchProgress.value = newTracks.size
            _importState.value = SpotifyImportState.Ready
            val matchedCount = _matchResults.value.count { it.matchedTrack != null }
            NotificationHelper.showImportComplete(context.applicationContext, "Sync: $playlistName", matchedCount, newTracks.size)
        }
    }

    fun appendSyncTracksToPlaylist(onComplete: (Int) -> Unit = {}) {
        val playlistId = syncTargetPlaylistId ?: return
        val matches = _matchResults.value
        val validTracks = matches.filter { it.isSelected }.mapNotNull { it.matchedTrack }
        if (validTracks.isEmpty()) return

        coroutineScope.launch(Dispatchers.IO) {
            _importState.value = SpotifyImportState.Creating
            _createdDestination.value = "online"

            try {
                val existingTracks = onlinePlaylistDao.getOnlinePlaylistTracksSync(playlistId)
                val maxOrder = existingTracks.maxOfOrNull { it.orderIndex } ?: -1
                var orderIndex = maxOrder + 1
                var addedCount = 0

                for (track in validTracks) {
                    onlinePlaylistDao.insertOnlineTrack(
                        OnlinePlaylistTrackEntity(
                            onlinePlaylistId = playlistId,
                            trackId = track.id,
                            title = track.title,
                            artist = track.artist,
                            artworkUrl = track.artworkUrl,
                            filePath = track.filePath,
                            duration = track.duration,
                            orderIndex = orderIndex++
                        )
                    )
                    addedCount++
                }

                onlinePlaylistDao.updateOnlinePlaylistTimestamp(playlistId)
                markDirty()
                _importState.value = SpotifyImportState.Done
                Log.d(TAG, "Appended $addedCount sync tracks to playlist ID: $playlistId")
                withContext(Dispatchers.Main) { onComplete(addedCount) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to append sync tracks", e)
                _errorMessage.value = "Failed to add tracks: ${e.message}"
                _importState.value = SpotifyImportState.Error
            }
        }
    }

    fun createYouTubePlaylist(onComplete: (Boolean) -> Unit = {}) {
        val playlistData = _spotifyPlaylistData.value ?: return
        val matches = _matchResults.value

        coroutineScope.launch(Dispatchers.IO) {
            _importState.value = SpotifyImportState.Creating
            _createdDestination.value = "youtube"

            try {
                if (!onlineRepo.innerTube.isLoggedIn()) {
                    _errorMessage.value = "Please sign in to YouTube Music first"
                    _importState.value = SpotifyImportState.Error
                    withContext(Dispatchers.Main) { onComplete(false) }
                    return@launch
                }

                val newPlaylistId = onlineRepo.innerTube.createPlaylist(
                    title = playlistData.name,
                    description = playlistData.description.orEmpty()
                )

                if (newPlaylistId == null) {
                    _errorMessage.value = "Failed to create playlist on YouTube Music"
                    _importState.value = SpotifyImportState.Error
                    withContext(Dispatchers.Main) { onComplete(false) }
                    return@launch
                }

                var addedCount = 0
                val matchedTracks = matches.filter { it.isSelected }.mapNotNull { it.matchedTrack }
                for (track in matchedTracks) {
                    var videoId = onlineRepo.extractVideoId(track)
                    if (videoId.isBlank()) {
                        val search = onlineRepo.innerTube.search("${track.title} ${track.artist}", "Eg-KAQwIABAAGAEgASgB")
                        videoId = search.firstOrNull()?.videoId ?: ""
                    }
                    if (videoId.isNotBlank()) {
                        val ok = onlineRepo.innerTube.addTrackToPlaylist(newPlaylistId, videoId)
                        if (ok) addedCount++
                    }
                }

                onYouTubeRefresh?.invoke()
                _importState.value = SpotifyImportState.Done
                Log.d(TAG, "Created YouTube Music playlist \"${playlistData.name}\" ($newPlaylistId) with $addedCount tracks")
                withContext(Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create YouTube Music playlist", e)
                _errorMessage.value = "Failed to create YouTube playlist: ${e.message}"
                _importState.value = SpotifyImportState.Error
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    fun reset() {
        importJob?.cancel()
        lastContext?.let { NotificationHelper.dismissImportNotification(it) }
        _importState.value = SpotifyImportState.Idle
        _spotifyPlaylistData.value = null
        _matchResults.value = emptyList()
        _matchProgress.value = 0
        _errorMessage.value = null
        _bulkImportProgress.value = BulkPlaylistImportProgress()
        syncTargetPlaylistId = null
        _syncTargetPlaylistName.value = null
    }
}
