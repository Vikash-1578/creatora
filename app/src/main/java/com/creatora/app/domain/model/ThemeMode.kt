package com.creatora.app.domain.model

/** The user's appearance choice. [SYSTEM] follows the phone's dark-mode setting. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        val Default = SYSTEM
    }
}
