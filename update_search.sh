cat << 'INNER' > patch.kt
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
            oldProfile.features.searchEnabled != profile.features.searchEnabled ||
            oldProfile.features.liveTvEnabled != profile.features.liveTvEnabled ||
            oldProfile.features.moviesEnabled != profile.features.moviesEnabled ||
            oldProfile.features.seriesEnabled != profile.features.seriesEnabled
            
        if (identityChanged) {
            searchJob?.cancel()
            activeRequestIdentity = null
            favoriteMutationJob?.cancel()
            favoriteMutationGeneration++
            
            liveCatsJob?.cancel()
            movieCatsJob?.cancel()
            seriesCatsJob?.cancel()
            observeInputJob?.cancel()
            
            liveCatsJob = null
            movieCatsJob = null
            seriesCatsJob = null
            observeInputJob = null

            if (!profile.features.searchEnabled) {
                _query.value = ""
                _uiState.update { SearchUiState(query = "") }
            }
        }
INNER
