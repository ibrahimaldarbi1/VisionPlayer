import re

# Fix MainActivity.kt
with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

if "import com.example.ui.feature.shell.FeatureAvailabilityPolicy" not in content:
    content = content.replace("import com.example.ui.theme.MyApplicationTheme", "import com.example.ui.theme.MyApplicationTheme\nimport com.example.ui.feature.shell.FeatureAvailabilityPolicy")

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

# Fix HomeScreen.kt
with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "r") as f:
    content = f.read()

block_to_move = """    val multiViewEnabled = FeatureAvailabilityPolicy.shouldShowMultiView(profile.features)

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
content = content.replace("\n" + block_to_move, "\n")
content = content.replace("    LaunchedEffect(addToMultiViewChannel, multiViewEnabled) {", block_to_move + "    LaunchedEffect(addToMultiViewChannel, multiViewEnabled) {")

with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "w") as f:
    f.write(content)
