cat << 'INNER' > patch.kt
    private var loadSeasonsJob: Job? = null
    private var loadEpisodesJob: Job? = null
    
    private var favoriteMutationGeneration = 0
    private data class FavoriteMutationIdentity(
        val profileId: String,
        val providerId: String,
        val favoritesEnabled: Boolean,
        val relevantContentEnabled: Boolean,
        val generation: Int
    )

    fun onProfileChanged(profile: ProviderProfile) {
        val oldProfile = currentProfile
        currentProfile = profile
        
        val identityChanged = oldProfile?.id != profile.id ||
            oldProfile.providerId != profile.providerId ||
            oldProfile.features.favoritesEnabled != profile.features.favoritesEnabled ||
            oldProfile.features.seriesEnabled != profile.features.seriesEnabled
            
        if (identityChanged) {
            favoriteMutationJob?.cancel()
            favoriteMutationGeneration++
        }

        if (oldProfile?.providerId != profile.providerId) {
            _uiState.update { it.copy(selectedCategoryId = null, seriesList = emptyList(), error = null) }
            selectSeries(null)
        }
INNER
