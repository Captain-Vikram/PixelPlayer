# PixelPlayer - Extensions Feature Porting & Bugs TODO List

This document lists the remaining features from the newer version (`b080ae5f`) that need to be ported, tested, and merged into the main branch, along with outstanding extension bugs.

## Current Fixes Applied (in this branch)
- [x] **R8/Proguard Release Build Fix:** Disabled R8 Full Mode and added keep rules for `kotlin.**` standard library classes to prevent dynamic loading crashes.
- [x] **Login WebView Improvements:** Extended login WebView timeouts to 5 minutes, added WebChromeClient multiple-window (`window.open`) popup redirection, and modernized the User Agent string.
- [x] **Post-Login Feed Auto-Refresh:** Added caching invalidation (`clearCache`) to the repository and ViewModels so the feed automatically updates upon a successful login.
- [x] **Source Selection Login Status Badges:** Display `"Logged In"` or `"Login Required"` badges next to extension versions in the bottom sheet.
- [x] **Spotify Login Cookie Fix:** Enabled third-party cookies and added `flush()` to CookieManager to prevent `"unauthorized request"` errors after Spotify OAuth login.
- [x] **Spotify/Extension Duplicate Shelves:** Resolved duplicate shelves (e.g. repeating artist, playlist, or favorite shelves) appearing on the Home page and Library tab after user login by implementing title-based deduplication in `ExtensionRepository.kt`.
- [x] **Native Mixes Extension Integration:** Updated "Your Mix" and "Daily Mix" components on the Home Screen to fetch and show songs from the active extension's feed when an extension is active.
- [x] **Non-Functional Library Tabs Hiding:** Gracefully hide the top tab bar ("Songs", "Albums", "Artists") and compact pill switcher in the Library tab when an extension is active.
- [x] **Background Album/Playlist Mixes Extraction:** Scan and extract tracks from shelves containing Albums and Playlists (by loading their tracks in the background via `PlaylistClient` and `AlbumClient`) to populate the native "Your Mix" and "Daily Mix" lists universally across all extensions.
- [x] **General Feed Before Login Support:** Gracefully handle home feed loading before login. Emits guest/public feeds if available. Suppress auth-related 401 error popups. Fall back to local library mixes, and display a beautiful, brand-colored "Connect [Extension]" banner prompting the user to login.
- [x] **Good Morning/Evening Quick Picks Grid:** Dynamically identify single-item shelves (like custom mixes, radios, or Discover Weekly) and group them into a compact, beautiful 2-column "Quick Picks" grid at the top of the feed (with time-based salutations "Good morning", "Good afternoon", "Good evening"), matching Spotify's home screen architecture.
- [x] **Aspect Ratio-Aware Dynamic Image Scaling:** Implemented automatic aspect ratio inspection in `SmartImage.kt`. If a thumbnail is landscape-oriented (like YouTube Music video covers), it automatically scales to `ContentScale.Fit` (leaving clean top/bottom bars) rather than stretching/cropping to 1:1, resolving layout distortions.
- [x] **Switching Bug in Choose-Your-Source Popup:** Filter the displayed extension list to only include active/enabled extensions. Additionally, clear caches, shelves, and native mixes instantly in `ExtensionRepository.kt` on source transition to avoid loading stale UI.
- [x] **Hide Miniplayer and Nav Bar in Extensions Screen:** Hide the bottom navigation bar and the player sheet (miniplayer) when navigating to the Extensions, Extension Settings, and Extension Login screens.
- [x] **Source Popup Icon Consistency:** Replaced the home top-right local library indicator icon (`Icons.Rounded.AutoAwesome`) with `Icons.Rounded.Storage` to align with the pop-up icon.
- [x] **Manage Sources Button Relocation & Rename:** Renamed "Manage Sources" to "Manage Extensions" and moved it directly under the "Extensions" header in the pop-up menu.

## Current Problems & Bugs to Investigate
*None currently outstanding. All reported bugs are addressed and ready for testing.*

## Remaining Features to Port from Newer Version (`b080ae5f`)
- [ ] **Active Playback Source Switching:** Port the source override fix in `PlayerViewModel` to allow seamless switching of sources during active playback.
- [ ] **Extension Loader State Synchronization:** Update the active extension reference automatically when the underlying classes or configurations are updated.
- [x] **UI Reordering (Home Screen) [CANCELLED]:** Keep older layout (mixes below extension shelves in extension mode, but top in local mode) as explicitly requested by the user.

---

## ⚡ Performance, Streaming & Cache Tuning Areas of Improvement
To eliminate audio buffering lags and optimize playback/caching, the following enhancements should be investigated and added to the roadmap:

### 1. Network Protocol & DataSource Modernization
*   **Implement OkHttpDataSource:** Replace Media3's default `HttpURLConnection` datasource (which lacks HTTP/2, connection multiplexing, and advanced pooling) with `OkHttpDataSource.Factory`. Using a customized, shared `OkHttpClient` instance will dramatically reduce the Connection Setup Latency (Time-To-First-Byte) for remote streams.
*   **DNS & Connection Optimizations:** Configure custom DNS resolution (like DNS-over-HTTPS or Cloudflare DNS) and adjust TCP keep-alive settings in the OkHttpClient to speed up media segment requests on poor mobile networks.

### 2. Pre-emptive Background JIT Resolution (Next Track Pre-Caching)
*   **Background URL Prefetching:** Currently, the player resolves stream URLs synchronously on-demand via `runBlocking` inside `ResolvingDataSource` when the track changes. This causes a noticeable pause between songs. 
*   **Implementation:** Pre-resolve the next song's cloud URI in the background *20-30 seconds before* the current song ends, and write it to a fast local memory cache (e.g., `resolvedUriCache` or `rawSourceMap`). This will allow ExoPlayer to transition instantly (0ms resolution overhead).

### 3. Dual-Player Crossfade Caching Strategy
*   **Transition Player Buffering:** Because `DualPlayerEngine` uses two separate ExoPlayer instances for crossfades, standard single-player gapless queuing is bypassed. 
*   **Implementation:** Trigger background pre-buffering on the `transitionPlayer` using the pre-resolved URL before starting the crossfade, ensuring the crossfading song is ready to stream instantly without silent gaps.

### 4. SimpleCache & Buffer Sizes Tuning
*   **Write/Read Buffer Tuning:** Customise the buffer and block sizes in `CacheDataSource.Factory` to maximize memory-to-disk write throughput.
*   **Dynamic Cache Eviction:** Review `LeastRecentlyUsedCacheEvictor` parameters and expand disk caches specifically for extensions on high-end devices.
