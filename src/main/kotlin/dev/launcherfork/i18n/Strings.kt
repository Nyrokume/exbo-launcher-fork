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

/** CLDR plural categories used by the bundles: Russian has one / few / many, English one / many (= "other"). */
enum class PluralForm { ONE, FEW, MANY }

/** Russian: 1, 21, 101 игрок; 2-4, 22-24 игрока; 0, 5-20, 25-30, 11-14, 111-114 игроков. English: 1 player, else players. */
fun pluralForm(n: Long, lang: String): PluralForm {
    val a = kotlin.math.abs(n)
    if (lang != "ru") return if (a == 1L) PluralForm.ONE else PluralForm.MANY
    val d = a % 10; val h = a % 100
    return when {
        d == 1L && h != 11L -> PluralForm.ONE
        d in 2L..4L && h !in 12L..14L -> PluralForm.FEW
        else -> PluralForm.MANY
    }
}

/** `<baseKey>.one|few|many` for [n] in [lang], formatted with [args] (e.g. the localized number). */
fun ResourceBundle.plural(baseKey: String, n: Long, lang: String, vararg args: Any): String =
    text("$baseKey.${pluralForm(n, lang).name.lowercase()}", *args)
