package dev.launcherfork.i18n

import java.util.Locale
import java.util.ResourceBundle

/** Local replacement for the original server-fed LangManager: plain properties bundles (ru + en). */
object Strings {
    const val BUNDLE = "dev.launcherfork.ui.i18n.messages"
    val SUPPORTED = listOf("ru", "en")

    fun bundle(lang: String): ResourceBundle = ResourceBundle.getBundle(
        BUNDLE,
        Locale.forLanguageTag(if (lang in SUPPORTED) lang else "ru"),
        ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES),
    )
}

/** Missing keys fall back to the key itself, like the original. */
fun ResourceBundle.text(key: String, vararg args: Any): String {
    val raw = if (containsKey(key)) getString(key) else key
    return if (args.isEmpty()) raw else raw.format(*args)
}
