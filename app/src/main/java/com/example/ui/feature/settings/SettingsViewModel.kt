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

    init {
        viewModelScope.launch {
            repository.activeSession.collect { session ->
                _uiState.update { it.copy(activeSession = session) }
            }
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

    private fun observeCategories() {
        observeCategoriesJob?.cancel()
        val tab = _uiState.value.selectedTab
        observeCategoriesJob = viewModelScope.launch {
            repository.observeAllCategoriesForManagement(tab).collect { list ->
                _uiState.update { it.copy(categories = list) }
            }
        }
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

    fun reorderCategories(listIds: List<String>) {
        val tab = _uiState.value.selectedTab
        _uiState.update { it.copy(isCategoryOperating = true) }
        viewModelScope.launch {
            try {
                repository.updateCategorySortOrder(tab, listIds)
            } catch (e: Exception) {
                _uiState.update { it.copy(categoryError = e.message) }
            } finally {
                _uiState.update { it.copy(isCategoryOperating = false) }
            }
        }
    }

    fun setCategoryPinned(categoryId: String, pinned: Boolean) {
        val tab = _uiState.value.selectedTab
        _uiState.update { it.copy(isCategoryOperating = true) }
        viewModelScope.launch {
            try {
                repository.setCategoryPinned(tab, categoryId, pinned)
            } catch (e: Exception) {
                _uiState.update { it.copy(categoryError = e.message) }
            } finally {
                _uiState.update { it.copy(isCategoryOperating = false) }
            }
        }
    }

    fun setCategoryHidden(categoryId: String, hidden: Boolean) {
        val tab = _uiState.value.selectedTab
        _uiState.update { it.copy(isCategoryOperating = true) }
        viewModelScope.launch {
            try {
                repository.setCategoryHidden(tab, categoryId, hidden)
            } catch (e: Exception) {
                _uiState.update { it.copy(categoryError = e.message) }
            } finally {
                _uiState.update { it.copy(isCategoryOperating = false) }
            }
        }
    }

    fun resetCategoryCustomization() {
        val tab = _uiState.value.selectedTab
        _uiState.update { it.copy(isCategoryOperating = true) }
        viewModelScope.launch {
            try {
                repository.resetCategoryCustomization(tab)
            } catch (e: Exception) {
                _uiState.update { it.copy(categoryError = e.message) }
            } finally {
                _uiState.update { it.copy(isCategoryOperating = false) }
            }
        }
    }

    fun clearCategoryError() {
        _uiState.update { it.copy(categoryError = null) }
    }
}
