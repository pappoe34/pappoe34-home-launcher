package com.pappoe34.launcher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pappoe34.launcher.LauncherApplication
import com.pappoe34.launcher.data.AppRepository
import com.pappoe34.launcher.data.LauncherPreferences
import com.pappoe34.launcher.model.AppInfo
import com.pappoe34.launcher.model.LauncherSettings
import com.pappoe34.launcher.model.Ring
import com.pappoe34.launcher.model.SlotId
import com.pappoe34.launcher.model.WheelTheme
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Result of trying to add an app to the first free wheel slot. */
sealed interface AddToWheelResult {
    data class Added(val slot: SlotId) : AddToWheelResult
    data object AlreadyOnWheel : AddToWheelResult
    data object WheelsFull : AddToWheelResult
}

data class LauncherUiState(
    val apps: List<AppInfo> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true,
    val isDefaultLauncher: Boolean = true,
    val defaultPromptDismissed: Boolean = false,
    val settings: LauncherSettings = LauncherSettings(),
) {
    private val appsByKey: Map<String, AppInfo> by lazy { apps.associateBy { it.key } }

    val filteredApps: List<AppInfo>
        get() {
            val q = query.trim()
            if (q.isEmpty()) return apps
            return apps.filter { app ->
                app.label.contains(q, ignoreCase = true) || app.packageName.contains(q, ignoreCase = true)
            }
        }

    /** Apps per ring, in slot order; null marks an empty slot. */
    val rings: Map<Ring, List<AppInfo?>>
        get() = Ring.entries.associateWith { ring ->
            List(ring.slotCount) { index -> settings.slots[SlotId(ring, index)]?.let { appsByKey[it] } }
        }

    fun appAt(slot: SlotId): AppInfo? = settings.slots[slot]?.let { appsByKey[it] }

    fun isOnWheel(app: AppInfo): Boolean = settings.slots.containsValue(app.key)

    val showDefaultPrompt: Boolean get() = !isDefaultLauncher && !defaultPromptDismissed
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LauncherApplication
    private val repository: AppRepository = app.appRepository
    private val preferences: LauncherPreferences = app.preferences

    private val local = MutableStateFlow(LauncherUiState())

    val state: StateFlow<LauncherUiState> = combine(local, preferences.settings) { base, settings ->
        base.copy(settings = settings)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherUiState())

    init {
        refresh()
        observePackageChanges()
        seedOnFirstRun()
    }

    fun refresh() {
        viewModelScope.launch {
            val apps = repository.loadApps()
            local.update { it.copy(apps = apps, isLoading = false) }
        }
        refreshDefaultStatus()
    }

    fun refreshDefaultStatus() {
        val isDefault = repository.isDefaultLauncher()
        local.update { it.copy(isDefaultLauncher = isDefault) }
    }

    fun onQueryChange(query: String) = local.update { it.copy(query = query) }
    fun clearQuery() = local.update { it.copy(query = "") }
    fun dismissDefaultPrompt() = local.update { it.copy(defaultPromptDismissed = true) }

    fun launch(app: AppInfo): Boolean {
        val ok = repository.launch(app)
        if (ok) clearQuery()
        return ok
    }

    fun openAppInfo(app: AppInfo) = repository.openAppInfo(app)
    fun uninstall(app: AppInfo) = repository.requestUninstall(app)

    // ---- Wheel slots -------------------------------------------------------

    fun assign(slot: SlotId, app: AppInfo?) {
        viewModelScope.launch { preferences.assign(slot, app?.key) }
    }

    fun addToWheel(app: AppInfo): AddToWheelResult {
        val current = state.value
        if (current.isOnWheel(app)) return AddToWheelResult.AlreadyOnWheel
        val free = SlotId.all().firstOrNull { it !in current.settings.slots } ?: return AddToWheelResult.WheelsFull
        assign(free, app)
        return AddToWheelResult.Added(free)
    }

    /** Fill every slot from the installed apps, alphabetically, overwriting current assignments. */
    fun autoFillWheels() {
        val apps = state.value.apps
        if (apps.isEmpty()) return
        val slots = SlotId.all().zip(apps).associate { (slot, app) -> slot to app.key }
        viewModelScope.launch { preferences.replaceSlots(slots, markSeeded = true) }
    }

    fun clearWheels() {
        viewModelScope.launch { preferences.replaceSlots(emptyMap(), markSeeded = true) }
    }

    // ---- Settings ----------------------------------------------------------

    fun setTheme(theme: WheelTheme) = viewModelScope.launch { preferences.setTheme(theme) }
    fun setHaptics(enabled: Boolean) = viewModelScope.launch { preferences.setHaptics(enabled) }
    fun setSound(enabled: Boolean) = viewModelScope.launch { preferences.setSound(enabled) }
    fun setBlur(enabled: Boolean) = viewModelScope.launch { preferences.setBlur(enabled) }

    // ---- Internals ---------------------------------------------------------

    private fun seedOnFirstRun() {
        viewModelScope.launch {
            // `state` only emits once both the app list and the persisted settings are known,
            // so the first non-loading value carries the real `seeded` flag.
            val ready = state.first { !it.isLoading }
            if (!ready.settings.seeded && ready.apps.isNotEmpty()) autoFillWheels()
        }
    }

    @OptIn(FlowPreview::class)
    private fun observePackageChanges() {
        viewModelScope.launch {
            repository.packageChanges()
                .debounce(300)
                .collect { refresh() }
        }
    }
}
