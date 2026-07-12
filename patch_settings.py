import re
with open("app/src/main/java/com/example/ui/feature/settings/SettingsView.kt", "r") as f:
    content = f.read()

# Add onProfileSelected to signature
content = re.sub(
    r'fun SettingsView\(\s*profile: com\.example\.config\.ProviderProfile,\s*repository: IptvRepository,',
    r'fun SettingsView(\n    profile: com.example.config.ProviderProfile,\n    onProfileSelected: (com.example.config.ProviderProfile) -> Unit,\n    repository: IptvRepository,',
    content
)

# In the debug-only provider switcher, replace:
# ProviderConfigRegistry.currentProfile = prof
# with:
# onProfileSelected(prof)
content = re.sub(
    r'ProviderConfigRegistry\.currentProfile\s*=\s*prof',
    r'onProfileSelected(prof)',
    content
)

with open("app/src/main/java/com/example/ui/feature/settings/SettingsView.kt", "w") as f:
    f.write(content)
