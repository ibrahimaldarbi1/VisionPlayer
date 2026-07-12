import re

with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "r") as f:
    content = f.read()

# 1. Strip the incorrectly placed block.
# We will use string replacement to remove it exactly.
block_to_remove = """    val multiViewEnabled = FeatureAvailabilityPolicy.shouldShowMultiView(profile.features)

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
content = content.replace(block_to_remove, "")

# 2. Insert it at the right place. We will find `var unavailableTmdbItem by remember { mutableStateOf<HomeItem?>(null) }`
# and insert our block right after it.
insertion_point = "    var unavailableTmdbItem by remember { mutableStateOf<HomeItem?>(null) }\n"
content = content.replace(insertion_point, insertion_point + "\n" + block_to_remove)

with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "w") as f:
    f.write(content)
