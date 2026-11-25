package com.pappoe34.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pappoe34.launcher.model.LauncherSettings
import com.pappoe34.launcher.model.SlotId
import com.pappoe34.launcher.model.WheelTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher")

/** Persists wheel slot assignments and user settings. */
class LauncherPreferences(context: Context) {

    private val store = context.applicationContext.launcherDataStore

    val settings: Flow<LauncherSettings> = store.data.map { prefs -> prefs.toSettings() }

    suspend fun assign(slot: SlotId, appKey: String?) {
        store.edit { prefs ->
            val entries = prefs[Keys.SLOTS].orEmpty().filterNot { it.startsWith(slot.encode() + SEPARATOR) }.toMutableSet()
            if (appKey != null) entries += slot.encode() + SEPARATOR + appKey
            prefs[Keys.SLOTS] = entries
        }
    }

    suspend fun replaceSlots(slots: Map<SlotId, String>, markSeeded: Boolean) {
        store.edit { prefs ->
            prefs[Keys.SLOTS] = slots.entries.map { (slot, key) -> slot.encode() + SEPARATOR + key }.toSet()
            if (markSeeded) prefs[Keys.SEEDED] = true
        }
    }

    suspend fun removeApp(appKey: String) {
        store.edit { prefs ->
            prefs[Keys.SLOTS] = prefs[Keys.SLOTS].orEmpty().filterNot { it.endsWith(SEPARATOR + appKey) }.toSet()
        }
    }

    suspend fun setTheme(theme: WheelTheme) = store.edit { it[Keys.THEME] = theme.name }
    suspend fun setHaptics(enabled: Boolean) = store.edit { it[Keys.HAPTICS] = enabled }
    suspend fun setSound(enabled: Boolean) = store.edit { it[Keys.SOUND] = enabled }
    suspend fun setBlur(enabled: Boolean) = store.edit { it[Keys.BLUR] = enabled }

    private fun Preferences.toSettings(): LauncherSettings {
        val slots = this[Keys.SLOTS].orEmpty().mapNotNull { entry ->
            val sep = entry.indexOf(SEPARATOR)
            if (sep <= 0) return@mapNotNull null
            val slot = SlotId.decode(entry.substring(0, sep)) ?: return@mapNotNull null
            slot to entry.substring(sep + 1)
        }.toMap()
        val theme = this[Keys.THEME]?.let { name -> WheelTheme.entries.firstOrNull { it.name == name } }
        return LauncherSettings(
            slots = slots,
            theme = theme ?: WheelTheme.METALLIC,
            haptics = this[Keys.HAPTICS] ?: true,
            sound = this[Keys.SOUND] ?: false,
            blur = this[Keys.BLUR] ?: true,
            seeded = this[Keys.SEEDED] ?: false,
        )
    }

    private object Keys {
        val SLOTS = stringSetPreferencesKey("slots")
        val THEME = stringPreferencesKey("theme")
        val HAPTICS = booleanPreferencesKey("haptics")
        val SOUND = booleanPreferencesKey("sound")
        val BLUR = booleanPreferencesKey("blur")
        val SEEDED = booleanPreferencesKey("seeded")
    }

    private companion object {
        const val SEPARATOR = '|'
    }
}
