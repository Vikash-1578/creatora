package com.creatora.app.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.model.AiProvider
import com.creatora.app.domain.model.AiProviderConfig
import com.creatora.app.domain.repository.AiProviderStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AiProvidersViewModel @Inject constructor(
    private val store: AiProviderStore,
) : ViewModel() {

    val configs: StateFlow<List<AiProviderConfig>> = store.configs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Saves a new key (only if [key] isn't blank) and the model name. The key is never read back to the screen. */
    fun save(provider: AiProvider, key: String, model: String) {
        viewModelScope.launch {
            if (key.isNotBlank()) store.setKey(provider, key)
            store.setModel(provider, if (model.trim() == provider.defaultModel) "" else model)
        }
    }

    fun removeKey(provider: AiProvider) {
        viewModelScope.launch { store.removeKey(provider) }
    }
}
