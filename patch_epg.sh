sed -i 's/val identity = EpgRefreshIdentity(/epgRefreshGeneration++\n        val identity = EpgRefreshIdentity(/g' app/src/main/java/com/example/ui/feature/settings/SettingsViewModel.kt
