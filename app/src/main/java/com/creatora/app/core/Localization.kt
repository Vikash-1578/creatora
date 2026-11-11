package com.creatora.app.core

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import com.creatora.app.domain.account.AppLanguage
import java.util.Locale

/** The locale to use for [language], or null to keep the device's. */
fun AppLanguage?.toLocale(): Locale? = this?.let { Locale.forLanguageTag(it.tag) }

/** A context whose strings come from [language]. Use for text built outside Compose (notifications). */
fun Context.withLanguage(language: AppLanguage?): Context {
    val locale = language.toLocale() ?: return this
    val config = Configuration(resources.configuration).apply { setLocale(locale) }
    return createConfigurationContext(config)
}

/**
 * A wrapper that only swaps the resources, so the original context (and the Activity inside it) stays reachable
 * by unwrapping. Used by the root composable so every screen follows the in-app language.
 */
fun Context.localized(language: AppLanguage?): Context {
    val locale = language.toLocale() ?: return this
    val config = Configuration(resources.configuration).apply { setLocale(locale) }
    val localizedResources = createConfigurationContext(config).resources
    return object : ContextWrapper(this) {
        override fun getResources(): Resources = localizedResources
    }
}
