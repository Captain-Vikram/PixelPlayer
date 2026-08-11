# PixelPlayer — Captain-Vikram Fork 🎵

<p align="center">
  <img src="assets/icon.png" alt="App Icon" width="128"/>
</p>

<p align="center">
  <strong>A beautiful, feature-rich music player for Android</strong><br>
  Built with Jetpack Compose and Material Design 3
</p>

<p align="center">
  <img src="assets/screenshot1.jpg" alt="Screenshot 1" width="200" style="border-radius:26px;"/>
  <img src="assets/screenshot2.jpg" alt="Screenshot 2" width="200" style="border-radius:26px;"/>
  <img src="assets/screenshot3.jpg" alt="Screenshot 3" width="200" style="border-radius:26px;"/>
  <img src="assets/screenshot4.jpg" alt="Screenshot 4" width="200" style="border-radius:26px;"/>
</p>

<p align="center">
    <a href="https://github.com/Captain-Vikram/PixelPlayer/releases/latest">
        <img src="https://img.shields.io/github/v/release/Captain-Vikram/PixelPlayer?include_prereleases&logo=github&style=for-the-badge&label=Latest%20Release" alt="Latest Release">
    </a>
    <a href="https://github.com/Captain-Vikram/PixelPlayer/releases">
        <img src="https://img.shields.io/github/downloads/Captain-Vikram/PixelPlayer/total?logo=github&style=for-the-badge" alt="Total Downloads">
    </a>
    <img src="https://img.shields.io/badge/Android-11%2B-green?style=for-the-badge&logo=android" alt="Android 11+">
    <img src="https://img.shields.io/badge/Kotlin-100%25-purple?style=for-the-badge&logo=kotlin" alt="Kotlin">
</p>

---

## 🙏 Acknowledgements & Credits

This project would not exist without the incredible work of two communities:

