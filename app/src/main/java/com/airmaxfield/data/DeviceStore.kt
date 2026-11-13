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

    private companion object { const val KEY = "list" }
}

/** Мелкие настройки приложения (не секретные). */
class AppPrefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
    var lightTheme: Boolean
        get() = p.getBoolean("light", false)
        set(v) { p.edit().putBoolean("light", v).apply() }
}
