package com.creatora.app.core

import android.content.Context
import com.creatora.app.data.settings.DataStoreSettingsRepository
import com.creatora.app.domain.repository.SettingsRepository

/**
 * Simple manual dependency container. One instance lives in [com.creatora.app.CreatoraApplication].
 * Replace with a DI framework (e.g. Hilt) if the app outgrows it.
 */
interface AppContainer {
    val settingsRepository: SettingsRepository
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext

    override val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(appContext)
    }
}
