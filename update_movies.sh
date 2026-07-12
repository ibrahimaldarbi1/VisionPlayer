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
            oldProfile.features.favoritesEnabled != profile.features.favoritesEnabled ||
            oldProfile.features.moviesEnabled != profile.features.moviesEnabled
            
        if (identityChanged) {
            favoriteMutationJob?.cancel()
            favoriteMutationGeneration++
        }

        if (oldProfile?.providerId != profile.providerId) {
            _uiState.update { it.copy(selectedCategoryId = null, movies = emptyList(), error = null) }
        }
INNER
