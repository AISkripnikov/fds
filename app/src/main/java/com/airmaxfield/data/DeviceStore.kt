package com.airmaxfield.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SavedDevice(
    val id: String,
    val name: String,
    val host: String,
    val user: String,
    val pass: String,
    /** Общий id у двух сторон одного моста (A↔B). */
    val pairId: String? = null,
)

/** Сохранённые пушки. Пароли — только в EncryptedSharedPreferences. */
class DeviceStore(ctx: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        ctx,
        "devices",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized fun all(): List<SavedDevice> =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString<List<SavedDevice>>(it) }.getOrNull() }
            .orEmpty()

    @Synchronized fun save(d: SavedDevice) {
        val list = all().filterNot { it.id == d.id || it.host == d.host } + d
        prefs.edit().putString(KEY, json.encodeToString(list)).apply()
    }

    @Synchronized fun delete(id: String) {
        prefs.edit().putString(KEY, json.encodeToString(all().filterNot { it.id == id })).apply()
    }

    /** Шаблоны мостов (в них ключ WPA2 и пароль — поэтому тоже в зашифрованном хранилище). */
    @Synchronized fun profiles(): List<com.airmaxfield.apply.BridgeProfile> =
        prefs.getString(PROFILES, null)
            ?.let { runCatching { json.decodeFromString<List<com.airmaxfield.apply.BridgeProfile>>(it) }.getOrNull() }
            .orEmpty()

    @Synchronized fun saveProfile(p: com.airmaxfield.apply.BridgeProfile) {
        val list = profiles().filterNot { it.name == p.name } + p
        prefs.edit().putString(PROFILES, json.encodeToString(list)).apply()
    }

    @Synchronized fun deleteProfile(name: String) {
        prefs.edit().putString(PROFILES, json.encodeToString(profiles().filterNot { it.name == name })).apply()
    }

    private companion object { const val KEY = "list"; const val PROFILES = "bridge_profiles" }
}

/** Мелкие настройки приложения (не секретные). */
class AppPrefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
    var lightTheme: Boolean
        get() = p.getBoolean("light", false)
        set(v) { p.edit().putBoolean("light", v).apply() }
}
