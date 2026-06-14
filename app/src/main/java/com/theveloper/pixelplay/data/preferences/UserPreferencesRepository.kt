package com.theveloper.pixelplay.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.model.PlaybackQueueSnapshot
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.data.model.SourceScope
import com.theveloper.pixelplay.data.model.StorageFilter
import com.theveloper.pixelplay.data.model.TransitionSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import timber.log.Timber

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val json = Json { ignoreUnknownKeys = true }

    private object PreferencesKeys {
        val SEARCH_HISTORY = stringPreferencesKey("search_history")
        val CURATED_YOUR_MIX = stringPreferencesKey("curated_your_mix")
        val CURATED_DAILY_MIX = stringPreferencesKey("curated_daily_mix")
        val LAST_PLAYBACK_HISTORY_CLEANUP = stringPreferencesKey("last_playback_history_cleanup")
        val LAST_PLAYLIST_ID = stringPreferencesKey("last_playlist_id")
        val LAST_PLAYLIST_NAME = stringPreferencesKey("last_playlist_name")
        val LAST_STORAGE_FILTER = stringPreferencesKey("last_storage_filter")
        val HIDE_LOCAL_MEDIA = booleanPreferencesKey("hide_local_media")
        val TELEGRAM_TOPIC_DISPLAY_MODE = stringPreferencesKey("telegram_topic_display_mode")
        val FOLDERS_SOURCE = stringPreferencesKey("folders_source")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val COLLAGE_PATTERN = stringPreferencesKey("collage_pattern")
        val COLLAGE_AUTO_ROTATE = booleanPreferencesKey("collage_auto_rotate")
        val RECENTLY_PLAYED_LIMIT = intPreferencesKey("recently_played_limit")
        val TRANSITION_SETTINGS = stringPreferencesKey("transition_settings")
        val LYRICS_SOURCE_PREFERENCE = stringPreferencesKey("lyrics_source_preference")
        val BETA_05_CLEAN_INSTALL_DISCLAIMER_DISMISSED = booleanPreferencesKey("beta_05_clean_install_disclaimer_dismissed")
        val MOCK_GENRES_ENABLED = booleanPreferencesKey("mock_genres_enabled")
        val PLAYLIST_SONG_ORDER_MODES = stringPreferencesKey("playlist_song_order_modes")
        val EXTENSION_REGISTRIES = stringSetPreferencesKey("extension_registries")
        val SONGS_SORT_OPTION = stringPreferencesKey("songs_sort_option")
        val NAV_BAR_CORNER_RADIUS = intPreferencesKey("nav_bar_corner_radius")
        val NAV_BAR_STYLE = stringPreferencesKey("nav_bar_style")
        val NAV_BAR_COMPACT_MODE = booleanPreferencesKey("nav_bar_compact_mode")
        val LIBRARY_NAVIGATION_MODE = stringPreferencesKey("library_navigation_mode")
        val CAROUSEL_STYLE = stringPreferencesKey("carousel_style")
        val LAUNCH_TAB = stringPreferencesKey("launch_tab")
        val KEEP_PLAYING_IN_BACKGROUND = booleanPreferencesKey("keep_playing_in_background")
        val DISABLE_CAST_AUTOPLAY = booleanPreferencesKey("disable_cast_autoplay")
        val RESUME_ON_HEADSET_RECONNECT = booleanPreferencesKey("resume_on_headset_reconnect")
        val FULL_PLAYER_DELAY_ALL = booleanPreferencesKey("full_player_delay_all")
        val FULL_PLAYER_DELAY_ALBUM = booleanPreferencesKey("full_player_delay_album")
        val FULL_PLAYER_DELAY_METADATA = booleanPreferencesKey("full_player_delay_metadata")
        val FULL_PLAYER_DELAY_PROGRESS = booleanPreferencesKey("full_player_delay_progress")
        val FULL_PLAYER_DELAY_CONTROLS = booleanPreferencesKey("full_player_delay_controls")
        val FULL_PLAYER_PLACEHOLDERS = booleanPreferencesKey("full_player_placeholders")
        val FULL_PLAYER_PLACEHOLDER_TRANSPARENT = booleanPreferencesKey("full_player_placeholder_transparent")
        val FULL_PLAYER_PLACEHOLDERS_ON_CLOSE = booleanPreferencesKey("full_player_placeholders_on_close")
        val FULL_PLAYER_SWITCH_ON_DRAG_RELEASE = booleanPreferencesKey("full_player_switch_on_drag_release")
        val FULL_PLAYER_DELAY_THRESHOLD = intPreferencesKey("full_player_delay_threshold")
        val FULL_PLAYER_CLOSE_THRESHOLD = intPreferencesKey("full_player_close_threshold")
        val USE_PLAYER_SHEET_V2 = booleanPreferencesKey("use_player_sheet_v2")
        val USE_ANIMATED_LYRICS = booleanPreferencesKey("use_animated_lyrics")
        val ANIMATED_LYRICS_BLUR_ENABLED = booleanPreferencesKey("animated_lyrics_blur_enabled")
        val ANIMATED_LYRICS_BLUR_STRENGTH = floatPreferencesKey("animated_lyrics_blur_strength")
        val LIBRARY_TABS_ORDER = stringPreferencesKey("library_tabs_order")
        val IS_FOLDER_FILTER_ACTIVE = booleanPreferencesKey("is_folder_filter_active")
        val IS_FOLDERS_PLAYLIST_VIEW = booleanPreferencesKey("is_folders_playlist_view")
        val SHOW_TELEGRAM_CLOUD_PLAYLISTS = booleanPreferencesKey("show_telegram_cloud_playlists")
        val EXTENSION_MEDIA_CACHE_LIMIT_MB = intPreferencesKey("extension_media_cache_limit_mb")
    }

    private fun <T> pref(transform: (Preferences) -> T): Flow<T> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    Timber.e(exception, "Error reading preferences.")
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { transform(it) }

    val searchHistoryFlow: Flow<String?> = pref { it[PreferencesKeys.SEARCH_HISTORY] }

    suspend fun saveSearchHistory(historyJson: String) {
        dataStore.edit { it[PreferencesKeys.SEARCH_HISTORY] = historyJson }
    }

    val lastSourceScopeFlow: Flow<SourceScope> =
        dataStore.data.map { preferences ->
            val value = preferences[PreferencesKeys.LAST_STORAGE_FILTER] ?: "ALL"
            when {
                value == "ALL" -> SourceScope.All
                value == "LOCAL" -> SourceScope.Local
                value.startsWith("EXT:") -> SourceScope.Extension(value.removePrefix("EXT:"))
                // Legacy support
                value == "ONLINE" -> SourceScope.All
                value == "OFFLINE" -> SourceScope.Local
                else -> SourceScope.All
            }
        }

    suspend fun saveLastSourceScope(scope: SourceScope) {
        dataStore.edit { preferences ->
            val value = when (scope) {
                SourceScope.All -> "ALL"
                SourceScope.Local -> "LOCAL"
                is SourceScope.Extension -> "EXT:${scope.extensionId}"
            }
            preferences[PreferencesKeys.LAST_STORAGE_FILTER] = value
        }
    }

    val hideLocalMediaFlow: Flow<Boolean> = pref { it[PreferencesKeys.HIDE_LOCAL_MEDIA] ?: false }

    suspend fun setHideLocalMedia(hide: Boolean) {
        dataStore.edit { it[PreferencesKeys.HIDE_LOCAL_MEDIA] = hide }
    }

    val telegramTopicDisplayModeFlow: Flow<String?> = pref { it[PreferencesKeys.TELEGRAM_TOPIC_DISPLAY_MODE] }

    suspend fun setTelegramTopicDisplayMode(mode: String) {
        dataStore.edit { it[PreferencesKeys.TELEGRAM_TOPIC_DISPLAY_MODE] = mode }
    }

    val foldersSourceFlow: Flow<String?> = pref { it[PreferencesKeys.FOLDERS_SOURCE] }

    suspend fun setFoldersSource(source: String) {
        dataStore.edit { it[PreferencesKeys.FOLDERS_SOURCE] = source }
    }

    val themeModeFlow: Flow<String> = pref { it[PreferencesKeys.THEME_MODE] ?: "SYSTEM" }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { it[PreferencesKeys.THEME_MODE] = mode }
    }

    val dynamicColorsFlow: Flow<Boolean> = pref { it[PreferencesKeys.DYNAMIC_COLORS] ?: true }

    suspend fun setDynamicColors(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.DYNAMIC_COLORS] = enabled }
    }

    val collagePatternFlow: Flow<String> = pref { it[PreferencesKeys.COLLAGE_PATTERN] ?: "GRID" }

    suspend fun setCollagePattern(pattern: String) {
        dataStore.edit { it[PreferencesKeys.COLLAGE_PATTERN] = pattern }
    }

    val collageAutoRotateFlow: Flow<Boolean> = pref { it[PreferencesKeys.COLLAGE_AUTO_ROTATE] ?: false }

    suspend fun setCollageAutoRotate(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.COLLAGE_AUTO_ROTATE] = enabled }
    }

    val transitionSettingsFlow: Flow<String?> = pref { it[PreferencesKeys.TRANSITION_SETTINGS] }

    suspend fun saveTransitionSettings(settingsJson: String) {
        dataStore.edit { it[PreferencesKeys.TRANSITION_SETTINGS] = settingsJson }
    }

    val lyricsSourcePreferenceFlow: Flow<LyricsSourcePreference> = pref { 
        val name = it[PreferencesKeys.LYRICS_SOURCE_PREFERENCE] ?: LyricsSourcePreference.API_FIRST.name
        try {
            LyricsSourcePreference.valueOf(name)
        } catch (e: Exception) {
            LyricsSourcePreference.API_FIRST
        }
    }

    suspend fun saveLyricsSourcePreference(preference: LyricsSourcePreference) {
        dataStore.edit { it[PreferencesKeys.LYRICS_SOURCE_PREFERENCE] = preference.name }
    }

    val beta05CleanInstallDisclaimerDismissedFlow: Flow<Boolean> = pref { it[PreferencesKeys.BETA_05_CLEAN_INSTALL_DISCLAIMER_DISMISSED] ?: false }

    suspend fun setBeta05CleanInstallDisclaimerDismissed(dismissed: Boolean) {
        dataStore.edit { it[PreferencesKeys.BETA_05_CLEAN_INSTALL_DISCLAIMER_DISMISSED] = dismissed }
    }

    val mockGenresEnabledFlow: Flow<Boolean> = pref { it[PreferencesKeys.MOCK_GENRES_ENABLED] ?: false }

    suspend fun setMockGenresEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.MOCK_GENRES_ENABLED] = enabled }
    }

    val lastPlaylistIdFlow: Flow<String?> = pref { it[PreferencesKeys.LAST_PLAYLIST_ID]?.takeIf { id -> id.isNotBlank() } }
    val lastPlaylistNameFlow: Flow<String?> = pref { it[PreferencesKeys.LAST_PLAYLIST_NAME] }

    suspend fun setLastPlaylist(playlistId: String, playlistName: String) {
        dataStore.edit {
            it[PreferencesKeys.LAST_PLAYLIST_ID] = playlistId
            it[PreferencesKeys.LAST_PLAYLIST_NAME] = playlistName
        }
    }

    suspend fun clearLastPlaylist() {
        dataStore.edit {
            it.remove(PreferencesKeys.LAST_PLAYLIST_ID)
            it.remove(PreferencesKeys.LAST_PLAYLIST_NAME)
        }
    }

    val extensionRegistriesFlow: Flow<Set<String>> = pref { it[PreferencesKeys.EXTENSION_REGISTRIES] ?: emptySet() }

    suspend fun addExtensionRegistry(url: String) {
        dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.EXTENSION_REGISTRIES] ?: emptySet()
            preferences[PreferencesKeys.EXTENSION_REGISTRIES] = current + url
        }
    }

    suspend fun removeExtensionRegistry(url: String) {
        dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.EXTENSION_REGISTRIES] ?: emptySet()
            preferences[PreferencesKeys.EXTENSION_REGISTRIES] = current - url
        }
    }

    val extensionMediaCacheLimitMbFlow: Flow<Int> = pref { it[PreferencesKeys.EXTENSION_MEDIA_CACHE_LIMIT_MB] ?: 500 }

    suspend fun setExtensionMediaCacheLimitMb(limitMb: Int) {
        dataStore.edit { it[PreferencesKeys.EXTENSION_MEDIA_CACHE_LIMIT_MB] = limitMb.coerceIn(100, 5000) }
    }

    val navBarCornerRadiusFlow: Flow<Int> = pref { it[PreferencesKeys.NAV_BAR_CORNER_RADIUS] ?: 32 }

    suspend fun setNavBarCornerRadius(radius: Int) {
        dataStore.edit { it[PreferencesKeys.NAV_BAR_CORNER_RADIUS] = radius }
    }

    val navBarStyleFlow: Flow<String> = pref { it[PreferencesKeys.NAV_BAR_STYLE] ?: "DEFAULT" }

    suspend fun setNavBarStyle(style: String) {
        dataStore.edit { it[PreferencesKeys.NAV_BAR_STYLE] = style }
    }

    val navBarCompactModeFlow: Flow<Boolean> = pref { it[PreferencesKeys.NAV_BAR_COMPACT_MODE] ?: false }

    suspend fun setNavBarCompactMode(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.NAV_BAR_COMPACT_MODE] = enabled }
    }

    val libraryNavigationModeFlow: Flow<String> = pref { it[PreferencesKeys.LIBRARY_NAVIGATION_MODE] ?: "TAB_ROW" }

    suspend fun setLibraryNavigationMode(mode: String) {
        dataStore.edit { it[PreferencesKeys.LIBRARY_NAVIGATION_MODE] = mode }
    }

    val carouselStyleFlow: Flow<String> = pref { it[PreferencesKeys.CAROUSEL_STYLE] ?: "NO_PEEK" }

    suspend fun setCarouselStyle(style: String) {
        dataStore.edit { it[PreferencesKeys.CAROUSEL_STYLE] = style }
    }

    val launchTabFlow: Flow<String> = pref { it[PreferencesKeys.LAUNCH_TAB] ?: "HOME" }

    suspend fun setLaunchTab(tab: String) {
        dataStore.edit { it[PreferencesKeys.LAUNCH_TAB] = tab }
    }

    suspend fun setKeepPlayingInBackground(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.KEEP_PLAYING_IN_BACKGROUND] = enabled }
    }

    suspend fun setDisableCastAutoplay(disabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.DISABLE_CAST_AUTOPLAY] = disabled }
    }

    suspend fun setResumeOnHeadsetReconnect(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.RESUME_ON_HEADSET_RECONNECT] = enabled }
    }

    suspend fun setDelayAllFullPlayerContent(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FULL_PLAYER_DELAY_ALL] = enabled
            preferences[PreferencesKeys.FULL_PLAYER_DELAY_ALBUM] = enabled
            preferences[PreferencesKeys.FULL_PLAYER_DELAY_METADATA] = enabled
            preferences[PreferencesKeys.FULL_PLAYER_DELAY_PROGRESS] = enabled
            preferences[PreferencesKeys.FULL_PLAYER_DELAY_CONTROLS] = enabled
        }
    }

    suspend fun setDelayAlbumCarousel(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.FULL_PLAYER_DELAY_ALBUM] = enabled }
    }

    suspend fun setDelaySongMetadata(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.FULL_PLAYER_DELAY_METADATA] = enabled }
    }

    suspend fun setDelayProgressBar(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.FULL_PLAYER_DELAY_PROGRESS] = enabled }
    }

    suspend fun setDelayControls(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.FULL_PLAYER_DELAY_CONTROLS] = enabled }
    }

    suspend fun setFullPlayerPlaceholders(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FULL_PLAYER_PLACEHOLDERS] = enabled
            if (!enabled) {
                preferences[PreferencesKeys.FULL_PLAYER_PLACEHOLDER_TRANSPARENT] = false
            }
        }
    }

    suspend fun setTransparentPlaceholders(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.FULL_PLAYER_PLACEHOLDER_TRANSPARENT] = enabled }
    }

    suspend fun setFullPlayerPlaceholdersOnClose(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.FULL_PLAYER_PLACEHOLDERS_ON_CLOSE] = enabled }
    }

    suspend fun setFullPlayerSwitchOnDragRelease(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.FULL_PLAYER_SWITCH_ON_DRAG_RELEASE] = enabled }
    }

    suspend fun setFullPlayerAppearThreshold(thresholdPercent: Int) {
        dataStore.edit { it[PreferencesKeys.FULL_PLAYER_DELAY_THRESHOLD] = thresholdPercent.coerceIn(0, 100) }
    }

    suspend fun setFullPlayerCloseThreshold(thresholdPercent: Int) {
        dataStore.edit { it[PreferencesKeys.FULL_PLAYER_CLOSE_THRESHOLD] = thresholdPercent.coerceIn(0, 100) }
    }

    val useAnimatedLyricsFlow: Flow<Boolean> = pref { it[PreferencesKeys.USE_ANIMATED_LYRICS] ?: false }

    suspend fun setUseAnimatedLyrics(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.USE_ANIMATED_LYRICS] = enabled }
    }

    val animatedLyricsBlurEnabledFlow: Flow<Boolean> = pref { it[PreferencesKeys.ANIMATED_LYRICS_BLUR_ENABLED] ?: true }

    suspend fun setAnimatedLyricsBlurEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.ANIMATED_LYRICS_BLUR_ENABLED] = enabled }
    }

    val animatedLyricsBlurStrengthFlow: Flow<Float> = pref { it[PreferencesKeys.ANIMATED_LYRICS_BLUR_STRENGTH] ?: 2.5f }

    suspend fun setAnimatedLyricsBlurStrength(strength: Float) {
        dataStore.edit { it[PreferencesKeys.ANIMATED_LYRICS_BLUR_STRENGTH] = strength }
    }

    val libraryTabsOrderFlow: Flow<String?> = pref { it[PreferencesKeys.LIBRARY_TABS_ORDER] }

    suspend fun saveLibraryTabsOrder(order: String) {
        dataStore.edit { it[PreferencesKeys.LIBRARY_TABS_ORDER] = order }
    }

    suspend fun resetLibraryTabsOrder() {
        dataStore.edit { it.remove(PreferencesKeys.LIBRARY_TABS_ORDER) }
    }

    suspend fun migrateTabOrder() {
        dataStore.edit { preferences ->
            val orderJson = preferences[PreferencesKeys.LIBRARY_TABS_ORDER] ?: return@edit
            val order = runCatching {
                json.decodeFromString<MutableList<String>>(orderJson)
            }.getOrNull() ?: return@edit

            if ("FOLDERS" !in order) {
                val insertAfter = order.indexOf("LIKED").takeIf { it != -1 } ?: order.lastIndex
                order.add(insertAfter + 1, "FOLDERS")
                preferences[PreferencesKeys.LIBRARY_TABS_ORDER] = json.encodeToString(order)
            }
        }
    }

    val isFolderFilterActiveFlow: Flow<Boolean> = pref { it[PreferencesKeys.IS_FOLDER_FILTER_ACTIVE] ?: false }

    suspend fun setFolderFilterActive(isActive: Boolean) {
        dataStore.edit { it[PreferencesKeys.IS_FOLDER_FILTER_ACTIVE] = isActive }
    }

    val isFoldersPlaylistViewFlow: Flow<Boolean> = pref { it[PreferencesKeys.IS_FOLDERS_PLAYLIST_VIEW] ?: false }

    suspend fun setFoldersPlaylistView(isPlaylistView: Boolean) {
        dataStore.edit { it[PreferencesKeys.IS_FOLDERS_PLAYLIST_VIEW] = isPlaylistView }
    }

    val showTelegramCloudPlaylistsFlow: Flow<Boolean> = pref { it[PreferencesKeys.SHOW_TELEGRAM_CLOUD_PLAYLISTS] ?: true }

    suspend fun setSongsSortOption(option: String) {
        dataStore.edit { it[PreferencesKeys.SONGS_SORT_OPTION] = option }
    }

    val songsSortOptionFlow: Flow<String> = pref { it[PreferencesKeys.SONGS_SORT_OPTION] ?: "TITLE_ASC" }

    companion object {
        /** Default character delimiters for splitting multi-artist tags */
        val DEFAULT_ARTIST_DELIMITERS = listOf("/", ";", ",", "+", "&")
        /** Default word-based delimiters (matched case-insensitively with whitespace boundaries) */
        val DEFAULT_ARTIST_WORD_DELIMITERS = listOf("featuring", "feat.", "feat", "ft.", "ft", "vs.", "vs", "versus", "with", "prod.", "prod")
        const val DEFAULT_ALBUM_ART_CACHE_LIMIT_MB = 200
        const val DEFAULT_EXTENSION_MEDIA_CACHE_LIMIT_MB = 500
    }

    /** Increments [value] by 1, wrapping back to 0 on overflow. */
    private fun incrementWrapped(value: Int?) =
        if (value == null || value == Int.MAX_VALUE) 0 else value + 1
}
