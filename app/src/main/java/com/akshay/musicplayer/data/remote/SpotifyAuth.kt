package com.akshay.musicplayer.data.remote

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Spotify sign-in with the Authorization Code + PKCE flow, which needs only the
 * Client ID of the user's own Spotify developer app (no client secret), and a
 * reader for the signed-in user's playlists.
 */
object SpotifyAuth {
    const val REDIRECT_URI = "mueso://spotify-callback"
    private const val SCOPES = "playlist-read-private playlist-read-collaborative"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val random = SecureRandom()

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    fun newRandomString(): String = base64Url(ByteArray(48).also { random.nextBytes(it) })

    fun authorizeUri(clientId: String, codeVerifier: String, state: String): Uri {
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII)))
        return Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", SCOPES)
            .build()
    }

    fun isCallback(uri: Uri?): Boolean =
        uri != null && uri.scheme == "mueso" && uri.host == "spotify-callback"

    /** Trades the code from the callback for an access token. Throws with a readable message on failure. */
    suspend fun exchangeCode(clientId: String, code: String, codeVerifier: String): String = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("client_id", clientId)
            .add("code_verifier", codeVerifier)
            .build()
        val request = Request.Builder().url("https://accounts.spotify.com/api/token").post(body).build()
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (!response.isSuccessful) {
                val reason = json?.optString("error_description")?.takeIf { it.isNotBlank() }
                    ?: json?.optString("error")?.takeIf { it.isNotBlank() }
                    ?: "HTTP ${response.code}"
                throw IllegalStateException("Spotify sign-in failed: $reason")
            }
            json?.optString("access_token")?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Spotify sign-in failed: no access token in the reply.")
        }
    }

    private fun getJson(url: String, token: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .get()
            .build()
        return httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val reason = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }
                    .getOrNull()?.takeIf { it.isNotBlank() } ?: "HTTP ${response.code}"
                throw IllegalStateException("Spotify refused the playlist request: $reason")
            }
            JSONObject(text)
        }
    }

    /**
     * Every playlist in the signed-in user's library (their own, private ones
     * included, plus followed ones), with the user's own playlists first so they
     * win name matches against followed playlists of the same name.
     */
    suspend fun fetchMyPlaylists(token: String, onProgress: (Int) -> Unit = {}): List<SpotifyAccountPlaylist> =
        withContext(Dispatchers.IO) {
            val myId = runCatching { getJson("https://api.spotify.com/v1/me", token).optString("id") }.getOrNull()
            val result = mutableListOf<Pair<SpotifyAccountPlaylist, Boolean>>()
            var next: String? = "https://api.spotify.com/v1/me/playlists?limit=50&offset=0"
            while (next != null) {
                val page = getJson(next, token)
                val items = page.optJSONArray("items") ?: break
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    // Spotify lists the largest image first.
                    val cover = item.optJSONArray("images")?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() }
                    val owned = myId != null && item.optJSONObject("owner")?.optString("id") == myId
                    result += SpotifyAccountPlaylist(id, item.optString("name"), cover) to owned
                }
                onProgress(result.size)
                next = page.optString("next").takeIf { it.startsWith("http") }
            }
            result.sortedByDescending { it.second }.map { it.first }
        }
}
