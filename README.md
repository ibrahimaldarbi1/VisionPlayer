# White-Label IPTV Player (Android & Android TV)

A feature-rich, high-performance, single-codebase **White-Label IPTV Player** built natively for **Android Phones** and **Android TV / Fire TV** devices. 

The application is a **player only**. It contains no built-in streams or TV channels out-of-the-box. Legal IPTV content and EPG parameters are loaded dynamically via provider sessions or white-label server profiles.

---

## 🚀 Key Architectural highlights
* **Unified Codebase**: Dual UI layouts (standard Jetpack Compose for touch screens and Android TV Leanback-style D-pad focus grids) in a single module.
* **Hidden-Feature Rule**: Fully rigorous compliance with hiding any disabled parameters. Toggling off VOD (Movies or Series) or EPG completely purges related tabs, carousel shelves, search suggestions, favorites menus, and settings configurations.
* **Modern Media3 Playback**: Google's modern `media3-exoplayer` container with custom overlays, aspect ratio scaling (Fit/Zoom/Stretch), error recovery, and hardware decoding support.
* **Reactive Cache Core**: Jetpack Room SQLite storage engine for fast, offline-ready favorites, recently watched channel logs, parental PIN controls, and VOD continue watching progress overlays.

---

## 🛠️ White-Label Branding Setup

Each provider APK is configured via a Kotlin configuration file. For version 1, all active configurations are maintained in:
`app/src/main/java/com/example/config/ProviderConfig.kt`

### 1. Customizing App Profile details
Edit the active `ProviderProfile` structures in `ProviderConfig.kt`. Here is the core layout structure:

```kotlin
data class ProviderProfile(
    val id: String,
    val name: String,
    val appName: String,
    val features: FeatureConfig,
    val branding: BrandingConfig,
    val support: SupportConfig,
    val layoutMode: LayoutMode
)
```

### 2. Enabling & Disabling Features (Hidden-Feature Compliance)
Feature flags are declared under `FeatureConfig`. Setting a flag to `false` instantly hides the feature across all menus, navigation rails, detail views, and search entries:

```kotlin
data class FeatureConfig(
    val liveTvEnabled: Boolean = true,
    val moviesEnabled: Boolean = true,
    val seriesEnabled: Boolean = true,
    val epgEnabled: Boolean = true,
    val searchEnabled: Boolean = true,
    val favoritesEnabled: Boolean = true,
    val recentlyWatchedEnabled: Boolean = true,
    val continueWatchingEnabled: Boolean = true,
    val parentalControlEnabled: Boolean = true,
    val supportPageEnabled: Boolean = true,
    val updateCheckerEnabled: Boolean = true
)
```

### 3. Theme Colors & Typography Customization
Branding colors are declared using hex color values in `BrandingConfig`. The Material 3 dynamic color theme dynamically updates all highlight indicators, slider track states, switches, buttons, and layouts to adapt to the configured brand colors:

```kotlin
data class BrandingConfig(
    val primaryColor: Long = 0xFFE50914,      // Main Accent Branded Color
    val secondaryColor: Long = 0xFFB81D24,    // Secondary Accent
    val backgroundColor: Long = 0xFF000000,   // Canvas Dark background
    val surfaceColor: Long = 0xFF141414,      // Surface containers
    val logoText: String = "NEBULA STREAM",
    val splashText: String = "Welcome Screen Slogan"
)
```

---

## 📺 Touch vs. Android TV Focus Layouts

### 1. Mobile & Touch Screen Devices
* **Bottom Navigation**: Fluid Material 3 bottom navigation tab bar that handles notches and navigation bar safe insets.
* **Touch-Optimized Sliders**: Screen gesture volume, seek sliders, and responsive carousel swiping.
* **Auto-Orientation**: Responsive transition between portrait listing layout and fullscreen landscape media playback.

### 2. Android TV & Fire TV Box Controls
* **D-pad Navigation Rail**: A left-aligned, vertical Leanback menu layout that listens to TV remote key-presses.
* **Focus Highlighting**: Custom cards with white border highlights when receiving remote focus, maintaining an absolute visual indicator.
* **Dedicated Player Key-binds**:
  * `OK / SELECT`: Toggle HUD player action bar overlay or pause/play content.
  * `DPAD LEFT / RIGHT`: Seek backward/forward 10 seconds (VOD only).
  * `DPAD UP / DOWN`: Fast channel-up / channel-down switching (Live TV only).
  * `BACK`: Dismiss HUD overlays, or return to channel grid categories.

---

## 🔒 Parental Protection Code system

* **Passcode Block**: Setting a 4-digit numeric PIN restricts access to adult-labeled categories.
* **Adult content handling**:
  * **LOCKED Mode**: Mature categories are visible in lists but prompt for a passcode challenge before playback starts.
  * **HIDDEN Mode**: Fully purges adult content, removing mature streams from search listings, lists, and Home shelves.

---

## 🧪 Quick Test Showcase & Demo Swapping

We have bundled 3 highly polished branded profiles directly inside the APK for convenient review:
1. **Nebula Premium** (Netflix-Red theme, Premium layout mode, all Live, VOD, and EPG modules enabled).
2. **Retro IPTV** (Cyan-Blue theme, Classic layout mode, Live TV + EPG only. **VOD/Movies/Series are fully hidden** everywhere).
3. **Cosmic VOD** (Purple theme, Simple layout mode, VOD Movies + Series only. **Live TV & EPG are fully hidden** everywhere).

### To Test Dynamic Feature-Hiding:
1. Launch the app on mobile or TV.
2. Sign in using any dummy credentials (e.g., Server: `https://demo.iptvserver.net`, User: `demo_user`, Pass: `demo_pass`).
3. Click on the **Settings/More** tab.
4. Click on **White-Label Provider Profile**.
5. Select any of the 3 profiles.
6. **Watch the entire app's navigation tabs, background colors, buttons, stream feeds, and menus instantly reconstruct and change in real-time, completely hiding the disabled features!**

---

## 🚧 Removing Mock Data before Production

To distribute your branded client player in production, clean the demo mock entries:
1. Open `app/src/main/java/com/example/data/IptvModels.kt`.
2. Locate the `IptvMockData` object.
3. Replace the local streams under `LiveChannels`, `Movies`, and `SeriesList` with your legal Xtream Codes server request parser or XMLTV stream loader.
4. Set the default server URL in `LoginScreen.kt` to your fixed provider portal gateway to restrict user entry fields if needed.
