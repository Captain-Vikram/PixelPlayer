# PixelPlayer - Extensions Feature Porting & Bugs TODO List

This document lists the remaining features from the newer version (`b080ae5f`) that need to be ported, tested, and merged into the main branch, along with outstanding extension bugs.

## Current Fixes Applied (in this branch)
- [x] **R8/Proguard Release Build Fix:** Disabled R8 Full Mode and added keep rules for `kotlin.**` standard library classes to prevent dynamic loading crashes.
- [x] **Login WebView Improvements:** Extended login WebView timeouts to 5 minutes, added WebChromeClient multiple-window (`window.open`) popup redirection, and modernized the User Agent string.
- [x] **Post-Login Feed Auto-Refresh:** Added caching invalidation (`clearCache`) to the repository and ViewModels so the feed automatically updates upon a successful login.

## Current Problems with this Version (e5fd0c92)
1. **Spotify Feed Loading Failures ("Unauthorized Request"):** Even after the user successfully logs into Spotify and sees their account overview inside the WebView, the home feed fails to load and returns an `"unauthorized request"` error.
2. **Missing Login State Indicators:** The UI does not show whether an extension is logged in or not, making it difficult for the user to understand if they need to authorize.
3. **No Mixes Integration:** The native mixes ("Your Mix" and "Daily Mix") only show local tracks and do not pull recommendations from active extensions.
4. **Playback Source Override Glitches:** Switching music sources (e.g. from local to extension) during active playback causes conflicts or fails because of the strict source override loop in `PlayerViewModel`.
5. **Loader Instance Desync:** Room database updates or hot-reloads of extensions do not synchronize the class instances in the active loader, causing outdated extension references.

## Outstanding Bugs to Investigate
- [ ] **Spotify Extension Unauthorized Request:** Investigate why the Spotify extension fails to load feeds and throws an `"unauthorized request"` error, even after successful web login (account overview visible).

## Remaining Features to Port from Newer Version (`b080ae5f`)
- [ ] **UI Reordering (Home Screen):** Move the "Extension Shelves" section below the native "Your Mix" and "Daily Mix" sections.
- [ ] **Mixes Integration:** Update native mixes ("Your Mix" & "Daily Mix") to fetch tracks directly from the active music extension (falling back to local files when offline).
- [ ] **Login Status Badges:** Display `"Logged In"` or `"Login Required"` indicators in the source selection bottom sheet next to extensions.
- [ ] **Active Playback Source Switching:** Port the source override fix in `PlayerViewModel` to allow seamless switching of sources during active playback.
- [ ] **Extension Loader State Synchronization:** Update the active extension reference automatically when the underlying classes or configurations are updated.
