package com.theveloper.pixelplay.data.service.http

import android.content.Context
import android.content.Intent
import com.theveloper.pixelplay.data.stream.HttpServerController
import com.theveloper.pixelplay.data.stream.CastAccessPolicyDto

class KtorHttpServerController : HttpServerController {
    override val isServerRunning: Boolean
        get() = MediaFileHttpServerService.isServerRunning

    override val isServerStarting: Boolean
        get() = MediaFileHttpServerService.isServerStarting

    override val serverAddress: String?
        get() = MediaFileHttpServerService.serverAddress

    override val serverPrefixLength: Int
        get() = MediaFileHttpServerService.serverPrefixLength

    override val lastFailureReason: String?
        get() = MediaFileHttpServerService.lastFailureReason?.name

    override val lastFailureMessage: String?
        get() = MediaFileHttpServerService.lastFailureMessage

    override fun startServer(context: Context, castDeviceIpHint: String?) {
        val intent = Intent(context, MediaFileHttpServerService::class.java).apply {
            action = MediaFileHttpServerService.ACTION_START_SERVER
            castDeviceIpHint?.let { putExtra(MediaFileHttpServerService.EXTRA_CAST_DEVICE_IP, it) }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    override fun stopServer(context: Context) {
        context.stopService(Intent(context, MediaFileHttpServerService::class.java))
    }

    override fun configureCastSessionAccess(songIds: Collection<String>, castDeviceIpHint: String?): CastAccessPolicyDto {
        val policy = MediaFileHttpServerService.configureCastSessionAccess(songIds, castDeviceIpHint)
        return CastAccessPolicyDto(
            authToken = policy.authToken,
            allowedSongIds = policy.allowedSongIds,
            allowedClientAddresses = policy.allowedClientAddresses,
            enforceClientAddressAllowlist = policy.enforceClientAddressAllowlist
        )
    }
}
