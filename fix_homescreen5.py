import re

with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "r") as f:
    content = f.read()

# First, strip the block wherever it is
block = r'\s*val multiViewEnabled = FeatureAvailabilityPolicy\.shouldShowMultiView\(profile\.features\)\s*LaunchedEffect\(profile\.id, profile\.providerId, profile\.features\) \{[\s\S]*?\n    \}\n'
content = re.sub(block, '\n', content)

# Now, locate unavailableTmdbItem declaration
target_line = r'(    var unavailableTmdbItem by remember \{ mutableStateOf<HomeItem\?><null>\(null\) \}\n)'
# Wait, the generic <HomeItem?> could be messed up in regex. Let's match just the line exactly.
target_str = "    var unavailableTmdbItem by remember { mutableStateOf<HomeItem?>(null) }\n"

new_block = """
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

content = content.replace(target_str, target_str + new_block)

with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "w") as f:
    f.write(content)
