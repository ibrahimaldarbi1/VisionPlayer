with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

if "import com.example.ui.feature.shell.FeatureAvailabilityPolicy" not in content:
    content = content.replace("import com.example.ui.theme.AppTheme", "import com.example.ui.theme.AppTheme\nimport com.example.ui.feature.shell.FeatureAvailabilityPolicy")

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
