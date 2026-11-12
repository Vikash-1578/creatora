package com.creatora.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.repository.HomePrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal const val DEFAULT_REGION = "US"

internal fun deviceRegion(): String = Locale.getDefault().country.ifEmpty { DEFAULT_REGION }.uppercase()

@HiltViewModel
class RegionViewModel @Inject constructor(
    private val prefs: HomePrefsRepository,
) : ViewModel() {
    val region: StateFlow<String> = prefs.region
        .map { it ?: deviceRegion() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), deviceRegion())

    fun select(code: String) {
        viewModelScope.launch { prefs.setRegion(code.uppercase(Locale.ROOT)) }
    }
}
