import os
import re

def patch_file(filepath, callback):
    with open(filepath, 'r') as f:
        content = f.read()
    new_content = callback(content)
    with open(filepath, 'w') as f:
        f.write(new_content)

def patch_homescreen(content):
    # 3. Make HomeScreen receive the profile
    # Change signature
    content = re.sub(
        r'fun HomeScreen\(\s*repository: IptvRepository,\s*onPlayContent: \(PlaybackItem\) -> Unit\s*\)',
        r'import com.example.config.ProviderProfile\n\n@Composable\nfun HomeScreen(\n    profile: ProviderProfile,\n    onProfileSelected: (ProviderProfile) -> Unit,\n    repository: IptvRepository,\n    onPlayContent: (PlaybackItem) -> Unit\n)',
        content
    )

    # Remove unused import and local profile declaration
    content = re.sub(r'import com\.example\.config\.ProviderConfigRegistry\n', '', content)
    content = re.sub(r'\s*val profile = ProviderConfigRegistry\.currentProfile\n', '\n', content)

    # 4. SettingsView call update
    content = re.sub(
        r'SettingsView\(\s*repository = repository\s*\)',
        r'SettingsView(\n                                repository = repository,\n                                onProfileSelected = onProfileSelected\n                            )',
        content
    )

    # 5 & 6. Connect active-destination sanitization and disabled-feature cleanup
    # Insert after navigationState is declared
    # Assuming navigationState = rememberAppNavigationState()
    sanitize_effect = """
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
    content = re.sub(
        r'(val navigationState = rememberAppNavigationState\(\)\n)',
        r'\1' + sanitize_effect,
        content
    )

    # 8. Guard incoming Multi-view requests
    old_effect = r'LaunchedEffect\(addToMultiViewChannel\) \{\s*addToMultiViewChannel\?\.let \{ channel ->\s*if \(pendingMultiViewChannels\.none \{ it\.id == channel\.id \}\) \{\s*pendingMultiViewChannels = pendingMultiViewChannels \+ channel\s*\}\s*showMultiViewSetup = true\s*onAddToMultiViewHandled\(\)\s*\}\s*\}'
    new_effect = """LaunchedEffect(addToMultiViewChannel, multiViewEnabled) {
        addToMultiViewChannel?.let { channel ->
            if (multiViewEnabled) {
                if (pendingMultiViewChannels.none { it.id == channel.id }) {
                    pendingMultiViewChannels = pendingMultiViewChannels + channel
                }
                showMultiViewSetup = true
            }
            onAddToMultiViewHandled()
        }
    }"""
    content = re.sub(old_effect, new_effect, content, flags=re.MULTILINE)

    # 9. Guard Multi-view overlays synchronously
    content = re.sub(
        r'if \(activeMultiViewChannels != null\) \{',
        r'if (multiViewEnabled && activeMultiViewChannels != null) {',
        content
    )
    content = re.sub(
        r'\} else if \(showMultiViewSetup\) \{',
        r'} else if (multiViewEnabled && showMultiViewSetup) {',
        content
    )

    # 13. Use the AppShell content destination
    content = re.sub(
        r'AppShell\([^)]+\)\s*\{\s*activeDestination\s*->\s*when\s*\(navigationState\.activeDestination\)\s*\{',
        r'AppShell(navigationState = navigationState) { activeDestination ->\n            when (activeDestination) {',
        content,
        flags=re.MULTILINE|re.DOTALL
    )

    # Note: Regex for AppShell might be tricky if arguments span multiple lines.
    # Let's use a simpler regex for the 'when' part
    content = re.sub(
        r'when\s*\(navigationState\.activeDestination\)',
        r'when (activeDestination)',
        content
    )

    # 10. Guard Home Multi-view callback
    old_home_cb = r'onAddToMultiView\s*=\s*\{\s*channel\s*->\s*if\s*\(pendingMultiViewChannels\.none\s*\{\s*it\.id\s*==\s*channel\.id\s*\}\)\s*\{\s*pendingMultiViewChannels\s*=\s*pendingMultiViewChannels\s*\+\s*channel\s*\}\s*showMultiViewSetup\s*=\s*true\s*\}'
    new_home_cb = r"""onAddToMultiView = if (multiViewEnabled) {
                                    { channel ->
                                        if (pendingMultiViewChannels.none { it.id == channel.id }) {
                                            pendingMultiViewChannels = pendingMultiViewChannels + channel
                                        }
                                        showMultiViewSetup = true
                                    }
                                } else null"""
    content = re.sub(old_home_cb, new_home_cb, content)

    # 11. Guard Live Multi-view callback
    old_live_cb = r'onStartMultiViewSetup\s*=\s*\{\s*pendingMultiViewChannels\s*=\s*emptyList\(\)\s*showMultiViewSetup\s*=\s*true\s*\}'
    new_live_cb = r"""onStartMultiViewSetup = if (multiViewEnabled) {
                                    {
                                        pendingMultiViewChannels = emptyList()
                                        showMultiViewSetup = true
                                    }
                                } else null"""
    content = re.sub(old_live_cb, new_live_cb, content)

    # 14. Avoid invalid feature work after disablement (Search)
    old_search_effect = r'LaunchedEffect\(searchQuery,\s*activeTab\)\s*\{\s*if\s*\(searchQuery\.isBlank\(\)\)\s*\{\s*searchResult\s*=\s*SearchResults\(\)\s*return@LaunchedEffect\s*\}'
    new_search_effect = r"""LaunchedEffect(searchQuery, activeTab) {
                            if (!profile.features.searchEnabled || searchQuery.isBlank()) {
                                searchResult = SearchResults()
                                return@LaunchedEffect
                            }"""
    content = re.sub(old_search_effect, new_search_effect, content)

    return content

patch_file('app/src/main/java/com/example/ui/screens/HomeScreen.kt', patch_homescreen)
print("HomeScreen patched")
