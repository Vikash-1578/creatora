package com.creatora.app.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.creatora.app.CreatoraApplication
import com.creatora.app.core.AppContainer

/** Gives ViewModel factories access to the app's dependency container. */
fun CreationExtras.appContainer(): AppContainer =
    (checkNotNull(this[APPLICATION_KEY]) as CreatoraApplication).container
