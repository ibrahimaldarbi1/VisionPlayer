import re
with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "r") as f:
    content = f.read()

# Replace fun HomeScreen(...) with the new signature
content = re.sub(
    r'fun HomeScreen\(',
    r'import com.example.config.ProviderProfile\n\n@Composable\nfun HomeScreen(\n    profile: ProviderProfile,\n    onProfileSelected: (ProviderProfile) -> Unit,',
    content,
    count=1
)

with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "w") as f:
    f.write(content)
