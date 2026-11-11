package com.creatora.app.domain.account

/** Languages the app is written in. [tag] is the BCP 47 tag; Hinglish is Hindi written in Latin letters. */
enum class AppLanguage(val tag: String, val nativeName: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिंदी"),
    HINGLISH("hi-Latn", "Hinglish");

    companion object {
        fun fromTag(tag: String?): AppLanguage? = entries.firstOrNull { it.tag == tag }

        /** The device's language as an app language: the default until the user chooses one. */
        fun deviceDefault(locale: java.util.Locale = java.util.Locale.getDefault()): AppLanguage = forDevice(locale.language, locale.script)

        /** The choice to preselect for a device: Hindi devices get Hindi (Hinglish for Latin script), everything else English. */
        fun forDevice(language: String, script: String = ""): AppLanguage = when {
            language != "hi" -> ENGLISH
            script == "Latn" -> HINGLISH
            else -> HINDI
        }
    }
}
