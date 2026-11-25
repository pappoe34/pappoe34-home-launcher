package com.pappoe34.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pappoe34.launcher.model.LauncherSettings
import com.pappoe34.launcher.model.PlacedWidget
import com.pappoe34.launcher.model.Ring
import com.pappoe34.launcher.model.SlotId
import com.pappoe34.launcher.model.WheelTheme
import com.pappoe34.launcher.model.WidgetPage
import com.pappoe34.launcher.model.WidgetSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher")

/** Persists wheel slot assignments, widget pages and user settings. */
class LauncherPreferences(context: Context) {

    private val store = context.applicationContext.launcherDataStore

    val settings: Flow<LauncherSettings> = store.data.map { prefs -> prefs.toSettings() }

    // ---- Wheel slots -------------------------------------------------------

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

    suspend fun setGroupName(ring: Ring, name: String) = store.edit { it[groupKey(ring)] = name.trim() }

    suspend fun setSelected(ring: Ring, slot: Int) = store.edit { it[selectedKey(ring)] = Math.floorMod(slot, ring.slotCount) }

    // ---- Widget pages ------------------------------------------------------

    suspend fun addPage(): String {
        val id = UUID.randomUUID().toString()
        editPages { pages -> pages + WidgetPage(id) }
        return id
    }

    suspend fun removePage(pageId: String) = editPages { pages -> pages.filterNot { it.id == pageId } }

    suspend fun placeWidget(pageId: String, widget: PlacedWidget) = editPages { pages ->
        pages.map { page -> if (page.id == pageId) page.copy(widgets = page.widgets + widget) else page }
    }

    suspend fun removeWidget(appWidgetId: Int) = editPages { pages ->
        pages.map { page -> page.copy(widgets = page.widgets.filterNot { it.appWidgetId == appWidgetId }) }
    }

    suspend fun resizeWidget(appWidgetId: Int, size: WidgetSize) = editPages { pages ->
        pages.map { page ->
            page.copy(widgets = page.widgets.map { if (it.appWidgetId == appWidgetId) it.copy(size = size) else it })
        }
    }

    private suspend fun editPages(transform: (List<WidgetPage>) -> List<WidgetPage>) {
        store.edit { prefs -> prefs[Keys.PAGES] = encodePages(transform(decodePages(prefs[Keys.PAGES]))) }
    }

    // ---- Toggles -----------------------------------------------------------

    suspend fun setTheme(theme: WheelTheme) = store.edit { it[Keys.THEME] = theme.name }
    suspend fun setDepth3d(enabled: Boolean) = store.edit { it[Keys.DEPTH] = enabled }
    suspend fun setHaptics(enabled: Boolean) = store.edit { it[Keys.HAPTICS] = enabled }
    suspend fun setSound(enabled: Boolean) = store.edit { it[Keys.SOUND] = enabled }
    suspend fun setBlur(enabled: Boolean) = store.edit { it[Keys.BLUR] = enabled }

    // ---- Mapping -----------------------------------------------------------

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
            groupNames = Ring.entries.associateWith { ring -> this[groupKey(ring)] ?: ring.defaultGroupName },
            selected = Ring.entries.associateWith { ring -> this[selectedKey(ring)] ?: 0 },
            pages = decodePages(this[Keys.PAGES]),
            theme = theme ?: WheelTheme.METALLIC,
            depth3d = this[Keys.DEPTH] ?: true,
            haptics = this[Keys.HAPTICS] ?: true,
            sound = this[Keys.SOUND] ?: false,
            blur = this[Keys.BLUR] ?: true,
            seeded = this[Keys.SEEDED] ?: false,
        )
    }

    private fun groupKey(ring: Ring) = stringPreferencesKey("group_" + ring.name.lowercase())
    private fun selectedKey(ring: Ring) = intPreferencesKey("selected_" + ring.name.lowercase())

    private fun encodePages(pages: List<WidgetPage>): String {
        val array = JSONArray()
        pages.forEach { page ->
            val widgets = JSONArray()
            page.widgets.forEach { w ->
                widgets.put(JSONObject().put("id", w.appWidgetId).put("size", w.size.name))
            }
            array.put(JSONObject().put("id", page.id).put("widgets", widgets))
        }
        return array.toString()
    }

    private fun decodePages(json: String?): List<WidgetPage> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val page = array.getJSONObject(i)
                val widgetsJson = page.optJSONArray("widgets") ?: JSONArray()
                val widgets = List(widgetsJson.length()) { j ->
                    val w = widgetsJson.getJSONObject(j)
                    val size = WidgetSize.entries.firstOrNull { it.name == w.optString("size") } ?: WidgetSize.MEDIUM
                    PlacedWidget(appWidgetId = w.getInt("id"), size = size)
                }
                WidgetPage(id = page.getString("id"), widgets = widgets)
            }
        }.getOrDefault(emptyList())
    }

    private object Keys {
        val SLOTS = stringSetPreferencesKey("slots")
        val PAGES = stringPreferencesKey("pages")
        val THEME = stringPreferencesKey("theme")
        val DEPTH = booleanPreferencesKey("depth3d")
        val HAPTICS = booleanPreferencesKey("haptics")
        val SOUND = booleanPreferencesKey("sound")
        val BLUR = booleanPreferencesKey("blur")
        val SEEDED = booleanPreferencesKey("seeded")
    }

    private companion object {
        const val SEPARATOR = '|'
    }
}
