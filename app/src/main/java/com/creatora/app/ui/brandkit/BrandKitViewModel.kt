package com.creatora.app.ui.brandkit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.model.BrandKit
import com.creatora.app.domain.model.FontFile
import com.creatora.app.domain.model.normalizeHexColor
import com.creatora.app.domain.repository.BrandKitRepository
import com.creatora.app.domain.repository.UserFiles
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A one-time message for the snackbar. */
enum class BrandKitMessage { INVALID_LOGO, INVALID_FONT, IMPORT_FAILED }

@HiltViewModel
class BrandKitViewModel @Inject constructor(
    private val repository: BrandKitRepository,
    private val userFiles: UserFiles,
) : ViewModel() {

    val brandKit: StateFlow<BrandKit> = repository.brandKit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrandKit())

    private val _message = MutableStateFlow<BrandKitMessage?>(null)
    val message: StateFlow<BrandKitMessage?> = _message

    fun messageShown() {
        _message.value = null
    }

    fun setLogo(uri: String) = viewModelScope.launch {
        val file = userFiles.import(uri) ?: return@launch report(BrandKitMessage.IMPORT_FAILED)
        if (!file.mimeType.startsWith("image/")) {
            userFiles.discard(file.uri)
            return@launch report(BrandKitMessage.INVALID_LOGO)
        }
        val old = repository.brandKit.first().logoUri
        repository.update { it.copy(logoUri = file.uri) }
        old?.let { userFiles.discard(it) }
    }

    fun removeLogo() = viewModelScope.launch {
        val old = repository.brandKit.first().logoUri ?: return@launch
        repository.update { it.copy(logoUri = null) }
        userFiles.discard(old)
    }

    /** Adds a colour from user text. Returns false if it isn't a valid six-digit hex colour. */
    fun addColor(text: String): Boolean {
        val color = normalizeHexColor(text) ?: return false
        viewModelScope.launch {
            repository.update { if (color in it.colors) it else it.copy(colors = it.colors + color) }
        }
        return true
    }

    fun removeColor(color: String) = viewModelScope.launch {
        repository.update { it.copy(colors = it.colors - color) }
    }

    fun addFont(uri: String) = viewModelScope.launch {
        val file = userFiles.import(uri) ?: return@launch report(BrandKitMessage.IMPORT_FAILED)
        if (!file.name.lowercase().let { it.endsWith(".ttf") || it.endsWith(".otf") }) {
            userFiles.discard(file.uri)
            return@launch report(BrandKitMessage.INVALID_FONT)
        }
        repository.update { it.copy(fonts = it.fonts + FontFile(file.name, file.uri)) }
    }

    fun removeFont(font: FontFile) = viewModelScope.launch {
        repository.update { it.copy(fonts = it.fonts - font) }
        userFiles.discard(font.uri)
    }

    private fun report(message: BrandKitMessage) {
        _message.value = message
    }
}
