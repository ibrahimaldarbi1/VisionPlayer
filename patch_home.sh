cat << 'INNER' > patch.kt
            if (identity != currentIdentity) {
                println("Identity mismatch: \$identity != \$currentIdentity")
                return@launch
            }
            if (!identity.favoritesEnabled || !identity.relevantContentEnabled) {
                println("Not enabled: fav=\${identity.favoritesEnabled} rel=\${identity.relevantContentEnabled}")
                return@launch
            }
            
            println("Adding favorite")
INNER
sed -i '/if (identity != currentIdentity) return@launch/,/if (!identity.favoritesEnabled || !identity.relevantContentEnabled) return@launch/c\            if (identity != currentIdentity) { println("Identity mismatch: $identity != $currentIdentity"); return@launch }\n            if (!identity.favoritesEnabled || !identity.relevantContentEnabled) { println("Not enabled"); return@launch }\n            println("Adding favorite: " + favorite)' app/src/main/java/com/example/ui/screens/HomeViewModel.kt
