package com.theveloper.pixelplay.data.stream

import android.content.Context

interface HttpServerController {
    val isServerRunning: Boolean
    val isServerStarting: Boolean
    val serverAddress: String?
    val serverPrefixLength: Int
    val lastFailureReason: String?
    val lastFailureMessage: String?

    fun startServer(context: Context, castDeviceIpHint: String?)
    fun stopServer(context: Context)
    fun configureCastSessionAccess(songIds: Collection<String>, castDeviceIpHint: String?): CastAccessPolicyDto
}

data class CastAccessPolicyDto(
    val authToken: String?,
    val allowedSongIds: Set<String>,
    val allowedClientAddresses: Set<String>,
    val enforceClientAddressAllowlist: Boolean
)
