package com.creatora.app.ui.thumbnail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.model.Asset
import com.creatora.app.domain.model.AssetType
import com.creatora.app.domain.repository.AssetRepository
import com.creatora.app.domain.repository.BrandKitRepository
import com.creatora.app.domain.repository.MediaSaver
import com.creatora.app.domain.repository.ProjectRepository
import com.creatora.app.domain.repository.ThumbnailRenderer
import com.creatora.app.ui.render.GalleryMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ThumbnailUiState(
    /** The text on the thumbnail. Starts as the project's title. */
    val text: String = "",
    val images: List<Asset> = emptyList(),
    /** The chosen picture; null means a plain brand colour. */
    val selectedImageUri: String? = null,
    val working: Boolean = false,
    val resultUri: String? = null,
    val failed: Boolean = false,
    val galleryMessage: GalleryMessage? = null,
)

@HiltViewModel
class ThumbnailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projects: ProjectRepository,
    assets: AssetRepository,
    private val brandKit: BrandKitRepository,
    private val renderer: ThumbnailRenderer,
    private val saver: MediaSaver,
) : ViewModel() {

    private val projectId: Long = savedStateHandle[ARG_PROJECT_ID] ?: 0L
    private val edits = MutableStateFlow(ThumbnailUiState())
    private var textTouched = false

    val uiState: StateFlow<ThumbnailUiState> = combine(edits, assets.observeAll(), projects.observe(projectId)) { edited, assetList, project ->
        edited.copy(
            text = if (textTouched) edited.text else project?.title.orEmpty(),
            images = assetList.filter { it.type == AssetType.IMAGE && (it.projectId == null || it.projectId == projectId) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThumbnailUiState())

    fun setText(text: String) {
        textTouched = true
        edits.value = edits.value.copy(text = text)
    }

    fun selectImage(uri: String?) {
        edits.value = edits.value.copy(selectedImageUri = uri)
    }

    fun create() {
        val state = uiState.value
        if (state.working) return
        edits.value = edits.value.copy(working = true, failed = false, galleryMessage = null)
        viewModelScope.launch {
            val colour = brandKit.brandKit.first().colors.firstOrNull()
            val uri = renderer.render(state.selectedImageUri, state.text, colour)
            edits.value = edits.value.copy(working = false, resultUri = uri ?: edits.value.resultUri, failed = uri == null)
        }
    }

    fun saveToGallery(displayName: String) = save { saver.saveToGallery(it, displayName, "image/png") }
    fun saveTo(destinationUri: String) = save { saver.copyTo(it, destinationUri) }

    private fun save(action: suspend (String) -> Boolean) {
        val uri = edits.value.resultUri ?: return
        viewModelScope.launch {
            edits.value = edits.value.copy(galleryMessage = if (action(uri)) GalleryMessage.SAVED else GalleryMessage.FAILED)
        }
    }

    companion object {
        const val ARG_PROJECT_ID = "projectId"
    }
}
