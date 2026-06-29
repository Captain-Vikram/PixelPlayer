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

## Current Problems & Bugs to Investigate
- [ ] **Background Album/Playlist Mixes Extraction:** Scan and extract tracks from shelves containing Albums and Playlists (by loading their tracks in the background via `PlaylistClient` and `AlbumClient`) to populate the native "Your Mix" and "Daily Mix" lists universally across all extensions.
- [ ] **General Feed Before Login Support:** Gracefully handle home feed loading before login. Ensure public/guest feeds (like public charts, popular releases) load and render without showing disruptive error dialogues. Prompt the user to log in when they try to play a track that requires authentication.

## Remaining Features to Port from Newer Version (`b080ae5f`)
- [ ] **Active Playback Source Switching:** Port the source override fix in `PlayerViewModel` to allow seamless switching of sources during active playback.
- [ ] **Extension Loader State Synchronization:** Update the active extension reference automatically when the underlying classes or configurations are updated.
- [x] **UI Reordering (Home Screen) [CANCELLED]:** Keep older layout (mixes below extension shelves in extension mode, but top in local mode) as explicitly requested by the user.
