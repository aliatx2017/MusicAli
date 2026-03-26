package com.musicali.app.auth

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.HashMap

/**
 * Unit tests for TokenStore key persistence and expiry logic.
 * Uses in-memory InMemorySharedPreferences to avoid Keystore/Robolectric dependency.
 * The clock lambda makes System.currentTimeMillis() injectable for deterministic tests.
 * Covers AUTH-02 (token persistence) and AUTH-03 (expiry detection for proactive refresh).
 */
class TokenStoreTest {

    private var fakeNowMs: Long = 1_000_000L
    private lateinit var store: TokenStore

    @Before
    fun setup() {
        store = TokenStore(
            prefs = InMemorySharedPreferences(),
            clock = { fakeNowMs }
        )
    }

    // --- Expiry logic ---

    @Test
    fun `isTokenExpired returns true when no token stored (expiry is 0)`() {
        assertTrue(store.isTokenExpired())
    }

    @Test
    fun `isTokenExpired returns false when token expires well in future`() {
        // Token expires 5 minutes from now — outside 60s grace window
        val expiryMs = fakeNowMs + 5 * 60 * 1000L
        store.saveTokens("access", "refresh", expiryMs)
        assertFalse(store.isTokenExpired(graceWindowMs = 60_000L))
    }

    @Test
    fun `isTokenExpired returns true when token expires within grace window`() {
        // Token expires in 30s — inside 60s grace window
        val expiryMs = fakeNowMs + 30_000L
        store.saveTokens("access", "refresh", expiryMs)
        assertTrue(store.isTokenExpired(graceWindowMs = 60_000L))
    }

    @Test
    fun `isTokenExpired returns true when token already expired`() {
        // Token expired 10s ago
        val expiryMs = fakeNowMs - 10_000L
        store.saveTokens("access", "refresh", expiryMs)
        assertTrue(store.isTokenExpired(graceWindowMs = 60_000L))
    }

    @Test
    fun `isTokenExpired with graceWindowMs=0 is false when token valid`() {
        // Token expires exactly at now + 1ms — no grace window
        val expiryMs = fakeNowMs + 1L
        store.saveTokens("access", "refresh", expiryMs)
        assertFalse(store.isTokenExpired(graceWindowMs = 0L))
    }

    // --- Key persistence ---

    @Test
    fun `saveTokens persists access token readable via getAccessToken`() {
        store.saveTokens("my-access-token", "my-refresh-token", fakeNowMs + 3600_000L)
        assertEquals("my-access-token", store.getAccessToken())
    }

    @Test
    fun `getAccessToken returns null before any tokens saved`() {
        assertNull(store.getAccessToken())
    }

    @Test
    fun `savePlaylistId and getPlaylistId round-trip`() {
        store.savePlaylistId("PL-abc-123")
        assertEquals("PL-abc-123", store.getPlaylistId())
    }

    @Test
    fun `getPlaylistId returns null before any playlist saved`() {
        assertNull(store.getPlaylistId())
    }

    @Test
    fun `clearAll removes all keys`() {
        store.saveTokens("access", "refresh", fakeNowMs + 3600_000L)
        store.savePlaylistId("PL-abc")
        store.clearAll()
        assertNull(store.getAccessToken())
        assertNull(store.getPlaylistId())
        assertTrue(store.isTokenExpired())
    }
}

/**
 * Minimal in-memory SharedPreferences for unit tests.
 * Avoids Robolectric and Android Keystore requirements.
 */
private class InMemorySharedPreferences : SharedPreferences {
    private val map = HashMap<String, Any?>()
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): Map<String, *> = map
    override fun getString(key: String, defValue: String?): String? =
        (map[key] as? String) ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        (map[key] as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: defValues
    override fun getInt(key: String, defValue: Int): Int =
        (map[key] as? Int) ?: defValue
    override fun getLong(key: String, defValue: Long): Long =
        (map[key] as? Long) ?: defValue
    override fun getFloat(key: String, defValue: Float): Float =
        (map[key] as? Float) ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        (map[key] as? Boolean) ?: defValue
    override fun contains(key: String): Boolean = map.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor(map, listeners, this)
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) { listeners.add(listener) }
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) { listeners.remove(listener) }

    private class Editor(
        private val map: HashMap<String, Any?>,
        private val listeners: List<SharedPreferences.OnSharedPreferenceChangeListener>,
        private val prefs: SharedPreferences
    ) : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>()
        private var clearPending = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { pending[key] = null }
        override fun clear() = apply { clearPending = true }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearPending) { map.clear(); clearPending = false }
            pending.forEach { (k, v) ->
                if (v == null) map.remove(k) else map[k] = v
            }
            pending.clear()
        }
    }
}
