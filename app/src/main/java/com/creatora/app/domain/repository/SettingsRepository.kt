package com.creatora.app.domain.repository

import com.creatora.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/** Persistent user settings. */
interface SettingsRepository {
    /** Emits the saved theme mode, or [ThemeMode.Default] if none was saved yet. */
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)
}
