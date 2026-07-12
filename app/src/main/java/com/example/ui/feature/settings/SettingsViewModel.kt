package com.example.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.ProviderProfile
import com.example.data.CategoryManagementItem
import com.example.data.IptvRepository
import com.example.data.SessionEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val showProfileDialog: Boolean = false,
    val subScreen: String? = null,
    val isRefreshingCache: Boolean = false,
    val activeSession: SessionEntity? = null,
    val categories: List<CategoryManagementItem> = emptyList(),
    val selectedTab: String = "LIVE",
    val isCategoryOperating: Boolean = false,
    val categoryError: String? = null
)

class SettingsViewModel(private val repository: IptvRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var observeCategoriesJob: Job? = null
    private var currentProfile: ProviderProfile? = null
    private var activeOperationsCount = 0

    init {
        viewModelScope.launch {
            repository.activeSession.collect { session ->
                _uiState.update { it.copy(activeSession = session) }
            }
        }
    }

    fun onProfileChanged(profile: ProviderProfile) {
        val oldProfile = currentProfile
        currentProfile = profile

        // 1. Ensure category management selects an enabled content tab after profile features change.
        val enabledTabs = mutableListOf<String>()
        if (com.example.ui.feature.shell.FeatureAvailabilityPolicy.canManageCategory(profile.features, "LIVE")) enabledTabs.add("LIVE")
        if (com.example.ui.feature.shell.FeatureAvailabilityPolicy.canManageCategory(profile.features, "MOVIE")) enabledTabs.add("MOVIE")
        if (com.example.ui.feature.shell.FeatureAvailabilityPolicy.canManageCategory(profile.features, "SERIES")) enabledTabs.add("SERIES")

        val currentTab = _uiState.value.selectedTab
        if (currentTab !in enabledTabs) {
            val newTab = enabledTabs.firstOrNull() ?: "LIVE"
            _uiState.update { it.copy(selectedTab = newTab) }
        }
        
        if (enabledTabs.isEmpty() && _uiState.value.subScreen == "CATEGORY_MANAGEMENT") {
            setSubScreen(null)
            return
        }

        // 2. If the subScreen is category management, update our observations
        if (_uiState.value.subScreen == "CATEGORY_MANAGEMENT") {
            observeCategories()
        }
    }

    fun showProfileDialog(show: Boolean) {
        _uiState.update { it.copy(showProfileDialog = show) }
    }

    fun setSubScreen(screen: String?) {
        _uiState.update { it.copy(subScreen = screen) }
        if (screen == "CATEGORY_MANAGEMENT") {
            observeCategories()
        } else {
            observeCategoriesJob?.cancel()
            observeCategoriesJob = null
        }
    }

    fun selectTab(tab: String) {
        _uiState.update { it.copy(selectedTab = tab) }
        observeCategories()
    }

    private fun isTabEnabled(tab: String): Boolean {
        val profile = currentProfile ?: return false
        return when (tab) {
            "LIVE" -> com.example.ui.feature.shell.FeatureAvailabilityPolicy.canManageCategory(profile.features, "LIVE")
            "MOVIE" -> com.example.ui.feature.shell.FeatureAvailabilityPolicy.canManageCategory(profile.features, "MOVIE")
            "SERIES" -> com.example.ui.feature.shell.FeatureAvailabilityPolicy.canManageCategory(profile.features, "SERIES")
            else -> false
        }
    }

    private fun observeCategories() {
        observeCategoriesJob?.cancel()
        val profile = currentProfile ?: return
        val tab = _uiState.value.selectedTab
        
        if (!isTabEnabled(tab)) {
            _uiState.update { it.copy(categories = emptyList()) }
            return
        }

        observeCategoriesJob = viewModelScope.launch {
            repository.observeAllCategoriesForManagement(tab).collect { list ->
                _uiState.update { it.copy(categories = list) }
            }
        }
    }

    fun refreshCache() {
        val profile = currentProfile ?: return
        if (!com.example.ui.feature.shell.FeatureAvailabilityPolicy.canRefreshEpg(profile.features)) return
        _uiState.update { it.copy(isRefreshingCache = true) }
        viewModelScope.launch {
            try {
                repository.refreshEpg()
            } finally {
                _uiState.update { it.copy(isRefreshingCache = false) }
            }
        }
    }

    private fun startOperation() {
        activeOperationsCount++
        _uiState.update { it.copy(isCategoryOperating = true) }
    }

    private fun endOperation() {
        activeOperationsCount--
        if (activeOperationsCount <= 0) {
            activeOperationsCount = 0
            _uiState.update { it.copy(isCategoryOperating = false) }
        }
    }

    fun reorderCategories(listIds: List<String>) {
        val tab = _uiState.value.selectedTab
        if (!isTabEnabled(tab)) return
        startOperation()
        viewModelScope.launch {
            try {
                if (!isTabEnabled(tab)) return@launch
                repository.updateCategorySortOrder(tab, listIds)
            } catch (e: Exception) {
                _uiState.update { it.copy(categoryError = e.message) }
            } finally {
                endOperation()
            }
        }
    }

    fun setCategoryPinned(categoryId: String, pinned: Boolean) {
        val tab = _uiState.value.selectedTab
        if (!isTabEnabled(tab)) return
        startOperation()
        viewModelScope.launch {
            try {
                if (!isTabEnabled(tab)) return@launch
                repository.setCategoryPinned(tab, categoryId, pinned)
            } catch (e: Exception) {
                _uiState.update { it.copy(categoryError = e.message) }
            } finally {
                endOperation()
            }
        }
    }

    fun setCategoryHidden(categoryId: String, hidden: Boolean) {
        val tab = _uiState.value.selectedTab
        if (!isTabEnabled(tab)) return
        startOperation()
        viewModelScope.launch {
            try {
                if (!isTabEnabled(tab)) return@launch
                repository.setCategoryHidden(tab, categoryId, hidden)
            } catch (e: Exception) {
                _uiState.update { it.copy(categoryError = e.message) }
            } finally {
                endOperation()
            }
        }
    }

    fun resetCategoryCustomization() {
        val tab = _uiState.value.selectedTab
        if (!isTabEnabled(tab)) return
        startOperation()
        viewModelScope.launch {
            try {
                if (!isTabEnabled(tab)) return@launch
                repository.resetCategoryCustomization(tab)
            } catch (e: Exception) {
                _uiState.update { it.copy(categoryError = e.message) }
            } finally {
                endOperation()
            }
        }
    }

    fun clearCategoryError() {
        _uiState.update { it.copy(categoryError = null) }
    }
}
