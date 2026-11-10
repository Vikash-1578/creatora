package com.creatora.app

import android.app.Application
import com.creatora.app.core.AppContainer
import com.creatora.app.core.DefaultAppContainer

class CreatoraApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
