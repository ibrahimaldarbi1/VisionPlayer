import re

with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "r") as f:
    content = f.read()

# I need to find the LaunchedEffect for search
search_effect_regex = r'LaunchedEffect\(searchQuery, navigationState\.activeDestination, liveState\.categories, categoriesMovie, categoriesSeries\) \{\s*if \(searchQuery\.isNotEmpty\(\)\) \{'

replacement = """LaunchedEffect(searchQuery, navigationState.activeDestination, liveState.categories, categoriesMovie, categoriesSeries) {
        if (!profile.features.searchEnabled || searchQuery.isBlank()) {
            searchResult = SearchResults()
            return@LaunchedEffect
        }
        if (searchQuery.isNotEmpty()) {"""

content = re.sub(search_effect_regex, replacement, content)

with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "w") as f:
    f.write(content)
