package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import com.theveloper.pixelplay.data.repository.ExtensionRepository

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryStateHolder: LibraryStateHolder,
    private val extensionRepository: ExtensionRepository
) : ViewModel() {

    val songsPagingFlow = libraryStateHolder.songsPagingFlow.cachedIn(viewModelScope)

    val albumsPagingFlow = libraryStateHolder.albumsPagingFlow.cachedIn(viewModelScope)

    val artistsPagingFlow = libraryStateHolder.artistsPagingFlow.cachedIn(viewModelScope)

    val favoritesPagingFlow = libraryStateHolder.favoritesPagingFlow.cachedIn(viewModelScope)

    val favoriteSongCountFlow = libraryStateHolder.favoriteSongCountFlow

    val isLoadingLibrary = libraryStateHolder.isLoadingLibrary

    val currentSourceScope = libraryStateHolder.currentSourceScope

    val libraryShelves = extensionRepository.libraryShelves
    val isLoadingLibraryFeed = extensionRepository.isLoadingLibraryFeed

    fun setSourceScope(scope: com.theveloper.pixelplay.data.model.SourceScope) {
        libraryStateHolder.setSourceScope(scope)
    }
}
