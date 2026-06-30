package com.theveloper.pixelplay.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.Album as AppAlbum
import com.theveloper.pixelplay.data.model.Artist as AppArtist
import com.theveloper.pixelplay.extensions.core.toSong
import com.theveloper.pixelplay.extensions.core.toAppAlbum
import com.theveloper.pixelplay.extensions.core.toAppArtist
import dev.brahmkshatriya.echo.common.models.*
import dev.brahmkshatriya.echo.common.helpers.PagedData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExtensionMediaPagingSource<T : Any>(
    private val extensionId: String,
    private val pagedData: PagedData<Shelf>,
    private val mediaType: Class<T>
) : PagingSource<String, T>() {

    override fun getRefreshKey(state: PagingState<String, T>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, T> = withContext(Dispatchers.IO) {
        try {
            val page = pagedData.loadPage(params.key)
            val shelves = page.data
            
            val items = shelves.flatMap { shelf ->
                when (shelf) {
                    is Shelf.Lists<*> -> shelf.list
                    is Shelf.Item -> listOf(shelf.media)
                    else -> emptyList()
                }
            }.mapNotNull { echoMediaItem ->
                mapToAppModel(echoMediaItem)
            }

            @Suppress("UNCHECKED_CAST")
            LoadResult.Page(
                data = items as List<T>,
                prevKey = null,
                nextKey = page.continuation
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToAppModel(item: Any): Any? {
        return when (mediaType) {
            Song::class.java -> {
                if (item is Track) item.toSong(extensionId) else null
            }
            AppAlbum::class.java -> {
                if (item is Album) item.toAppAlbum(extensionId) else null
            }
            AppArtist::class.java -> {
                if (item is Artist) item.toAppArtist(extensionId) else null
            }
            else -> null
        }
    }
}