### 🎨 PixelPlayer — Original App & UI
All of the stunning frontend, UI design, animations, architecture, and core music-player features were created by **[theovilardo](https://github.com/theovilardo)** and the **[PixelPlayer contributors](https://github.com/theovilardo/PixelPlayer)**. Every screen you see, every smooth transition, and the beautiful Material You theming is their work.

> 🔗 **Original repository:** [github.com/theovilardo/PixelPlayer](https://github.com/theovilardo/PixelPlayer)
>
> A huge **thank you** to theovilardo and the entire PixelPlayer community for building and open-sourcing such a polished, production-quality music player. Your dedication to quality and design is evident in every pixel.

### 🔌 Echo Community — Extension Architecture
The powerful **extension system** that lets PixelPlayer load third-party streaming plugins (Spotify, YouTube Music, etc.) is inspired by and built upon the work of the **[Echo](https://github.com/brahmkshatriya/echo)** project and its community.

> 🔗 **Echo repository:** [github.com/brahmkshatriya/echo](https://github.com/brahmkshatriya/echo)
>
> A heartfelt **thank you** to the Echo team and community for pioneering the dynamic APK extension architecture (`DexClassLoader`-based plugin loading). Their open design allowed PixelPlayer to build a full extension ecosystem on top of it. The extension loader module in this fork is directly inspired by Echo's architecture.

---

## 🆕 What's New In This Fork (Captain-Vikram's Contributions)

This fork builds on the upstream PixelPlayer with a focused set of improvements in **three areas**: extension system stability, architecture modularization, and APK size reduction.

### 🔌 Extension System — Major Fixes & Improvements

The extension system (which allows online streaming from services like Spotify, YouTube Music, etc.) received a comprehensive overhaul:

| Fix | Description |
|-----|-------------|
| **R8/ProGuard Release Fix** | Disabled R8 Full Mode and added keep rules for `kotlin.**` to prevent dynamic class loading crashes in release builds |
| **Login WebView** | Extended login timeouts to 5 minutes, added `window.open` popup redirection support, modernized the User Agent |
| **Post-Login Feed Refresh** | Added cache invalidation so the home feed auto-refreshes immediately after a successful login |
| **Login Status Badges** | Source selection popup now shows `"Logged In"` / `"Login Required"` badges next to extension versions |
| **Spotify Cookie Fix** | Enabled third-party cookies + `CookieManager.flush()` to prevent unauthorized errors after Spotify OAuth |
| **Duplicate Shelf Fix** | Resolved duplicate shelves (artist/playlist/favorites repeating) after login via title-based deduplication in `ExtensionRepository` |
| **Native Mixes Integration** | "Your Mix" and "Daily Mix" now pull songs from the active extension's feed when an extension is active |
| **Library Tab Hiding** | Library tabs (Songs/Albums/Artists) gracefully hide when an extension is active |
| **Background Extraction** | Tracks are extracted from Album/Playlist shelves in the background via `PlaylistClient`/`AlbumClient` to populate native mixes |
| **Pre-Login Guest Feed** | Home feed loads guest/public content before login; suppresses 401 errors; shows a brand-colored "Connect [Extension]" banner |
| **Quick Picks Grid** | Single-item shelves (Discover Weekly, radios, custom mixes) are grouped into a beautiful 2-column "Quick Picks" grid with time-based salutations |
| **Smart Image Scaling** | `SmartImage.kt` auto-detects landscape thumbnails (e.g. YouTube Music video covers) and uses `ContentScale.Fit` instead of cropping |
| **Source Switching Bug** | Source chooser popup now only lists active/enabled extensions; caches and shelves clear instantly on transition |
| **Hide UI in Extensions Screen** | Bottom nav bar and mini-player are hidden when navigating to Extensions, Extension Settings, and Extension Login screens |
| **Icon Consistency** | Source popup now uses `Icons.Rounded.Storage` consistently throughout |
| **"Manage Extensions" Rename** | Renamed "Manage Sources" → "Manage Extensions" and moved it under the Extensions header in the popup |

### 🏗️ Architecture Modularization

Extracted heavy, optional features into isolated Gradle modules to reduce the base APK size and improve maintainability:

- **Stage 1** — Extracted `StreamProxy` interface + TDLib native library exclusions + ProGuard contracts into `:core:common`
- **Stage 2** — Isolated Telegram's Hilt module into its own Gradle module; removed redundant dependencies
- **Stage 3** — Moved all stream proxies to a dedicated ProGuard configuration  
- **Stage 4** — Extracted `:feature:ktor-server` as a standalone Gradle module (Ktor/Netty server for Chromecast/local streaming)
- **Telegram Extraction** — Extracted the Telegram/TDLib integration (previously ~30–50MB of `.so` libraries) into an optional dynamic plugin, keeping the base APK lean

### 📦 Unit Tests

Added comprehensive unit tests for:
- Extension track cache behavior
- Source selection logic  
- Worker sync correctness (`SyncWorkerTest`)

---

## ✨ All Features (Upstream + Fork)

### 🎨 Modern UI/UX
- **Material You** — Dynamic color theming that adapts to your wallpaper
- **Smooth Animations** — Fluid transitions and micro-interactions with Material 3 Expressive motion curves
- **Customizable UI** — Adjustable corner radius and navigation bar settings
- **Dark/Light Theme** — Automatic or manual theme switching
- **Album Art Colors** — Dynamic color extraction from album artwork

### 🎵 Powerful Playback
- **Media3 ExoPlayer** — Industry-leading audio engine with FFmpeg support
- **Background Playback** — Full media session integration
- **Queue Management** — Drag-and-drop reordering
- **Shuffle & Repeat** — All playback modes supported
- **Gapless Playback** — Seamless transitions between tracks
- **Custom Transitions** — Configure crossfades between songs
- **MIDI, ALAC/M4A/Opus** — Full audio format support overhaul

### 📚 Library Management
- **Multi-format Support** — MP3, FLAC, AAC, OGG, WAV, and more
- **Browse By** — Songs, Albums, Artists, Genres, Folders
- **Smart Artist Parsing** — Configurable delimiters for multi-artist tracks
- **Album Artist Grouping** — Proper album organization
- **Folder Filtering** — Choose which directories to scan

### 🔍 Discovery & Organization
- **Full-text Search** — Search across your entire library and active extensions
- **Daily Mix** — AI-powered personalized playlist based on listening habits
- **Playlists** — Create and manage custom playlists
- **Extension System** — Dynamically load plugins for online streaming ([Learn more](EXTENSIONS.md))
- **Download Manager** — Centralized management for offline content from extensions
- **Statistics** — Track your listening history and habits

### 🎤 Lyrics
- **Synchronized Lyrics** — LRC format via LRCLIB API
- **Lyrics Editing** — Modify or add lyrics to your tracks
- **Scrolling Display** — Follow along as you listen
- **AI Translation** — Translate lyrics with AI; romanization for CJK scripts

### 🖼️ Artist Artwork
- **Deezer Integration** — Automatic artist images from Deezer API
- **Smart Caching** — Memory (LRU) + database caching for offline access
- **Fallback Icons** — Beautiful placeholders when images unavailable

### 📲 Connectivity
- **Chromecast** — Stream to your TV or smart speakers
- **Android Auto** — Full Android Auto support for in-car playback (Soon)
- **Widgets** — Home screen control with Glance widgets
- **Wear OS** — Music transfer, local playback, queue sync, and remote control from the watch
- **Cloud** — Google Drive, Jellyfin, Navidrome/Subsonic streaming

### ⚙️ Advanced Features
- **Tag Editor** — Edit metadata with TagLib (MP3, FLAC, M4A support)
- **AI Playlists** — Generate playlists with AI (Gemini, Groq, DeepSeek, OpenAI, OpenRouter)
- **10-Band Equalizer** — Full effects suite
- **M3U Import/Export** — Standard playlist format support

---

## ⬇️ Download This Fork

<p align="center">
  <a href="https://github.com/Captain-Vikram/PixelPlayer/releases/latest">
    <img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" alt="Get it on GitHub" height="60">
  </a>
</p>

### Which APK should I download?

| File | Device Type |
|------|-------------|
| `app-arm64-v8a-release.apk` | **Most modern Android phones** (2017 and newer) — use this one |
| `app-armeabi-v7a-release.apk` | Older 32-bit devices |

> **Note:** These APKs are built and signed automatically by GitHub Actions on every push to `master`. They are **not** signed with the official PixelPlayer key — you will not be able to mix-install with the official release.

### Get Automatic Updates via Obtainium

<p align="center">
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.theveloper.pixelplay%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FCaptain-Vikram%2FPixelPlayer%22%2C%22author%22%3A%22Captain-Vikram%22%2C%22name%22%3A%22PixelPlayer%20(Captain-Vikram%20Fork)%22%2C%22supportFixedAPKURL%22%3Afalse%7D">
    <img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="50">
  </a>
</p>

---

## 🛠️ Tech Stack

| Category | Technology |
|----------|------------|
| **Language** | [Kotlin](https://kotlinlang.org/) 100% |
| **UI Framework** | [Jetpack Compose](https://developer.android.com/jetpack/compose) |
| **Design System** | [Material Design 3](https://m3.material.io/) |
| **Audio Engine** | [Media3 ExoPlayer](https://developer.android.com/guide/topics/media/media3) + FFmpeg |
| **Architecture** | MVVM with StateFlow/SharedFlow |
| **DI** | [Hilt](https://dagger.dev/hilt/) |
| **Database** | [Room](https://developer.android.com/training/data-storage/room) |
| **Networking** | [Retrofit](https://square.github.io/retrofit/) + OkHttp |
| **Image Loading** | [Coil](https://coil-kt.github.io/coil/) |
| **Async** | Kotlin Coroutines & Flow |
| **Background Tasks** | WorkManager |
| **Metadata** | [TagLib](https://github.com/nicholaus/taglib-android) |
| **Widgets** | [Glance](https://developer.android.com/jetpack/compose/glance) |
| **Extension Loader** | Custom DexClassLoader (inspired by Echo) |

---

## 📱 Requirements

- **Android 11** (API 30) or higher
- **4GB RAM** minimum; **6GB RAM** recommended for smooth performance

---

## 🚀 Building From Source

### Prerequisites

- Android Studio Ladybug | 2024.2.1 or newer
- Android SDK 30+
- JDK 21

### Build & Run

```sh
# Clone this fork
git clone https://github.com/Captain-Vikram/PixelPlayer.git
cd PixelPlayer

# Build a debug APK
./gradlew :app:assembleDebug

# Install directly to a connected device
./gradlew :app:installDebug

# Or manually via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch the app
adb shell monkey -p com.theveloper.pixelplay -c android.intent.category.LAUNCHER 1
```

### Staying In Sync With Upstream

```sh
# (One-time) Add the official PixelPlayer as upstream
git remote add upstream https://github.com/theovilardo/PixelPlayer.git

# Fetch latest upstream changes
git fetch upstream

# Rebase your changes on top (keeps history linear, avoids build/ conflicts)
git rebase upstream/master

# Push to your fork
git push origin master --force-with-lease
```

---

## 📂 Project Structure

```
PixelPlayer/
├── app/                        # Main Android app module
│   └── src/main/java/com/theveloper/pixelplay/
│       ├── data/               # DB, models, network, repositories, services, workers
│       ├── di/                 # Hilt DI modules
│       ├── presentation/       # Compose screens, ViewModels, components
│       └── ui/                 # Widgets, theme
├── core/
│   └── common/                 # Shared interfaces (StreamProxy, TelegramClientProvider)
├── feature/
│   └── ktor-server/            # Optional Ktor/Netty HTTP server module
├── extension-loader/           # Dynamic APK/DEX plugin loading engine
├── shared/                     # Extension SDK interfaces (Echo-compatible)
├── wear/                       # Wear OS companion module
└── docs/                       # Performance & architecture reports
```

---

## 📄 Documentation

| Document | Description |
|----------|-------------|
| [CHANGELOG.md](CHANGELOG.md) | Full version history |
| [EXTENSIONS.md](EXTENSIONS.md) | How to use & develop extensions |
| [TODO.md](TODO.md) | Remaining features & known issues |
| [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) | License notices for MIT-era contributions |
| [docs/performance-report.md](docs/performance-report.md) | Streaming & cache optimization analysis |

---

## ‼️ Disclaimer

- This is an **unofficial fork**. For issues specific to this fork's changes, please open an issue here.
- For support on the original PixelPlayer features, visit the [official repository](https://github.com/theovilardo/PixelPlayer).
- APKs distributed here are **not signed with the official key**. Do not mix-install with the official release.

---

## 📄 License

This project is licensed under a Proprietary License — see the [LICENSE](LICENSE) file for details.

Portions contributed before 2026-05-12 remain available under the MIT License; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

---

<p align="center">
  Original app made with ❤️ by <a href="https://github.com/theovilardo">theovilardo</a> &amp; the PixelPlayer community<br>
  Fork maintained by <a href="https://github.com/Captain-Vikram">Captain-Vikram</a>
</p>
