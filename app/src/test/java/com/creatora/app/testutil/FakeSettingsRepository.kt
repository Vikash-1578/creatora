package com.creatora.app.testutil

import com.creatora.app.domain.model.ThemeMode
import com.creatora.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository(initial: ThemeMode = ThemeMode.Default) : SettingsRepository {
    override val themeMode = MutableStateFlow(initial)
    override suspend fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
    }
}
