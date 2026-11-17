package com.akshay.musicplayer

import com.akshay.musicplayer.data.profiles.PlaylistKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LightProfilePlaylistKeyTest {
    @Test
    fun localAndOnlinePlaylistsSurviveARoundTrip() {
        val keys = listOf(PlaylistKey(isOnline = false, id = 7), PlaylistKey(isOnline = true, id = 7))
        assertEquals(keys, keys.map { PlaylistKey.decode(it.encode()) })
    }

    @Test
    fun malformedKeysAreIgnored() {
        assertNull(PlaylistKey.decode("youtube:3"))
        assertNull(PlaylistKey.decode("local:abc"))
        assertNull(PlaylistKey.decode("7"))
    }
}
