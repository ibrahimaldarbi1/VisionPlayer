import os
import re

def patch_file(filepath, callback):
    with open(filepath, 'r') as f:
        content = f.read()
    new_content = callback(content)
    with open(filepath, 'w') as f:
        f.write(new_content)

def patch_main_activity(content):
    # Fix 1: Make MainActivity own the active profile state
    # Replace:
    # val currentProfile = ProviderConfigRegistry.currentProfile
    # var appProfileState by remember { mutableStateOf(currentProfile) }
    # With:
    # var appProfileState by remember { mutableStateOf(ProviderConfigRegistry.currentProfile) }
    
    content = re.sub(
        r'val currentProfile\s*=\s*ProviderConfigRegistry\.currentProfile\s*var appProfileState\s*by\s*remember\s*\{\s*mutableStateOf\(\s*currentProfile\s*\)\s*\}',
        r'var appProfileState by remember { mutableStateOf(ProviderConfigRegistry.currentProfile) }',
        content,
        flags=re.MULTILINE
    )

    # 12. Use centralized policy in MainActivity player action
    # Replace: appProfileState.features.multiViewEnabled with FeatureAvailabilityPolicy.shouldShowMultiView(appProfileState.features)
    content = re.sub(
        r'appProfileState\.features\.multiViewEnabled',
        r'FeatureAvailabilityPolicy.shouldShowMultiView(appProfileState.features)',
        content
    )
    
    # Add import for FeatureAvailabilityPolicy if not present
    if 'import com.example.ui.feature.shell.FeatureAvailabilityPolicy' not in content:
        content = content.replace(
            'import com.example.ui.theme.AppTheme',
            'import com.example.ui.theme.AppTheme\nimport com.example.ui.feature.shell.FeatureAvailabilityPolicy'
        )

    # Update HomeScreen call
    # Replace:
    # HomeScreen(
    #     repository = repository,
    content = re.sub(
        r'HomeScreen\(\s*repository = repository,',
        r'HomeScreen(\n                                    profile = appProfileState,\n                                    onProfileSelected = {\n                                        selectedProfile ->\n                                        ProviderConfigRegistry.currentProfile = selectedProfile\n                                        appProfileState = selectedProfile\n                                    },\n                                    repository = repository,',
        content
    )
    
    return content

patch_file('app/src/main/java/com/example/MainActivity.kt', patch_main_activity)
print("MainActivity patched")
