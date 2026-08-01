package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// import com.theveloper.pixelplay.data.gdrive.GDriveRepository
import com.theveloper.pixelplay.data.jellyfin.JellyfinRepository

// import com.theveloper.pixelplay.data.netease.NeteaseRepository
// import com.theveloper.pixelplay.data.qqmusic.QqMusicRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.repository.ExtensionRepository
import dev.brahmkshatriya.echo.extension.loader.db.models.UserEntity.Companion.toCurrentUser
import com.theveloper.pixelplay.data.repository.TelegramRepositoryContract
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ExternalServiceAccount {
    TELEGRAM,
    GOOGLE_DRIVE,
    NETEASE,
    QQ_MUSIC,
    NAVIDROME,
    JELLYFIN
}

data class ExternalAccountUiModel(
    val service: ExternalServiceAccount?,
    val title: String,
    val accountLabel: String,
    val syncedContentLabel: String,
    val isLoggingOut: Boolean,
    val extensionId: String? = null,
    val iconUrl: String? = null
)

data class AccountsUiState(
    val connectedAccounts: List<ExternalAccountUiModel> = emptyList(),
    val disconnectedServices: List<ExternalServiceAccount> = emptyList()
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val telegramRepository: TelegramRepositoryContract,
    private val musicRepository: MusicRepository,
    private val gDriveRepository: com.theveloper.pixelplay.data.repository.GDriveRepositoryContract,
    private val neteaseRepository: com.theveloper.pixelplay.data.repository.NeteaseRepositoryContract,
    private val qqMusicRepository: com.theveloper.pixelplay.data.repository.QqMusicRepositoryContract,
    private val navidromeRepository: com.theveloper.pixelplay.data.repository.NavidromeRepositoryContract,
    private val jellyfinRepository: JellyfinRepository,
    private val extensionRepository: ExtensionRepository
) : ViewModel() {

    private val loggingOutServices = MutableStateFlow<Set<ExternalServiceAccount>>(emptySet())
    private val loggingOutExtensions = MutableStateFlow<Set<String>>(emptySet())

    private val telegramStateFlow = combine(
        telegramRepository.isAuthorizedFlow
            .distinctUntilChanged(),
        musicRepository.getAllTelegramChannels().map { it.size }
    ) { connected, channelCount ->
        connected to channelCount
    }

    private val gDriveStateFlow = combine(
        gDriveRepository.isLoggedInFlow,
        gDriveRepository.getFolderCount()
    ) { connected, folderCount ->
        connected to folderCount
    }

    private val neteaseStateFlow = combine(
        neteaseRepository.isLoggedInFlow,
        neteaseRepository.getPlaylistCount()
    ) { connected, playlistCount ->
        connected to playlistCount
    }

    private val qqMusicStateFlow = combine(
        qqMusicRepository.isLoggedInFlow,
        qqMusicRepository.getPlaylistCount()
    ) { connected, playlistCount ->
        connected to playlistCount
    }

    private val navidromeStateFlow = combine(
        navidromeRepository.isLoggedInFlow,
        navidromeRepository.getPlaylistCount()
    ) { connected, playlistCount ->
        connected to playlistCount
    }

    private val jellyfinStateFlow = combine(
        jellyfinRepository.isLoggedInFlow,
        jellyfinRepository.getPlaylistCount()
    ) { connected, playlistCount ->
        connected to playlistCount
    }

    val uiState: StateFlow<AccountsUiState> = combine(
        combine(
            listOf(
                telegramStateFlow,
                gDriveStateFlow,
                neteaseStateFlow,
                qqMusicStateFlow,
                navidromeStateFlow,
                jellyfinStateFlow
            )
        ) { it.toList() },
        extensionRepository.activeExtensionUsers,
        loggingOutServices,
        loggingOutExtensions
    ) { states, extensionUsers, activeLogouts, activeExtensionLogouts ->
        val (telegramConnected, telegramChannelCount) = states[0] as Pair<Boolean, Int>
        val (gDriveConnected, gDriveFolderCount) = states[1] as Pair<Boolean, Int>
        val (neteaseConnected, neteasePlaylistCount) = states[2] as Pair<Boolean, Int>
        val (qqConnected, qqPlaylistCount) = states[3] as Pair<Boolean, Int>
        val (navidromeConnected, navidromePlaylistCount) = states[4] as Pair<Boolean, Int>
        val (jellyfinConnected, jellyfinPlaylistCount) = states[5] as Pair<Boolean, Int>

        val connectedAccounts = buildList {
            if (telegramConnected) {
                add(
                    ExternalAccountUiModel(
                        service = ExternalServiceAccount.TELEGRAM,
                        title = "Telegram",
                        accountLabel = "Active Telegram session",
                        syncedContentLabel = formatCount(
                            count = telegramChannelCount,
                            singular = "synced channel",
                            plural = "synced channels"
                        ),
                        isLoggingOut = ExternalServiceAccount.TELEGRAM in activeLogouts
                    )
                )
            }
            if (gDriveConnected) {
                add(
                    ExternalAccountUiModel(
                        service = ExternalServiceAccount.GOOGLE_DRIVE,
                        title = "Google Drive",
                        accountLabel = gDriveRepository.userDisplayName
                            ?.takeIf { it.isNotBlank() }
                            ?: gDriveRepository.userEmail
                                ?.takeIf { it.isNotBlank() }
                            ?: "Google account connected",
                        syncedContentLabel = formatCount(
                            count = gDriveFolderCount,
                            singular = "synced folder",
                            plural = "synced folders"
                        ),
                        isLoggingOut = ExternalServiceAccount.GOOGLE_DRIVE in activeLogouts
                    )
                )
            }
            if (neteaseConnected) {
                add(
                    ExternalAccountUiModel(
                        service = ExternalServiceAccount.NETEASE,
                        title = "Netease Music",
                        accountLabel = neteaseRepository.userNickname
                            ?.takeIf { it.isNotBlank() }
                            ?: "Netease account connected",
                        syncedContentLabel = formatCount(
                            count = neteasePlaylistCount,
                            singular = "synced playlist",
                            plural = "synced playlists"
                        ),
                        isLoggingOut = ExternalServiceAccount.NETEASE in activeLogouts
                    )
                )
            }
            if (qqConnected) {
                add(
                    ExternalAccountUiModel(
                        service = ExternalServiceAccount.QQ_MUSIC,
                        title = "QQ Music",
                        accountLabel = qqMusicRepository.userNickname
                            ?.takeIf { it.isNotBlank() }
                            ?: "QQ Music account connected",
                        syncedContentLabel = formatCount(
                            count = qqPlaylistCount,
                            singular = "synced playlist",
                            plural = "synced playlists"
                        ),
                        isLoggingOut = ExternalServiceAccount.QQ_MUSIC in activeLogouts
                    )
                )
            }
            if (navidromeConnected) {
                add(
                    ExternalAccountUiModel(
                        service = ExternalServiceAccount.NAVIDROME,
                        title = "Subsonic",
                        accountLabel = navidromeRepository.username
                            ?.takeIf { it.isNotBlank() }
                            ?: "Subsonic account connected",
                        syncedContentLabel = formatCount(
                            count = navidromePlaylistCount,
                            singular = "synced playlist",
                            plural = "synced playlists"
                        ),
                        isLoggingOut = ExternalServiceAccount.NAVIDROME in activeLogouts
                    )
                )
            }
            if (jellyfinConnected) {
                add(
                    ExternalAccountUiModel(
                        service = ExternalServiceAccount.JELLYFIN,
                        title = "Jellyfin",
                        accountLabel = jellyfinRepository.username
                            ?.takeIf { it.isNotBlank() }
                            ?: "Jellyfin account connected",
                        syncedContentLabel = formatCount(
                            count = jellyfinPlaylistCount,
                            singular = "synced playlist",
                            plural = "synced playlists"
                        ),
                        isLoggingOut = ExternalServiceAccount.JELLYFIN in activeLogouts
                    )
                )
            }

            // Dynamic extension active logins
            extensionUsers.forEach { userEntity ->
                val user = userEntity.user.getOrNull() ?: return@forEach
                val extension = extensionRepository.allExtensions.value.find { it.metadata.id == userEntity.extId }
                val iconUrl = when (val icon = extension?.metadata?.icon) {
                    is dev.brahmkshatriya.echo.common.models.ImageHolder.NetworkRequestImageHolder -> icon.request.url
                    is dev.brahmkshatriya.echo.common.models.ImageHolder.ResourceUriImageHolder -> icon.uri
                    else -> null
                }
                add(
                    ExternalAccountUiModel(
                        service = null,
                        title = extension?.metadata?.name ?: userEntity.extId,
                        accountLabel = user.name.takeIf { it.isNotBlank() } ?: "Connected",
                        syncedContentLabel = user.subtitle?.takeIf { it.isNotBlank() } ?: "Extension account",
                        isLoggingOut = userEntity.extId in activeExtensionLogouts,
                        extensionId = userEntity.extId,
                        iconUrl = iconUrl
                    )
                )
            }
        }

        val disconnectedServices = buildList {
            if (!telegramConnected) add(ExternalServiceAccount.TELEGRAM)
            if (!gDriveConnected) add(ExternalServiceAccount.GOOGLE_DRIVE)
            if (!neteaseConnected) add(ExternalServiceAccount.NETEASE)
            if (!qqConnected) add(ExternalServiceAccount.QQ_MUSIC)
            if (!navidromeConnected) add(ExternalServiceAccount.NAVIDROME)
            if (!jellyfinConnected) add(ExternalServiceAccount.JELLYFIN)
        }

        AccountsUiState(
            connectedAccounts = connectedAccounts,
            disconnectedServices = disconnectedServices
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    fun logout(service: ExternalServiceAccount) {
        if (service in loggingOutServices.value) return

        viewModelScope.launch {
            loggingOutServices.update { it + service }
            try {
                runCatching {
                    when (service) {
                        ExternalServiceAccount.TELEGRAM -> {
                            telegramRepository.logout()
                            telegramRepository.clearMemoryCache()
                            musicRepository.clearTelegramData()
                        }
                        ExternalServiceAccount.GOOGLE_DRIVE -> gDriveRepository.logout()
                        ExternalServiceAccount.NETEASE -> neteaseRepository.logout()
                        ExternalServiceAccount.QQ_MUSIC -> qqMusicRepository.logout()
                        ExternalServiceAccount.NAVIDROME -> navidromeRepository.logout()
                        ExternalServiceAccount.JELLYFIN -> jellyfinRepository.logout()
                    }
                }
            } finally {
                loggingOutServices.update { it - service }
            }
        }
    }

    fun logoutExtension(extensionId: String) {
        if (extensionId in loggingOutExtensions.value) return

        viewModelScope.launch {
            loggingOutExtensions.update { it + extensionId }
            try {
                runCatching {
                    extensionRepository.removeLoginSession(extensionId)
                }
            } finally {
                loggingOutExtensions.update { it - extensionId }
            }
        }
    }

    private fun formatCount(count: Int, singular: String, plural: String): String {
        return if (count == 1) {
            "1 $singular"
        } else {
            "$count $plural"
        }
    }
}

