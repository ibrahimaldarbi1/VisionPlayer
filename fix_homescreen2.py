import re

with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "r") as f:
    content = f.read()

# Remove any existing definition of multiViewEnabled and the LaunchedEffect
pattern_to_remove = r'\s*val multiViewEnabled\s*=\s*FeatureAvailabilityPolicy\.shouldShowMultiView\(profile\.features\)\s*LaunchedEffect\(profile\.id, profile\.providerId, profile\.features\) \{[\s\S]*?(?=\s*LaunchedEffect|\s*val |\s*var )'

# We'll use a safer approach: just write a regex that matches exactly what we injected.
content = re.sub(r'\s*val multiViewEnabled = FeatureAvailabilityPolicy\.shouldShowMultiView\(profile\.features\)\s*LaunchedEffect\(profile\.id, profile\.providerId, profile\.features\) \{.*?\n    \}\n', '\n', content, flags=re.DOTALL)

# Also remove '    val multiViewEnabled = FeatureAvailabilityPolicy.shouldShowMultiView(profile.features)' if it's lingering
content = re.sub(r'\s*val multiViewEnabled = FeatureAvailabilityPolicy\.shouldShowMultiView\(profile\.features\)\n', '\n', content)

# Now inject it just before LaunchedEffect(addToMultiViewChannel, multiViewEnabled)
good_effect = """
    val multiViewEnabled = FeatureAvailabilityPolicy.shouldShowMultiView(profile.features)

    LaunchedEffect(profile.id, profile.providerId, profile.features) {
        navigationState.onFeaturesChanged(profile.features)
        
        if (!multiViewEnabled) {
            showMultiViewSetup = false
            activeMultiViewChannels = null
            pendingMultiViewChannels = emptyList()
        }
        if (!profile.features.seriesEnabled) {
            activeSeriesDetail = null
        }
        if (!profile.features.moviesEnabled && !profile.features.seriesEnabled) {
            unavailableTmdbItem = null
        }
        if (!profile.features.searchEnabled) {
            searchQuery = ""
            searchResult = SearchResults()
        }
    }

"""

content = content.replace("    LaunchedEffect(addToMultiViewChannel, multiViewEnabled) {", good_effect + "    LaunchedEffect(addToMultiViewChannel, multiViewEnabled) {")

with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "w") as f:
    f.write(content)
