package com.foldpod.app.library

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class SpotifyTokens(val access: String, val refresh: String, val expiresAt: Long,
    val scopes: Set<String> = emptySet())

/** Only AES-GCM ciphertext is persisted; key material stays in Android Keystore. */
internal class SpotifyTokenStore(context: Context) {
    private val preferences = context.getSharedPreferences("spotify_library_tokens", Context.MODE_PRIVATE)
    private val alias = "foldpod.spotify.library.v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }

    fun read(): SpotifyTokens? {
        val encrypted = preferences.getString("ciphertext", null) ?: return null
        return try {
            val iv = Base64.decode(preferences.getString("iv", null) ?: error("Missing IV"), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val data = JSONObject(String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8))
            SpotifyTokens(data.getString("access"), data.getString("refresh"), data.getLong("expiresAt"),
                SpotifyPlaybackPolicy.grantedScopes(if (data.has("scope")) data.optString("scope") else null))
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun write(tokens: SpotifyTokens) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val json = JSONObject().put("access", tokens.access).put("refresh", tokens.refresh)
            .put("expiresAt", tokens.expiresAt).put("scope", tokens.scopes.joinToString(" ")).toString()
        check(preferences.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("ciphertext", Base64.encodeToString(cipher.doFinal(json.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
            .commit()) { "Cannot save Spotify connection" }
    }

    fun clear() { preferences.edit().clear().apply() }
}
