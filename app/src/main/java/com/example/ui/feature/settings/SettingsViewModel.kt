package com.example.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.ProviderProfile
import com.example.data.IptvRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val showProfileDialog: Boolean = false,
    val subScreen: String? = null,
    val isRefreshingCache: Boolean = false
)

class SettingsViewModel(private val repository: IptvRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var onProfileSelectedCallback: ((ProviderProfile) -> Unit)? = null

    fun initCallbacks(onProfileSelected: (ProviderProfile) -> Unit) {
        onProfileSelectedCallback = onProfileSelected
    }

    fun selectProfile(profile: ProviderProfile) {
        onProfileSelectedCallback?.invoke(profile)
    }

    fun showProfileDialog(show: Boolean) {
        _uiState.update { it.copy(showProfileDialog = show) }
    }

    fun setSubScreen(screen: String?) {
        _uiState.update { it.copy(subScreen = screen) }
    }

    fun refreshCache() {
        _uiState.update { it.copy(isRefreshingCache = true) }
        viewModelScope.launch {
            try {
                repository.refreshEpg()
            } finally {
                _uiState.update { it.copy(isRefreshingCache = false) }
            }
        }
    }
}
