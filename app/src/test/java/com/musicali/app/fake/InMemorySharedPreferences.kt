package com.musicali.app.fake

import android.content.SharedPreferences
import java.util.HashMap

/**
 * Minimal in-memory SharedPreferences for unit tests.
 * Avoids Robolectric and Android Keystore requirements.
 * Shared version of the private class in TokenStoreTest.
 */
class InMemorySharedPreferences : SharedPreferences {
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
