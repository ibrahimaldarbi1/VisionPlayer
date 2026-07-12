import re

with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "r") as f:
    content = f.read()

# I will find all state declarations and move the effect to the VERY END of the state declarations.
# Let's search for "var unavailableTmdbItem by remember" and insert the effect right after it.

block = r'\s*val multiViewEnabled = FeatureAvailabilityPolicy\.shouldShowMultiView\(profile\.features\)\s*LaunchedEffect\(profile\.id, profile\.providerId, profile\.features\) \{[\s\S]*?\n    \}\n'
content = re.sub(block, '\n', content)

# But wait, we also have "LaunchedEffect(addToMultiViewChannel, multiViewEnabled)".
# If that is at line 245, it uses multiViewEnabled!
# So multiViewEnabled MUST be declared BEFORE "LaunchedEffect(addToMultiViewChannel, multiViewEnabled)".
# But multiViewEnabled's effect uses `activeSeriesDetail` etc., which are declared at line 290!
# THIS is the problem! The state variables are declared AFTER they are used in `LaunchedEffect`.

# So we MUST move all those state variables UP, or move `LaunchedEffect(addToMultiViewChannel, multiViewEnabled)` DOWN.
# Moving `LaunchedEffect(addToMultiViewChannel...` DOWN is much safer.

# Let's move LaunchedEffect(addToMultiViewChannel, multiViewEnabled) ... down.
add_to_multiview_effect_regex = r'\s*LaunchedEffect\(addToMultiViewChannel, multiViewEnabled\) \{[\s\S]*?\n    \}\n'
m = re.search(add_to_multiview_effect_regex, content)
if m:
    effect_content = m.group(0)
    content = content.replace(effect_content, '\n')
    
    # We will inject `multiViewEnabled` and both effects after `var unavailableTmdbItem by remember`
    target_str = "    var unavailableTmdbItem by remember { mutableStateOf<HomeItem?>(null) }\n"
    
    new_blocks = """
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
""" + effect_content

    content = content.replace(target_str, target_str + new_blocks)

with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "w") as f:
    f.write(content)

