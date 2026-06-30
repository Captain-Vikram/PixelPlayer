package com.theveloper.pixelplay.data.service.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import dev.brahmkshatriya.echo.common.models.Streamable
import kotlinx.coroutines.runBlocking
import java.io.InputStream

@OptIn(UnstableApi::class)
class RawDataSource : BaseDataSource(true) {

    class Factory : DataSource.Factory {
        override fun createDataSource() = RawDataSource()
    }

    private var stream: InputStream? = null
    private var uri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        val streamable = dataSpec.customData as Streamable.Source.Raw
        val (source, total) = runBlocking {
            streamable.streamProvider!!.provide(dataSpec.position, dataSpec.length)
        }
        uri = dataSpec.uri
        stream = source

        val remaining = if (dataSpec.length != androidx.media3.common.C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            total - dataSpec.position
        }

        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val read = stream?.read(buffer, offset, length) ?: -1
        if (read == -1) return androidx.media3.common.C.RESULT_END_OF_INPUT
        
        bytesTransferred(read)
        return read
    }

    override fun getUri() = uri

    override fun close() {
        try {
            stream?.close()
        } finally {
            stream = null
            transferEnded()
        }
        uri = null
    }
}