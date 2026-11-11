package com.creatora.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.creatora.app.domain.model.BrandKit
import com.creatora.app.domain.model.FontFile
import com.creatora.app.domain.repository.BrandKitRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Brand kit stored in the settings DataStore. Lists are newline-separated; fonts are "name<TAB>uri". */
class DataStoreBrandKitRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : BrandKitRepository {

    override val brandKit: Flow<BrandKit> = dataStore.data.map { it.toBrandKit() }

    override suspend fun update(transform: (BrandKit) -> BrandKit) {
        dataStore.edit { prefs ->
            val kit = transform(prefs.toBrandKit())
            if (kit.logoUri == null) prefs.remove(LOGO) else prefs[LOGO] = kit.logoUri
            prefs[COLORS] = kit.colors.joinToString("\n")
            prefs[FONTS] = kit.fonts.joinToString("\n") { "${it.name}\t${it.uri}" }
        }
    }

    private fun Preferences.toBrandKit() = BrandKit(
        logoUri = this[LOGO],
        colors = this[COLORS].lines(),
        fonts = this[FONTS].lines().mapNotNull { line ->
            line.split('\t').takeIf { it.size == 2 }?.let { FontFile(it[0], it[1]) }
        },
    )

    private fun String?.lines() = this?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    private companion object {
        val LOGO = stringPreferencesKey("brand_logo_uri")
        val COLORS = stringPreferencesKey("brand_colors")
        val FONTS = stringPreferencesKey("brand_fonts")
    }
}
