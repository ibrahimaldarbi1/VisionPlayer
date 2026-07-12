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

    private data class CategoryOpIdentity(
        val profileId: String,
        val providerId: String,
        val tab: String,
        val generation: Int
    )

    private data class EpgRefreshIdentity(
        val profileId: String,
        val providerId: String,
        val liveEnabled: Boolean,
        val epgEnabled: Boolean,
        val generation: Int
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var observeCategoriesJob: Job? = null
    
    private var epgRefreshJob: Job? = null
    private var epgRefreshGeneration = 0
    private var activeEpgIdentity: EpgRefreshIdentity? = null
    
    private val categoryOpJobs = mutableListOf<Job>()
    private var currentProfile: ProviderProfile? = null
    private var activeOperationsCount = 0
    private var opGeneration = 0

    init {
        viewModelScope.launch {
            repository.activeSession.collect { session ->
                _uiState.update { it.copy(activeSession = session) }
            }
        }
    }

    private fun clearCategoryOperations() {
        categoryOpJobs.forEach { it.cancel() }
        categoryOpJobs.clear()
        opGeneration++
        activeOperationsCount = 0
        _uiState.update { it.copy(isCategoryOperating = false, categoryError = null, categories = emptyList()) }
    }

    fun onProfileChanged(profile: ProviderProfile) {
        val oldProfile = currentProfile
        currentProfile = profile
        
        val epgIdentityChanged = oldProfile?.id != profile.id || 
            oldProfile.providerId != profile.providerId || 
            oldProfile.features.liveTvEnabled != profile.features.liveTvEnabled || 
            oldProfile.features.epgEnabled != profile.features.epgEnabled

        if (epgIdentityChanged) {
            epgRefreshJob?.cancel()
            epgRefreshGeneration++
            activeEpgIdentity = null
            _uiState.update { it.copy(isRefreshingCache = false) }
        }

        // 1. Ensure category management selects an enabled content tab after profile features change.
        val enabledTabs = mutableListOf<String>()
        if (com.example.ui.feature.shell.FeatureAvailabilityPolicy.canManageCategory(profile.features, "LIVE")) enabledTabs.add("LIVE")
        if (com.example.ui.feature.shell.FeatureAvailabilityPolicy.canManageCategory(profile.features, "MOVIE")) enabledTabs.add("MOVIE")
        if (com.example.ui.feature.shell.FeatureAvailabilityPolicy.canManageCategory(profile.features, "SERIES")) enabledTabs.add("SERIES")

        val currentTab = _uiState.value.selectedTab
        if (currentTab !in enabledTabs) {
            val newTab = enabledTabs.firstOrNull() ?: "LIVE"
            _uiState.update { it.copy(selectedTab = newTab) }
            clearCategoryOperations()
        }
        
        if (enabledTabs.isEmpty()) {
            if (_uiState.value.subScreen == "CATEGORY_MANAGEMENT") {
                setSubScreen(null)
            } else {
                clearCategoryOperations()
            }
            return
        }

        // 2. If the subScreen is category management, update our observations
        if (_uiState.value.subScreen == "CATEGORY_MANAGEMENT") {
            if (isTabEnabled(_uiState.value.selectedTab)) {
                observeCategories()
            } else {
                clearCategoryOperations()
            }
        }
    }

    fun showProfileDialog(show: Boolean) {
        _uiState.update { it.copy(showProfileDialog = show) }
    }

    fun setSubScreen(screen: String?) {
        if (screen == "CATEGORY_MANAGEMENT") {
            val profile = currentProfile ?: return
            val enabledTabs = mutableListOf<String>()
            if (com.example.ui.feature.shell.FeatureAvailabilityPolicy.canManageCategory(profile.features, "LIVE")) enabledTabs.add("LIVE")
            if (com.example.ui.feature.shell.FeatureAvailabilityPolicy.canManageCategory(profile.features, "MOVIE")) enabledTabs.add("MOVIE")
            if (com.example.ui.feature.shell.FeatureAvailabilityPolicy.canManageCategory(profile.features, "SERIES")) enabledTabs.add("SERIES")
            if (enabledTabs.isEmpty()) return
            
            _uiState.update { it.copy(subScreen = screen) }
            observeCategories()
        } else {
            _uiState.update { it.copy(subScreen = screen) }
            observeCategoriesJob?.cancel()
            observeCategoriesJob = null
            clearCategoryOperations()
        }
    }

    fun selectTab(tab: String) {
        if (!isTabEnabled(tab)) return
        val currentTab = _uiState.value.selectedTab
        if (currentTab != tab) {
            clearCategoryOperations()
        }
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

        epgRefreshGeneration++
        val identity = EpgRefreshIdentity(
            profileId = profile.id,
            providerId = profile.providerId,
            liveEnabled = profile.features.liveTvEnabled,
            epgEnabled = profile.features.epgEnabled,
            generation = epgRefreshGeneration
        )
        activeEpgIdentity = identity
        
        _uiState.update { it.copy(isRefreshingCache = true) }
        epgRefreshJob?.cancel()
        epgRefreshJob = viewModelScope.launch {
            try {
                if (activeEpgIdentity != identity) return@launch
                val current = currentProfile ?: return@launch
                if (!com.example.ui.feature.shell.FeatureAvailabilityPolicy.canRefreshEpg(current.features)) return@launch
                
                repository.refreshEpg()
            } finally {
                if (activeEpgIdentity == identity) {
                    _uiState.update { it.copy(isRefreshingCache = false) }
                }
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

    private fun getOpIdentity(tab: String): CategoryOpIdentity? {
        val profile = currentProfile ?: return null
        return CategoryOpIdentity(profile.id, profile.providerId, tab, opGeneration)
    }

    private fun isOpValid(identity: CategoryOpIdentity): Boolean {
        val profile = currentProfile ?: return false
        if (profile.id != identity.profileId || profile.providerId != identity.providerId) return false
        if (_uiState.value.selectedTab != identity.tab) return false
        if (_uiState.value.subScreen != "CATEGORY_MANAGEMENT") return false
        if (opGeneration != identity.generation) return false
        return isTabEnabled(identity.tab)
    }

    fun reorderCategories(listIds: List<String>) {
        val tab = _uiState.value.selectedTab
        val identity = getOpIdentity(tab) ?: return
        if (!isOpValid(identity)) return
        
        startOperation()
        val job = viewModelScope.launch {
            try {
                if (!isOpValid(identity)) return@launch
                repository.updateCategorySortOrder(tab, listIds)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isOpValid(identity)) {
                    _uiState.update { it.copy(categoryError = e.message) }
                }
            } finally {
                if (isOpValid(identity)) endOperation()
                categoryOpJobs.remove(coroutineContext[Job])
            }
        }
        categoryOpJobs.add(job)
    }

    fun setCategoryPinned(categoryId: String, pinned: Boolean) {
        val tab = _uiState.value.selectedTab
        val identity = getOpIdentity(tab) ?: return
        if (!isOpValid(identity)) return
        
        startOperation()
        val job = viewModelScope.launch {
            try {
                if (!isOpValid(identity)) return@launch
                repository.setCategoryPinned(tab, categoryId, pinned)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isOpValid(identity)) {
                    _uiState.update { it.copy(categoryError = e.message) }
                }
            } finally {
                if (isOpValid(identity)) endOperation()
                categoryOpJobs.remove(coroutineContext[Job])
            }
        }
        categoryOpJobs.add(job)
    }

    fun setCategoryHidden(categoryId: String, hidden: Boolean) {
        val tab = _uiState.value.selectedTab
        val identity = getOpIdentity(tab) ?: return
        if (!isOpValid(identity)) return
        
        startOperation()
        val job = viewModelScope.launch {
            try {
                if (!isOpValid(identity)) return@launch
                repository.setCategoryHidden(tab, categoryId, hidden)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isOpValid(identity)) {
                    _uiState.update { it.copy(categoryError = e.message) }
                }
            } finally {
                if (isOpValid(identity)) endOperation()
                categoryOpJobs.remove(coroutineContext[Job])
            }
        }
        categoryOpJobs.add(job)
    }

    fun resetCategoryCustomization() {
        val tab = _uiState.value.selectedTab
        val identity = getOpIdentity(tab) ?: return
        if (!isOpValid(identity)) return
        
        startOperation()
        val job = viewModelScope.launch {
            try {
                if (!isOpValid(identity)) return@launch
                repository.resetCategoryCustomization(tab)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isOpValid(identity)) {
                    _uiState.update { it.copy(categoryError = e.message) }
                }
            } finally {
                if (isOpValid(identity)) endOperation()
                categoryOpJobs.remove(coroutineContext[Job])
            }
        }
        categoryOpJobs.add(job)
    }

    fun clearCategoryError() {
        _uiState.update { it.copy(categoryError = null) }
    }
}
