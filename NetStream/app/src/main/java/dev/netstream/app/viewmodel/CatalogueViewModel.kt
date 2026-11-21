package dev.netstream.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.netstream.domain.CatalogueRepository
import dev.netstream.domain.VideoItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

sealed interface CatalogueState {
    data object Loading : CatalogueState

    data class Ready(val items: List<VideoItem>) : CatalogueState

    data class Failed(val message: String) : CatalogueState
}

class CatalogueViewModel(private val repository: CatalogueRepository) : ViewModel() {
    private val refresh = MutableStateFlow(0L)

    /** Release the remote listener when the catalogue stays offscreen or the app stops. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<CatalogueState> =
        refresh
            .flatMapLatest {
                repository
                    .observeVideos()
                    .map<List<VideoItem>, CatalogueState> { CatalogueState.Ready(it) }
                    .onStart { emit(CatalogueState.Loading) }
                    .catch {
                        emit(
                            CatalogueState.Failed(
                                it.localizedMessage ?: "Unable to load catalogue."
                            )
                        )
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogueState.Loading)

    fun reload() {
        refresh.update { it + 1 }
    }
}
