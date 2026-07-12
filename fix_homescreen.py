import re

with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "r") as f:
    content = f.read()

# Remove the bad import and the first @Composable
content = content.replace("@Composable\nimport com.example.config.ProviderProfile\n\n@Composable\nfun HomeScreen", "@Composable\nfun HomeScreen")

# Add the import at the top
if "import com.example.config.ProviderProfile" not in content[:1000]:
    content = content.replace("package com.example.ui.screens\n", "package com.example.ui.screens\n\nimport com.example.config.ProviderProfile\n")

# Remove the misplaced LaunchedEffect
bad_effect_regex = r'    val multiViewEnabled = FeatureAvailabilityPolicy\.shouldShowMultiView\(profile\.features\)\n\n    LaunchedEffect\(profile\.id, profile\.providerId, profile\.features\) \{\n        navigationState\.onFeaturesChanged\(profile\.features\)\n        \n        if \(\!multiViewEnabled\) \{\n            showMultiViewSetup = false\n            activeMultiViewChannels = null\n            pendingMultiViewChannels = emptyList\(\)\n        \}\n        if \(\!profile\.features\.seriesEnabled\) \{\n            activeSeriesDetail = null\n        \}\n        if \(\!profile\.features\.moviesEnabled && \!profile\.features\.seriesEnabled\) \{\n            unavailableTmdbItem = null\n        \}\n        if \(\!profile\.features\.searchEnabled\) \{\n            searchQuery = ""\n            searchResult = SearchResults\(\)\n        \}\n    \}\n'
content = content.replace(bad_effect_regex, "")

# Find where all state variables end, or simply place it before the second LaunchedEffect.
# Let's place the multiViewEnabled declaration right after the last remember
# Actually, I can put it right before LaunchedEffect(addToMultiViewChannel, multiViewEnabled)
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
