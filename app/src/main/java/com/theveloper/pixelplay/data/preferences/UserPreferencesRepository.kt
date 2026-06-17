package com.theveloper.pixelplay.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import timber.log.Timber

val Context.dataStore by preferencesDataStore(name = "settings")

data class AdvancedPerformanceDiagnosticsSettings(
    val enabled: Boolean,
    val sessionStartedEpochMs: Long?,
    val expiresAtEpochMs: Long?
) {
    fun isActive(): Boolean {
        val now = System.currentTimeMillis()
        return enabled && (expiresAtEpochMs == null || expiresAtEpochMs > now)
    }
}

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
        val GROUP_BY_ALBUM_ARTIST = booleanPreferencesKey("group_by_album_artist")
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
        
        val INITIAL_SETUP_DONE = booleanPreferencesKey("initial_setup_done")
        val ALLOWED_DIRECTORIES = stringSetPreferencesKey("allowed_directories")
        val BLOCKED_DIRECTORIES = stringSetPreferencesKey("blocked_directories")
        val SHOW_SCROLLBAR = booleanPreferencesKey("show_scrollbar")
        val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
        
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val SHOW_QUEUE_HISTORY = booleanPreferencesKey("show_queue_history")
        val IS_CROSSFADE_ENABLED = booleanPreferencesKey("is_crossfade_enabled")
        val CROSSFADE_DURATION = intPreferencesKey("crossfade_duration")
        val PERSISTENT_SHUFFLE_ENABLED = booleanPreferencesKey("persistent_shuffle_enabled")
        val FOLDER_BACK_GESTURE_NAVIGATION = booleanPreferencesKey("folder_back_gesture_navigation")
        val AUTO_SCAN_LRC_FILES = booleanPreferencesKey("auto_scan_lrc_files")
        val TAP_BACKGROUND_CLOSES_PLAYER = booleanPreferencesKey("tap_background_closes_player")
        val REPLAY_GAIN_ENABLED = booleanPreferencesKey("replay_gain_enabled")
        val REPLAY_GAIN_USE_ALBUM_GAIN = booleanPreferencesKey("replay_gain_use_album_gain")
        val IMMERSIVE_LYRICS_ENABLED = booleanPreferencesKey("immersive_lyrics_enabled")
        val IMMERSIVE_LYRICS_TIMEOUT = longPreferencesKey("immersive_lyrics_timeout")
        val MIN_SONG_DURATION = intPreferencesKey("min_song_duration")
        val MIN_TRACKS_PER_ALBUM = intPreferencesKey("min_tracks_per_album")
        val HI_FI_MODE_ENABLED = booleanPreferencesKey("hi_fi_mode_enabled")
        val ALBUM_ART_QUALITY = stringPreferencesKey("album_art_quality")
        val ALBUM_ART_CACHE_LIMIT_MB = intPreferencesKey("album_art_cache_limit_mb")
        val SHOW_PLAYER_FILE_INFO = booleanPreferencesKey("show_player_file_info")
        val BACKUP_INFO_DISMISSED = booleanPreferencesKey("backup_info_dismissed")
        val APP_REBRAND_DIALOG_SHOWN = booleanPreferencesKey("app_rebrand_dialog_shown")
        val DISABLE_BLUR_ALL_OVER = booleanPreferencesKey("disable_blur_all_over")
        val USE_SMOOTH_CORNERS = booleanPreferencesKey("use_smooth_corners")
        val LYRICS_ALIGNMENT = stringPreferencesKey("lyrics_alignment")
        val SHOW_LYRICS_TRANSLATION = booleanPreferencesKey("show_lyrics_translation")
        val SHOW_LYRICS_ROMANIZATION = booleanPreferencesKey("show_lyrics_romanization")
        val KEEP_SCREEN_ON_LYRICS = booleanPreferencesKey("keep_screen_on_lyrics")
        val LYRICS_SYNC_OFFSETS = stringPreferencesKey("lyrics_sync_offsets")
        
        val ALBUMS_SORT_OPTION = stringPreferencesKey("albums_sort_option")
        val ARTISTS_SORT_OPTION = stringPreferencesKey("artists_sort_option")
        val FOLDERS_SORT_OPTION = stringPreferencesKey("folders_sort_option")
        val LIKED_SONGS_SORT_OPTION = stringPreferencesKey("liked_songs_sort_option")
        val REPEAT_MODE = intPreferencesKey("repeat_mode")
        val LAST_LIBRARY_TAB_INDEX = intPreferencesKey("last_library_tab_index")
        val FAVORITE_SONG_IDS = stringSetPreferencesKey("favorite_song_ids")
        val CUSTOM_GENRES = stringSetPreferencesKey("custom_genres")
        val CUSTOM_GENRE_ICONS = stringPreferencesKey("custom_genre_icons")
        val IS_GENRE_GRID_VIEW = booleanPreferencesKey("is_genre_grid_view")

        val ADVANCED_DIAGNOSTICS_ENABLED = booleanPreferencesKey("advanced_diagnostics_enabled")
        val ADVANCED_DIAGNOSTICS_STARTED_AT = longPreferencesKey("advanced_diagnostics_started_at")
        val ADVANCED_DIAGNOSTICS_EXPIRES_AT = longPreferencesKey("advanced_diagnostics_expires_at")
        val ARTIST_SETTINGS_RESCAN_REQUIRED = booleanPreferencesKey("artist_settings_rescan_required")

        val DIRECTORY_RULES_VERSION = intPreferencesKey("directory_rules_version")
        val LAST_APPLIED_DIRECTORY_RULES_VERSION = intPreferencesKey("last_applied_directory_rules_version")

        val PLAYBACK_QUEUE_SNAPSHOT = stringPreferencesKey("playback_queue_snapshot")

        val ARTIST_DELIMITERS = stringPreferencesKey("artist_delimiters")
        val ARTIST_WORD_DELIMITERS = stringPreferencesKey("artist_word_delimiters")
        val EXTRACT_ARTISTS_FROM_TITLE = booleanPreferencesKey("extract_artists_from_title")

        val ALBUMS_LIST_VIEW = stringPreferencesKey("albums_list_view")
        val PLAYLISTS_SORT_OPTION = stringPreferencesKey("playlists_sort_option")

        val USER_PLAYLISTS_JSON_V1 = stringPreferencesKey("user_playlists_json_v1")
        val DAILY_MIX_SONG_IDS = stringPreferencesKey("daily_mix_song_ids")
        val YOUR_MIX_SONG_IDS = stringPreferencesKey("your_mix_song_ids")
        val LAST_DAILY_MIX_UPDATE_TIMESTAMP = longPreferencesKey("last_daily_mix_update_timestamp")
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

    // --- Core & Setup ---

    val initialSetupDoneFlow: Flow<Boolean> = pref { it[PreferencesKeys.INITIAL_SETUP_DONE] ?: false }
    suspend fun setInitialSetupDone(done: Boolean) {
        dataStore.edit { it[PreferencesKeys.INITIAL_SETUP_DONE] = done }
    }

    val allowedDirectoriesFlow: Flow<Set<String>> = pref { it[PreferencesKeys.ALLOWED_DIRECTORIES] ?: emptySet() }
    val blockedDirectoriesFlow: Flow<Set<String>> = pref { it[PreferencesKeys.BLOCKED_DIRECTORIES] ?: emptySet() }
    suspend fun setBlockedDirectories(blocked: Set<String>) {
        dataStore.edit { it[PreferencesKeys.BLOCKED_DIRECTORIES] = blocked }
    }

    suspend fun updateDirectorySelections(allowed: Set<String>, blocked: Set<String>) {
        dataStore.edit {
            it[PreferencesKeys.ALLOWED_DIRECTORIES] = allowed
            it[PreferencesKeys.BLOCKED_DIRECTORIES] = blocked
            it[PreferencesKeys.DIRECTORY_RULES_VERSION] = incrementWrapped(it[PreferencesKeys.DIRECTORY_RULES_VERSION])
        }
    }

    suspend fun getDirectoryRulesVersion(): Int = dataStore.data.map { it[PreferencesKeys.DIRECTORY_RULES_VERSION] ?: 0 }.first()
    suspend fun getLastAppliedDirectoryRulesVersion(): Int = dataStore.data.map { it[PreferencesKeys.LAST_APPLIED_DIRECTORY_RULES_VERSION] ?: 0 }.first()
    suspend fun markDirectoryRulesVersionApplied(version: Int) {
        dataStore.edit { it[PreferencesKeys.LAST_APPLIED_DIRECTORY_RULES_VERSION] = version }
    }

    suspend fun ensureLibrarySortDefaults() {
        dataStore.edit { preferences ->
            if (preferences[PreferencesKeys.SONGS_SORT_OPTION] == null) {
                preferences[PreferencesKeys.SONGS_SORT_OPTION] = "TITLE_ASC"
            }
            if (preferences[PreferencesKeys.ALBUMS_SORT_OPTION] == null) {
                preferences[PreferencesKeys.ALBUMS_SORT_OPTION] = "TITLE_ASC"
            }
            if (preferences[PreferencesKeys.ARTISTS_SORT_OPTION] == null) {
                preferences[PreferencesKeys.ARTISTS_SORT_OPTION] = "NAME_ASC"
            }
            if (preferences[PreferencesKeys.FOLDERS_SORT_OPTION] == null) {
                preferences[PreferencesKeys.FOLDERS_SORT_OPTION] = "NAME_ASC"
            }
        }
    }

    val showScrollbarFlow: Flow<Boolean> = pref { it[PreferencesKeys.SHOW_SCROLLBAR] ?: true }
    suspend fun setShowScrollbar(show: Boolean) {
        dataStore.edit { it[PreferencesKeys.SHOW_SCROLLBAR] = show }
    }

    val lastSyncTimestampFlow: Flow<Long> = pref { it[PreferencesKeys.LAST_SYNC_TIMESTAMP] ?: 0L }
    suspend fun getLastSyncTimestamp(): Long = dataStore.data.map { it[PreferencesKeys.LAST_SYNC_TIMESTAMP] ?: 0L }.first()
    suspend fun setLastSyncTimestamp(timestamp: Long) {
        dataStore.edit { it[PreferencesKeys.LAST_SYNC_TIMESTAMP] = timestamp }
    }

    val advancedPerformanceDiagnosticsSettingsFlow: Flow<AdvancedPerformanceDiagnosticsSettings> = pref { preferences ->
        AdvancedPerformanceDiagnosticsSettings(
            enabled = preferences[PreferencesKeys.ADVANCED_DIAGNOSTICS_ENABLED] ?: false,
            sessionStartedEpochMs = preferences[PreferencesKeys.ADVANCED_DIAGNOSTICS_STARTED_AT],
            expiresAtEpochMs = preferences[PreferencesKeys.ADVANCED_DIAGNOSTICS_EXPIRES_AT]
        )
    }

    suspend fun setAdvancedPerformanceDiagnosticsEnabled(enabled: Boolean, durationMs: Long? = null) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ADVANCED_DIAGNOSTICS_ENABLED] = enabled
            if (enabled && durationMs != null) {
                val now = System.currentTimeMillis()
                preferences[PreferencesKeys.ADVANCED_DIAGNOSTICS_STARTED_AT] = now
                preferences[PreferencesKeys.ADVANCED_DIAGNOSTICS_EXPIRES_AT] = now + durationMs
            } else {
                preferences.remove(PreferencesKeys.ADVANCED_DIAGNOSTICS_STARTED_AT)
                preferences.remove(PreferencesKeys.ADVANCED_DIAGNOSTICS_EXPIRES_AT)
            }
        }
    }

    suspend fun disableExpiredAdvancedPerformanceDiagnostics() {
        dataStore.edit { preferences ->
            val expiresAt = preferences[PreferencesKeys.ADVANCED_DIAGNOSTICS_EXPIRES_AT]
            if (expiresAt != null && System.currentTimeMillis() > expiresAt) {
                preferences[PreferencesKeys.ADVANCED_DIAGNOSTICS_ENABLED] = false
                preferences.remove(PreferencesKeys.ADVANCED_DIAGNOSTICS_STARTED_AT)
                preferences.remove(PreferencesKeys.ADVANCED_DIAGNOSTICS_EXPIRES_AT)
            }
        }
    }

    val searchHistoryFlow: Flow<String?> = pref { it[PreferencesKeys.SEARCH_HISTORY] }
    suspend fun saveSearchHistory(historyJson: String) {
        dataStore.edit { it[PreferencesKeys.SEARCH_HISTORY] = historyJson }
    }

    val dailyMixSongIdsFlow: Flow<List<String>> = pref { 
        val jsonString = it[PreferencesKeys.DAILY_MIX_SONG_IDS] ?: return@pref emptyList()
        try { json.decodeFromString<List<String>>(jsonString) } catch (e: Exception) { emptyList() }
    }
    suspend fun saveDailyMixSongIds(ids: List<String>) {
        dataStore.edit { it[PreferencesKeys.DAILY_MIX_SONG_IDS] = json.encodeToString(ids) }
    }

    val yourMixSongIdsFlow: Flow<List<String>> = pref { 
        val jsonString = it[PreferencesKeys.YOUR_MIX_SONG_IDS] ?: return@pref emptyList()
        try { json.decodeFromString<List<String>>(jsonString) } catch (e: Exception) { emptyList() }
    }
    suspend fun saveYourMixSongIds(ids: List<String>) {
        dataStore.edit { it[PreferencesKeys.YOUR_MIX_SONG_IDS] = json.encodeToString(ids) }
    }

    val lastDailyMixUpdateFlow: Flow<Long?> = pref { it[PreferencesKeys.LAST_DAILY_MIX_UPDATE_TIMESTAMP] }
    suspend fun saveLastDailyMixUpdateTimestamp(timestamp: Long) {
        dataStore.edit { it[PreferencesKeys.LAST_DAILY_MIX_UPDATE_TIMESTAMP] = timestamp }
    }

    val playbackQueueSnapshotFlow: Flow<PlaybackQueueSnapshot?> = pref { preferences ->
        val jsonString = preferences[PreferencesKeys.PLAYBACK_QUEUE_SNAPSHOT] ?: return@pref null
        try {
            json.decodeFromString<PlaybackQueueSnapshot>(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getPlaybackQueueSnapshotOnce(): PlaybackQueueSnapshot? {
        val jsonString = dataStore.data.first()[PreferencesKeys.PLAYBACK_QUEUE_SNAPSHOT] ?: return null
        return try {
            json.decodeFromString<PlaybackQueueSnapshot>(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun setPlaybackQueueSnapshot(snapshot: PlaybackQueueSnapshot?) {
        dataStore.edit {
            if (snapshot == null) {
                it.remove(PreferencesKeys.PLAYBACK_QUEUE_SNAPSHOT)
            } else {
                it[PreferencesKeys.PLAYBACK_QUEUE_SNAPSHOT] = json.encodeToString(snapshot)
            }
        }
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

    // --- UI & Theme ---

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

    val navBarCornerRadiusFlow: Flow<Int> = pref { it[PreferencesKeys.NAV_BAR_CORNER_RADIUS] ?: 32 }
    suspend fun setNavBarCornerRadius(radius: Int) {
        dataStore.edit { it[PreferencesKeys.NAV_BAR_CORNER_RADIUS] = sanitizeNavBarCornerRadius(radius) }
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

    val hapticsEnabledFlow: Flow<Boolean> = pref { it[PreferencesKeys.HAPTICS_ENABLED] ?: true }
    suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.HAPTICS_ENABLED] = enabled }
    }

    val lyricsAlignmentFlow: Flow<String> = pref { it[PreferencesKeys.LYRICS_ALIGNMENT] ?: "left" }
    suspend fun setLyricsAlignment(alignment: String) {
        dataStore.edit { it[PreferencesKeys.LYRICS_ALIGNMENT] = alignment }
    }

    val showLyricsTranslationFlow: Flow<Boolean> = pref { it[PreferencesKeys.SHOW_LYRICS_TRANSLATION] ?: true }
    suspend fun setShowLyricsTranslation(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.SHOW_LYRICS_TRANSLATION] = enabled }
    }

    val showLyricsRomanizationFlow: Flow<Boolean> = pref { it[PreferencesKeys.SHOW_LYRICS_ROMANIZATION] ?: true }
    suspend fun setShowLyricsRomanization(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.SHOW_LYRICS_ROMANIZATION] = enabled }
    }

    val disableBlurAllOverFlow: Flow<Boolean> = pref { it[PreferencesKeys.DISABLE_BLUR_ALL_OVER] ?: false }
    suspend fun setDisableBlurAllOver(disabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.DISABLE_BLUR_ALL_OVER] = disabled }
    }

    val keepScreenOnLyricsFlow: Flow<Boolean> = pref { it[PreferencesKeys.KEEP_SCREEN_ON_LYRICS] ?: false }
    suspend fun setKeepScreenOnLyrics(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.KEEP_SCREEN_ON_LYRICS] = enabled }
    }

    val useSmoothCornersFlow: Flow<Boolean> = pref { it[PreferencesKeys.USE_SMOOTH_CORNERS] ?: true }
    suspend fun setUseSmoothCorners(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.USE_SMOOTH_CORNERS] = enabled }
    }

    // --- Playback Settings ---

    val artistSettingsRescanRequiredFlow: Flow<Boolean> = pref { it[PreferencesKeys.ARTIST_SETTINGS_RESCAN_REQUIRED] ?: false }
    suspend fun clearArtistSettingsRescanRequired() {
        dataStore.edit { it[PreferencesKeys.ARTIST_SETTINGS_RESCAN_REQUIRED] = false }
    }

    suspend fun getMinSongDuration(): Long = 30000L

    suspend fun setShuffleOn(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.PERSISTENT_SHUFFLE_ENABLED] = enabled }
    }

    val isShuffleOnFlow: Flow<Boolean> = pref { it[PreferencesKeys.PERSISTENT_SHUFFLE_ENABLED] ?: false }

    val persistentShuffleEnabledFlow: Flow<Boolean> = pref { it[PreferencesKeys.PERSISTENT_SHUFFLE_ENABLED] ?: false }
    suspend fun setPersistentShuffleEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.PERSISTENT_SHUFFLE_ENABLED] = enabled }
    }

    val repeatModeFlow: Flow<Int> = pref { it[PreferencesKeys.REPEAT_MODE] ?: 0 }
    suspend fun setRepeatMode(mode: Int) {
        dataStore.edit { it[PreferencesKeys.REPEAT_MODE] = mode }
    }

    val showQueueHistoryFlow: Flow<Boolean> = pref { it[PreferencesKeys.SHOW_QUEUE_HISTORY] ?: true }
    suspend fun setShowQueueHistory(show: Boolean) {
        dataStore.edit { it[PreferencesKeys.SHOW_QUEUE_HISTORY] = show }
    }

    val isCrossfadeEnabledFlow: Flow<Boolean> = pref { it[PreferencesKeys.IS_CROSSFADE_ENABLED] ?: false }
    suspend fun setCrossfadeEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.IS_CROSSFADE_ENABLED] = enabled }
    }

    val crossfadeDurationFlow: Flow<Int> = pref { it[PreferencesKeys.CROSSFADE_DURATION] ?: 2000 }
    suspend fun setCrossfadeDuration(duration: Int) {
        dataStore.edit { it[PreferencesKeys.CROSSFADE_DURATION] = duration }
    }

    val replayGainEnabledFlow: Flow<Boolean> = pref { it[PreferencesKeys.REPLAY_GAIN_ENABLED] ?: false }
    suspend fun setReplayGainEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.REPLAY_GAIN_ENABLED] = enabled }
    }

    val replayGainUseAlbumGainFlow: Flow<Boolean> = pref { it[PreferencesKeys.REPLAY_GAIN_USE_ALBUM_GAIN] ?: false }
    suspend fun setReplayGainUseAlbumGain(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.REPLAY_GAIN_USE_ALBUM_GAIN] = enabled }
    }

    val tapBackgroundClosesPlayerFlow: Flow<Boolean> = pref { it[PreferencesKeys.TAP_BACKGROUND_CLOSES_PLAYER] ?: false }
    suspend fun setTapBackgroundClosesPlayer(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.TAP_BACKGROUND_CLOSES_PLAYER] = enabled }
    }

    // --- Library & Media ---

    val songsSortOptionFlow: Flow<String> = pref { it[PreferencesKeys.SONGS_SORT_OPTION] ?: "TITLE_ASC" }
    suspend fun setSongsSortOption(option: String) {
        dataStore.edit { it[PreferencesKeys.SONGS_SORT_OPTION] = option }
    }

    val albumsSortOptionFlow: Flow<String> = pref { it[PreferencesKeys.ALBUMS_SORT_OPTION] ?: "TITLE_ASC" }
    suspend fun setAlbumsSortOption(option: String) {
        dataStore.edit { it[PreferencesKeys.ALBUMS_SORT_OPTION] = option }
    }

    val artistsSortOptionFlow: Flow<String> = pref { it[PreferencesKeys.ARTISTS_SORT_OPTION] ?: "NAME_ASC" }
    suspend fun setArtistsSortOption(option: String) {
        dataStore.edit { it[PreferencesKeys.ARTISTS_SORT_OPTION] = option }
    }

    val foldersSortOptionFlow: Flow<String> = pref { it[PreferencesKeys.FOLDERS_SORT_OPTION] ?: "NAME_ASC" }
    suspend fun setFoldersSortOption(option: String) {
        dataStore.edit { it[PreferencesKeys.FOLDERS_SORT_OPTION] = option }
    }

    val likedSongsSortOptionFlow: Flow<String> = pref { it[PreferencesKeys.LIKED_SONGS_SORT_OPTION] ?: "TITLE_ASC" }
    suspend fun setLikedSongsSortOption(option: String) {
        dataStore.edit { it[PreferencesKeys.LIKED_SONGS_SORT_OPTION] = option }
    }

    val minSongDurationFlow: Flow<Int> = pref { it[PreferencesKeys.MIN_SONG_DURATION] ?: 10000 }
    suspend fun setMinSongDuration(duration: Int) {
        dataStore.edit { it[PreferencesKeys.MIN_SONG_DURATION] = duration }
    }

    val minTracksPerAlbumFlow: Flow<Int> = pref { it[PreferencesKeys.MIN_TRACKS_PER_ALBUM] ?: 1 }
    suspend fun setMinTracksPerAlbum(min: Int) {
        dataStore.edit { it[PreferencesKeys.MIN_TRACKS_PER_ALBUM] = min }
    }

    val artistDelimitersFlow: Flow<List<String>> = pref { it[PreferencesKeys.ARTIST_DELIMITERS]?.split("|") ?: DEFAULT_ARTIST_DELIMITERS }
    suspend fun setArtistDelimiters(delimiters: List<String>) {
        dataStore.edit { it[PreferencesKeys.ARTIST_DELIMITERS] = delimiters.joinToString("|") }
    }

    val artistWordDelimitersFlow: Flow<List<String>> = pref { it[PreferencesKeys.ARTIST_WORD_DELIMITERS]?.split("|") ?: DEFAULT_ARTIST_WORD_DELIMITERS }
    suspend fun setArtistWordDelimiters(delimiters: List<String>) {
        dataStore.edit { it[PreferencesKeys.ARTIST_WORD_DELIMITERS] = delimiters.joinToString("|") }
    }

    val extractArtistsFromTitleFlow: Flow<Boolean> = pref { it[PreferencesKeys.EXTRACT_ARTISTS_FROM_TITLE] ?: false }
    suspend fun setExtractArtistsFromTitle(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.EXTRACT_ARTISTS_FROM_TITLE] = enabled }
    }

    val groupByAlbumArtistFlow: Flow<Boolean> = pref { it[PreferencesKeys.GROUP_BY_ALBUM_ARTIST] ?: false }
    suspend fun setGroupByAlbumArtist(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.GROUP_BY_ALBUM_ARTIST] = enabled }
    }

    suspend fun resetArtistDelimitersToDefault() {
        dataStore.edit { it.remove(PreferencesKeys.ARTIST_DELIMITERS) }
    }

    suspend fun resetArtistWordDelimitersToDefault() {
        dataStore.edit { it.remove(PreferencesKeys.ARTIST_WORD_DELIMITERS) }
    }

    val albumArtQualityFlow: Flow<AlbumArtQuality> = pref { 
        val name = it[PreferencesKeys.ALBUM_ART_QUALITY] ?: AlbumArtQuality.MEDIUM.name
        try { AlbumArtQuality.valueOf(name) } catch (e: Exception) { AlbumArtQuality.MEDIUM }
    }
    suspend fun setAlbumArtQuality(quality: AlbumArtQuality) {
        dataStore.edit { it[PreferencesKeys.ALBUM_ART_QUALITY] = quality.name }
    }

    val albumsListViewFlow: Flow<String> = pref { it[PreferencesKeys.ALBUMS_LIST_VIEW] ?: "GRID" }
    suspend fun setAlbumsListView(view: String) {
        dataStore.edit { it[PreferencesKeys.ALBUMS_LIST_VIEW] = view }
    }

    val playlistsSortOptionFlow: Flow<String> = pref { it[PreferencesKeys.PLAYLISTS_SORT_OPTION] ?: "CUSTOM" }
    suspend fun setPlaylistsSortOption(option: String) {
        dataStore.edit { it[PreferencesKeys.PLAYLISTS_SORT_OPTION] = option }
    }

    suspend fun getLegacyUserPlaylistsOnce(): List<Playlist> {
        val jsonString = dataStore.data.first()[PreferencesKeys.USER_PLAYLISTS_JSON_V1] ?: return emptyList()
        return try {
            json.decodeFromString<List<Playlist>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun clearLegacyUserPlaylists() {
        dataStore.edit { it.remove(PreferencesKeys.USER_PLAYLISTS_JSON_V1) }
    }

    val albumArtCacheLimitMbFlow: Flow<Int> = pref { it[PreferencesKeys.ALBUM_ART_CACHE_LIMIT_MB] ?: DEFAULT_ALBUM_ART_CACHE_LIMIT_MB }
    suspend fun setAlbumArtCacheLimitMb(limit: Int) {
        dataStore.edit { it[PreferencesKeys.ALBUM_ART_CACHE_LIMIT_MB] = limit }
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

    val libraryTabsOrderFlow: Flow<String?> = pref { it[PreferencesKeys.LIBRARY_TABS_ORDER] }
    suspend fun saveLibraryTabsOrder(order: String) {
        dataStore.edit { it[PreferencesKeys.LIBRARY_TABS_ORDER] = order }
    }
    suspend fun resetLibraryTabsOrder() {
        dataStore.edit { it.remove(PreferencesKeys.LIBRARY_TABS_ORDER) }
    }

    val lastLibraryTabIndexFlow: Flow<Int> = pref { it[PreferencesKeys.LAST_LIBRARY_TAB_INDEX] ?: 0 }
    suspend fun saveLastLibraryTabIndex(index: Int) {
        dataStore.edit { it[PreferencesKeys.LAST_LIBRARY_TAB_INDEX] = index }
    }

    val favoriteSongIdsFlow: Flow<Set<String>> = pref { it[PreferencesKeys.FAVORITE_SONG_IDS] ?: emptySet() }
    suspend fun clearFavoriteSongIds() {
        dataStore.edit { it.remove(PreferencesKeys.FAVORITE_SONG_IDS) }
    }

    // --- Service & System ---

    val keepPlayingInBackgroundFlow: Flow<Boolean> = pref { it[PreferencesKeys.KEEP_PLAYING_IN_BACKGROUND] ?: false }
    suspend fun setKeepPlayingInBackground(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.KEEP_PLAYING_IN_BACKGROUND] = enabled }
    }

    val disableCastAutoplayFlow: Flow<Boolean> = pref { it[PreferencesKeys.DISABLE_CAST_AUTOPLAY] ?: false }
    suspend fun setDisableCastAutoplay(disabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.DISABLE_CAST_AUTOPLAY] = disabled }
    }

    val resumeOnHeadsetReconnectFlow: Flow<Boolean> = pref { it[PreferencesKeys.RESUME_ON_HEADSET_RECONNECT] ?: false }
    suspend fun setResumeOnHeadsetReconnect(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.RESUME_ON_HEADSET_RECONNECT] = enabled }
    }

    val hiFiModeEnabledFlow: Flow<Boolean> = pref { it[PreferencesKeys.HI_FI_MODE_ENABLED] ?: false }
    suspend fun setHiFiModeEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.HI_FI_MODE_ENABLED] = enabled }
    }

    val showPlayerFileInfoFlow: Flow<Boolean> = pref { it[PreferencesKeys.SHOW_PLAYER_FILE_INFO] ?: true }
    suspend fun setShowPlayerFileInfo(show: Boolean) {
        dataStore.edit { it[PreferencesKeys.SHOW_PLAYER_FILE_INFO] = show }
    }

    // --- Lyrics ---

    val lyricsSourcePreferenceFlow: Flow<LyricsSourcePreference> = pref { 
        val name = it[PreferencesKeys.LYRICS_SOURCE_PREFERENCE] ?: LyricsSourcePreference.API_FIRST.name
        try { LyricsSourcePreference.valueOf(name) } catch (e: Exception) { LyricsSourcePreference.API_FIRST }
    }
    suspend fun setLyricsSourcePreference(preference: LyricsSourcePreference) {
        dataStore.edit { it[PreferencesKeys.LYRICS_SOURCE_PREFERENCE] = preference.name }
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

    val immersiveLyricsEnabledFlow: Flow<Boolean> = pref { it[PreferencesKeys.IMMERSIVE_LYRICS_ENABLED] ?: false }
    suspend fun setImmersiveLyricsEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.IMMERSIVE_LYRICS_ENABLED] = enabled }
    }

    val immersiveLyricsTimeoutFlow: Flow<Long> = pref { it[PreferencesKeys.IMMERSIVE_LYRICS_TIMEOUT] ?: 4000L }
    suspend fun setImmersiveLyricsTimeout(timeout: Long) {
        dataStore.edit { it[PreferencesKeys.IMMERSIVE_LYRICS_TIMEOUT] = timeout }
    }

    val autoScanLrcFilesFlow: Flow<Boolean> = pref { it[PreferencesKeys.AUTO_SCAN_LRC_FILES] ?: false }
    suspend fun setAutoScanLrcFiles(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.AUTO_SCAN_LRC_FILES] = enabled }
    }

    // --- Folders ---

    val foldersSourceFlow: Flow<String?> = pref { it[PreferencesKeys.FOLDERS_SOURCE] }
    suspend fun setFoldersSource(source: String) {
        dataStore.edit { it[PreferencesKeys.FOLDERS_SOURCE] = source }
    }

    val isFolderFilterActiveFlow: Flow<Boolean> = pref { it[PreferencesKeys.IS_FOLDER_FILTER_ACTIVE] ?: false }
    suspend fun setFolderFilterActive(isActive: Boolean) {
        dataStore.edit { it[PreferencesKeys.IS_FOLDER_FILTER_ACTIVE] = isActive }
    }

    val isFoldersPlaylistViewFlow: Flow<Boolean> = pref { it[PreferencesKeys.IS_FOLDERS_PLAYLIST_VIEW] ?: false }
    suspend fun setFoldersPlaylistView(isPlaylistView: Boolean) {
        dataStore.edit { it[PreferencesKeys.IS_FOLDERS_PLAYLIST_VIEW] = isPlaylistView }
    }

    val folderBackGestureNavigationFlow: Flow<Boolean> = pref { it[PreferencesKeys.FOLDER_BACK_GESTURE_NAVIGATION] ?: true }
    suspend fun setFolderBackGestureNavigation(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.FOLDER_BACK_GESTURE_NAVIGATION] = enabled }
    }

    // --- Extensions ---

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

    // --- UI Tweaks (Full Player) ---

    val fullPlayerLoadingTweaksFlow: Flow<FullPlayerLoadingTweaks> = pref { preferences ->
        FullPlayerLoadingTweaks(
            delayAll = preferences[PreferencesKeys.FULL_PLAYER_DELAY_ALL] ?: false,
            delayAlbumCarousel = preferences[PreferencesKeys.FULL_PLAYER_DELAY_ALBUM] ?: true,
            delaySongMetadata = preferences[PreferencesKeys.FULL_PLAYER_DELAY_METADATA] ?: true,
            delayProgressBar = preferences[PreferencesKeys.FULL_PLAYER_DELAY_PROGRESS] ?: true,
            delayControls = preferences[PreferencesKeys.FULL_PLAYER_DELAY_CONTROLS] ?: true,
            showPlaceholders = preferences[PreferencesKeys.FULL_PLAYER_PLACEHOLDERS] ?: true,
            transparentPlaceholders = preferences[PreferencesKeys.FULL_PLAYER_PLACEHOLDER_TRANSPARENT] ?: false,
            applyPlaceholdersOnClose = preferences[PreferencesKeys.FULL_PLAYER_PLACEHOLDERS_ON_CLOSE] ?: false,
            switchOnDragRelease = preferences[PreferencesKeys.FULL_PLAYER_SWITCH_ON_DRAG_RELEASE] ?: true,
            contentAppearThresholdPercent = preferences[PreferencesKeys.FULL_PLAYER_DELAY_THRESHOLD] ?: 98,
            contentCloseThresholdPercent = preferences[PreferencesKeys.FULL_PLAYER_CLOSE_THRESHOLD] ?: 0
        )
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

    // --- Custom Genres ---

    val customGenresFlow: Flow<Set<String>> = pref { it[PreferencesKeys.CUSTOM_GENRES] ?: emptySet() }
    val customGenreIconsFlow: Flow<Map<String, String>> = pref {
        val jsonString = it[PreferencesKeys.CUSTOM_GENRE_ICONS] ?: return@pref emptyMap()
        try {
            json.decodeFromString<Map<String, String>>(jsonString)
        } catch (e: Exception) {
            emptyMap()
        }
    }
    val isGenreGridViewFlow: Flow<Boolean> = pref { it[PreferencesKeys.IS_GENRE_GRID_VIEW] ?: true }

    suspend fun setGenreGridView(isGridView: Boolean) {
        dataStore.edit { it[PreferencesKeys.IS_GENRE_GRID_VIEW] = isGridView }
    }

    suspend fun addCustomGenre(name: String, icon: String?) {
        dataStore.edit { preferences ->
            val genres = preferences[PreferencesKeys.CUSTOM_GENRES] ?: emptySet()
            preferences[PreferencesKeys.CUSTOM_GENRES] = genres + name

            if (icon != null) {
                val iconsJson = preferences[PreferencesKeys.CUSTOM_GENRE_ICONS]
                val icons = if (iconsJson != null) {
                    try {
                        json.decodeFromString<MutableMap<String, String>>(iconsJson)
                    } catch (e: Exception) {
                        mutableMapOf()
                    }
                } else {
                    mutableMapOf()
                }
                icons[name] = icon
                preferences[PreferencesKeys.CUSTOM_GENRE_ICONS] = json.encodeToString(icons)
            }
        }
    }

    // --- Backup & Restore ---

    suspend fun exportPreferencesForBackup(): List<PreferenceBackupEntry> {
        return dataStore.data.first().asMap().map { (key, value) ->
            val type = when (value) {
                is String -> "String"
                is Int -> "Int"
                is Long -> "Long"
                is Boolean -> "Boolean"
                is Float -> "Float"
                is Double -> "Double"
                is Set<*> -> "StringSet"
                else -> "Unknown"
            }
            PreferenceBackupEntry(
                key = key.name,
                type = type,
                stringValue = value as? String,
                intValue = value as? Int,
                longValue = value as? Long,
                booleanValue = value as? Boolean,
                floatValue = value as? Float,
                doubleValue = value as? Double,
                stringSetValue = value as? Set<String>
            )
        }
    }

    suspend fun importPreferencesFromBackup(entries: List<PreferenceBackupEntry>, clearExisting: Boolean) {
        dataStore.edit { preferences ->
            if (clearExisting) {
                val initialSetupDone = preferences[PreferencesKeys.INITIAL_SETUP_DONE]
                preferences.clear()
                if (initialSetupDone != null) {
                    preferences[PreferencesKeys.INITIAL_SETUP_DONE] = initialSetupDone
                }
            }
            entries.forEach { entry ->
                when (entry.type) {
                    "String" -> entry.stringValue?.let { preferences[stringPreferencesKey(entry.key)] = it }
                    "Int" -> entry.intValue?.let { preferences[intPreferencesKey(entry.key)] = it }
                    "Long" -> entry.longValue?.let { preferences[longPreferencesKey(entry.key)] = it }
                    "Boolean" -> entry.booleanValue?.let { preferences[booleanPreferencesKey(entry.key)] = it }
                    "Float" -> entry.floatValue?.let { preferences[floatPreferencesKey(entry.key)] = it }
                    "Double" -> entry.doubleValue?.let { preferences[doublePreferencesKey(entry.key)] = it }
                    "StringSet" -> entry.stringSetValue?.let { preferences[stringSetPreferencesKey(entry.key)] = it }
                }
            }
        }
    }

    suspend fun clearPreferencesExceptKeys(keysToKeep: List<String>) {
        dataStore.edit { preferences ->
            val currentKeys = preferences.asMap().keys.toList()
            currentKeys.forEach { key ->
                if (key.name !in keysToKeep && key.name != PreferencesKeys.INITIAL_SETUP_DONE.name) {
                    preferences.remove(key)
                }
            }
        }
    }

    suspend fun clearPreferencesByKeys(keysToRemove: List<String>) {
        dataStore.edit { preferences ->
            val currentKeys = preferences.asMap().keys.toList()
            currentKeys.forEach { key ->
                if (key.name in keysToRemove) {
                    preferences.remove(key)
                }
            }
        }
    }

    // --- Misc ---

    val telegramTopicDisplayModeFlow: Flow<String?> = pref { it[PreferencesKeys.TELEGRAM_TOPIC_DISPLAY_MODE] }
    suspend fun setTelegramTopicDisplayMode(mode: String) {
        dataStore.edit { it[PreferencesKeys.TELEGRAM_TOPIC_DISPLAY_MODE] = mode }
    }

    val transitionSettingsFlow: Flow<String?> = pref { it[PreferencesKeys.TRANSITION_SETTINGS] }
    suspend fun saveTransitionSettings(settingsJson: String) {
        dataStore.edit { it[PreferencesKeys.TRANSITION_SETTINGS] = settingsJson }
    }

    val beta05CleanInstallDisclaimerDismissedFlow: Flow<Boolean> = pref { it[PreferencesKeys.BETA_05_CLEAN_INSTALL_DISCLAIMER_DISMISSED] ?: false }
    suspend fun setBeta05CleanInstallDisclaimerDismissed(dismissed: Boolean) {
        dataStore.edit { it[PreferencesKeys.BETA_05_CLEAN_INSTALL_DISCLAIMER_DISMISSED] = dismissed }
    }

    val mockGenresEnabledFlow: Flow<Boolean> = pref { it[PreferencesKeys.MOCK_GENRES_ENABLED] ?: false }
    suspend fun setMockGenresEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.MOCK_GENRES_ENABLED] = enabled }
    }

    val playlistSongOrderModesFlow: Flow<Map<String, String>> = pref {
        val jsonString = it[PreferencesKeys.PLAYLIST_SONG_ORDER_MODES] ?: return@pref emptyMap()
        try {
            json.decodeFromString<Map<String, String>>(jsonString)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun setPlaylistSongOrderModes(modes: Map<String, String>) {
        dataStore.edit { it[PreferencesKeys.PLAYLIST_SONG_ORDER_MODES] = json.encodeToString(modes) }
    }

    val showTelegramCloudPlaylistsFlow: Flow<Boolean> = pref { it[PreferencesKeys.SHOW_TELEGRAM_CLOUD_PLAYLISTS] ?: true }
    suspend fun setShowTelegramCloudPlaylists(show: Boolean) {
        dataStore.edit { it[PreferencesKeys.SHOW_TELEGRAM_CLOUD_PLAYLISTS] = show }
    }

    val backupInfoDismissedFlow: Flow<Boolean> = pref { it[PreferencesKeys.BACKUP_INFO_DISMISSED] ?: false }
    suspend fun setBackupInfoDismissed(dismissed: Boolean) {
        dataStore.edit { it[PreferencesKeys.BACKUP_INFO_DISMISSED] = dismissed }
    }

    val appRebrandDialogShownFlow: Flow<Boolean> = pref { it[PreferencesKeys.APP_REBRAND_DIALOG_SHOWN] ?: false }
    suspend fun setAppRebrandDialogShown(shown: Boolean) {
        dataStore.edit { it[PreferencesKeys.APP_REBRAND_DIALOG_SHOWN] = shown }
    }

    suspend fun getLyricsSyncOffset(songId: String): Int {
        val preferences = dataStore.data.first()
        val offsetsJson = preferences[PreferencesKeys.LYRICS_SYNC_OFFSETS] ?: "{}"
        return try {
            val offsets = json.decodeFromString<Map<String, Int>>(offsetsJson)
            offsets[songId] ?: 0
        } catch (e: Exception) {
            0
        }
    }

    suspend fun setLyricsSyncOffset(songId: String, offsetMs: Int) {
        dataStore.edit { preferences ->
            val offsetsJson = preferences[PreferencesKeys.LYRICS_SYNC_OFFSETS] ?: "{}"
            val offsets = try {
                json.decodeFromString<Map<String, Int>>(offsetsJson).toMutableMap()
            } catch (e: Exception) {
                mutableMapOf()
            }
            offsets[songId] = offsetMs
            preferences[PreferencesKeys.LYRICS_SYNC_OFFSETS] = json.encodeToString(offsets)
        }
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

    companion object {
        /** Default character delimiters for splitting multi-artist tags */
        val DEFAULT_ARTIST_DELIMITERS = listOf("/", ";", ",", "+", "&")
        /** Default word-based delimiters (matched case-insensitively with whitespace boundaries) */
        val DEFAULT_ARTIST_WORD_DELIMITERS = listOf("featuring", "feat.", "feat", "ft.", "ft", "vs.", "vs", "versus", "with", "prod.", "prod")
        const val DEFAULT_ALBUM_ART_CACHE_LIMIT_MB = 200
        const val DEFAULT_EXTENSION_MEDIA_CACHE_LIMIT_MB = 500

        const val MIN_NAV_BAR_CORNER_RADIUS = 0
        const val MAX_NAV_BAR_CORNER_RADIUS = 48

        fun sanitizeNavBarCornerRadius(radius: Int): Int = radius.coerceIn(MIN_NAV_BAR_CORNER_RADIUS, MAX_NAV_BAR_CORNER_RADIUS)
    }

    /** Increments [value] by 1, wrapping back to 0 on overflow. */
    private fun incrementWrapped(value: Int?) =
        if (value == null || value == Int.MAX_VALUE) 0 else value + 1
}
