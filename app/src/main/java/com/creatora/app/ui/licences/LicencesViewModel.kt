package com.creatora.app.ui.licences

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.model.Asset
import com.creatora.app.domain.model.LicenceEntry
import com.creatora.app.domain.repository.AssetRepository
import com.creatora.app.domain.repository.LicenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** An entry with the name of the asset it belongs to (null if the asset is gone). */
data class LicenceRow(val entry: LicenceEntry, val assetName: String?)

data class LicencesUiState(
    /** The asset being logged, or null when showing every entry. */
    val asset: Asset? = null,
    val rows: List<LicenceRow> = emptyList(),
)

/** Shows the licence log of one asset (route argument `assetId` >= 0) or of every asset. */
@HiltViewModel
class LicencesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val licences: LicenceRepository,
    assets: AssetRepository,
) : ViewModel() {

    private val assetId: Long = savedStateHandle[ARG_ASSET_ID] ?: ALL

    val uiState: StateFlow<LicencesUiState> = combine(
        if (assetId == ALL) licences.observeAll() else licences.observeForAsset(assetId),
        assets.observeAll(),
    ) { entries, assetList ->
        val byId = assetList.associateBy { it.id }
        LicencesUiState(
            asset = byId[assetId],
            rows = entries.map { LicenceRow(it, byId[it.assetId]?.name) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LicencesUiState())

    /** Adds an entry to the asset this screen is about. Needs a source and a licence. */
    fun addEntry(source: String, licence: String, author: String, url: String) {
        if (assetId == ALL || source.isBlank() || licence.isBlank()) return
        viewModelScope.launch {
            licences.save(
                LicenceEntry(
                    assetId = assetId,
                    source = source.trim(),
                    licence = licence.trim(),
                    author = author.trim(),
                    url = url.trim(),
                ),
            )
        }
    }

    fun delete(entry: LicenceEntry) {
        viewModelScope.launch { licences.delete(entry.id) }
    }

    companion object {
        const val ARG_ASSET_ID = "assetId"
        const val ALL = -1L
    }
}
