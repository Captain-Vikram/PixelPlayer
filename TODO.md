# PixelPlayer - Extensions Feature Porting & Bugs TODO List

This document lists the remaining features from the newer version (`b080ae5f`) that need to be ported, tested, and merged into the main branch, along with outstanding extension bugs.

## Current Fixes Applied (in this branch)
- [x] **R8/Proguard Release Build Fix:** Disabled R8 Full Mode and added keep rules for `kotlin.**` standard library classes to prevent dynamic loading crashes.
- [x] **Login WebView Improvements:** Extended login WebView timeouts to 5 minutes, added WebChromeClient multiple-window (`window.open`) popup redirection, and modernized the User Agent string.
- [x] **Post-Login Feed Auto-Refresh:** Added caching invalidation (`clearCache`) to the repository and ViewModels so the feed automatically updates upon a successful login.
- [x] **Source Selection Login Status Badges:** Display `"Logged In"` or `"Login Required"` badges next to extension versions in the bottom sheet.
- [x] **Spotify Login Cookie Fix:** Enabled third-party cookies and added `flush()` to CookieManager to prevent `"unauthorized request"` errors after Spotify OAuth login.

## Current Problems & Bugs to Investigate
- [ ] **Spotify/Extension Duplicate Shelves:** Investigate and resolve duplicate shelves (e.g. repeating artist, playlist, or favorite shelves) appearing on the Home page and Library tab after user login. Check extension API responses in Spotify tab to implement a generic deduplication/cleanup mechanism.
- [ ] **Native Mixes Extension Integration & Positioning:**
  - **Data Integration:** Update "Your Mix" and "Daily Mix" components on the Home Screen to fetch and show songs from the active extension's feed rather than falling back to local files when an extension is active.
  - **UI Positioning:** Ensure native mixes are positioned correctly depending on the mode (at the top in local library mode, and moved down or handled cleanly when extensions are active).
- [ ] **Non-Functional Library Tabs:** Hide the top tab bar ("Songs", "Albums", "Artists") in the Library tab when an extension is active, as these tabs are non-functional for extensions.

## Remaining Features to Port from Newer Version (`b080ae5f`)
- [ ] **UI Reordering (Home Screen):** Move the "Extension Shelves" section below the native "Your Mix" and "Daily Mix" sections.
- [ ] **Active Playback Source Switching:** Port the source override fix in `PlayerViewModel` to allow seamless switching of sources during active playback.
- [ ] **Extension Loader State Synchronization:** Update the active extension reference automatically when the underlying classes or configurations are updated.
