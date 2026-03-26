package com.musicali.app.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefsKeys {
    const val ACCESS_TOKEN = "access_token"
    const val REFRESH_TOKEN = "refresh_token"
    const val TOKEN_EXPIRY_MS = "token_expiry_ms"
    const val PLAYLIST_ID = "playlist_id"
}

private const val PREFS_FILE_NAME = "musicali_secure_prefs"

class TokenStore internal constructor(
    private val prefs: SharedPreferences,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    companion object {
        fun create(context: Context): TokenStore {
            val prefs = try {
                buildPrefs(context)
            } catch (e: Exception) {
                // Stale Keystore entry — delete and recreate (per Pitfall 7)
                context.deleteSharedPreferences(PREFS_FILE_NAME)
                buildPrefs(context)
            }
            return TokenStore(prefs)
        }

        private fun buildPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    fun saveTokens(accessToken: String, refreshToken: String, expiryMs: Long) {
        prefs.edit()
            .putString(SecurePrefsKeys.ACCESS_TOKEN, accessToken)
            .putString(SecurePrefsKeys.REFRESH_TOKEN, refreshToken)
            .putLong(SecurePrefsKeys.TOKEN_EXPIRY_MS, expiryMs)
            .apply()
    }

    fun getAccessToken(): String? = prefs.getString(SecurePrefsKeys.ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = prefs.getString(SecurePrefsKeys.REFRESH_TOKEN, null)
    fun getExpiryMs(): Long = prefs.getLong(SecurePrefsKeys.TOKEN_EXPIRY_MS, 0L)

    fun isTokenExpired(graceWindowMs: Long = 60_000L): Boolean {
        val expiryMs = getExpiryMs()
        if (expiryMs == 0L) return true
        return clock() >= expiryMs - graceWindowMs
    }

    fun getPlaylistId(): String? = prefs.getString(SecurePrefsKeys.PLAYLIST_ID, null)

    fun savePlaylistId(id: String) {
        prefs.edit().putString(SecurePrefsKeys.PLAYLIST_ID, id).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
