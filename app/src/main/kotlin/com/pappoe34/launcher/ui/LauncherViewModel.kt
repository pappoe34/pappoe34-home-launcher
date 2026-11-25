package com.pappoe34.launcher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pappoe34.launcher.LauncherApplication
import com.pappoe34.launcher.data.AppRepository
import com.pappoe34.launcher.model.AppInfo
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LauncherUiState(
    val apps: List<AppInfo> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true,
    val isDefaultLauncher: Boolean = true,
    val defaultPromptDismissed: Boolean = false,
) {
    val filteredApps: List<AppInfo>
        get() {
            val q = query.trim()
            if (q.isEmpty()) return apps
            return apps.filter { app ->
                app.label.contains(q, ignoreCase = true) ||
                    app.packageName.contains(q, ignoreCase = true)
            }
        }

    val showDefaultPrompt: Boolean get() = !isDefaultLauncher && !defaultPromptDismissed
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository = (application as LauncherApplication).appRepository

    private val _state = MutableStateFlow(LauncherUiState())
    val state: StateFlow<LauncherUiState> = _state.asStateFlow()

    init {
        refresh()
        observePackageChanges()
    }

    fun refresh() {
        viewModelScope.launch {
            val apps = repository.loadApps()
            _state.update { it.copy(apps = apps, isLoading = false) }
        }
        refreshDefaultStatus()
    }

    fun refreshDefaultStatus() {
        val isDefault = repository.isDefaultLauncher()
        _state.update { it.copy(isDefaultLauncher = isDefault) }
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    fun clearQuery() = _state.update { it.copy(query = "") }

    fun dismissDefaultPrompt() = _state.update { it.copy(defaultPromptDismissed = true) }

    fun launch(app: AppInfo): Boolean {
        val ok = repository.launch(app)
        if (ok) clearQuery()
        return ok
    }

    fun openAppInfo(app: AppInfo) = repository.openAppInfo(app)

    fun uninstall(app: AppInfo) = repository.requestUninstall(app)

    @OptIn(FlowPreview::class)
    private fun observePackageChanges() {
        viewModelScope.launch {
            repository.packageChanges()
                .debounce(300)
                .collect { refresh() }
        }
    }
}
