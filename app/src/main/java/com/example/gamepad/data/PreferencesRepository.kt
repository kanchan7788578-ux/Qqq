package com.example.gamepad.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gamepad.model.AppLanguage
import com.example.gamepad.model.ButtonSymbolStyle
import com.example.gamepad.model.ControllerLayout
import com.example.gamepad.model.DiscoveredTv
import com.example.gamepad.model.PollingRate
import com.example.gamepad.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pulsepad_settings")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val POLLING_RATE = stringPreferencesKey("polling_rate")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val DEADZONE = floatPreferencesKey("deadzone")
        val LAYOUT = stringPreferencesKey("layout")
        val SYMBOL_STYLE = stringPreferencesKey("symbol_style")
        val AUTO_LANDSCAPE = booleanPreferencesKey("auto_landscape")
        val SAVED_TVS = stringPreferencesKey("saved_tvs")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            language = prefs[Keys.LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.BN,
            pollingRate = prefs[Keys.POLLING_RATE]?.let { runCatching { PollingRate.valueOf(it) }.getOrNull() }
                ?: PollingRate.HZ_120,
            hapticsEnabled = prefs[Keys.HAPTICS] ?: true,
            deadzone = (prefs[Keys.DEADZONE] ?: 0.08f).coerceIn(0.02f, 0.30f),
            layout = prefs[Keys.LAYOUT]?.let { runCatching { ControllerLayout.valueOf(it) }.getOrNull() }
                ?: ControllerLayout.STANDARD,
            symbolStyle = prefs[Keys.SYMBOL_STYLE]?.let { runCatching { ButtonSymbolStyle.valueOf(it) }.getOrNull() }
                ?: ButtonSymbolStyle.XBOX_ABXY,
            autoLandscape = prefs[Keys.AUTO_LANDSCAPE] ?: true,
            savedTvs = decodeSavedTvs(prefs[Keys.SAVED_TVS].orEmpty())
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setPollingRate(rate: PollingRate) {
        context.dataStore.edit { it[Keys.POLLING_RATE] = rate.name }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTICS] = enabled }
    }

    suspend fun setDeadzone(deadzone: Float) {
        context.dataStore.edit { it[Keys.DEADZONE] = deadzone.coerceIn(0.02f, 0.30f) }
    }

    suspend fun setLayout(layout: ControllerLayout) {
        context.dataStore.edit { it[Keys.LAYOUT] = layout.name }
    }

    suspend fun setSymbolStyle(style: ButtonSymbolStyle) {
        context.dataStore.edit { it[Keys.SYMBOL_STYLE] = style.name }
    }

    suspend fun setAutoLandscape(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_LANDSCAPE] = enabled }
    }

    suspend fun saveTv(tv: DiscoveredTv) {
        if (tv.host == "127.0.0.1") return
        context.dataStore.edit { prefs ->
            val existing = decodeSavedTvs(prefs[Keys.SAVED_TVS].orEmpty())
                .filterNot { it.host == tv.host && it.port == tv.port }
            val updated = (listOf(tv.copy(isSaved = true)) + existing).take(8)
            prefs[Keys.SAVED_TVS] = encodeSavedTvs(updated)
        }
    }

    suspend fun removeSavedTv(tv: DiscoveredTv) {
        context.dataStore.edit { prefs ->
            val updated = decodeSavedTvs(prefs[Keys.SAVED_TVS].orEmpty())
                .filterNot { it.host == tv.host && it.port == tv.port }
            prefs[Keys.SAVED_TVS] = encodeSavedTvs(updated)
        }
    }

    private fun encodeSavedTvs(list: List<DiscoveredTv>): String {
        return list.joinToString("\n") { tv ->
            val safeName = tv.name.replace("|", " ").replace("\n", " ").trim()
            val safeTransport = tv.transport.replace("|", " ").replace("\n", " ").trim()
            "${tv.host}|${tv.port}|$safeName|$safeTransport"
        }
    }

    private fun decodeSavedTvs(raw: String): List<DiscoveredTv> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size < 3) return@mapNotNull null
                val host = parts[0].trim()
                val port = parts[1].trim().toIntOrNull() ?: return@mapNotNull null
                val name = parts[2].trim().ifBlank { "Smart TV" }
                val transport = parts.getOrNull(3)?.trim().orEmpty().ifBlank { "Saved" }
                DiscoveredTv(
                    id = "$host:$port",
                    name = name,
                    host = host,
                    port = port,
                    transport = transport,
                    isSaved = true
                )
            }.toList()
    }
}
