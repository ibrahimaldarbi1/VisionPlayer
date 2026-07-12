import re
with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "r") as f:
    content = f.read()

content = re.sub(
    r'AppDestination\.SETTINGS -> SettingsView\(\s*profile = profile,\s*repository = repository,',
    r'AppDestination.SETTINGS -> SettingsView(\n                        profile = profile,\n                        onProfileSelected = onProfileSelected,\n                        repository = repository,',
    content
)

with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "w") as f:
    f.write(content)
