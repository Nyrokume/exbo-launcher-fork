package dev.launcherfork

import dev.launcherfork.theme.Theme
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * Every theme must define the same set of looked-up color tokens, so components work in all of them.
 */
class ThemeTokensTest {
    private fun tokens(file: String): Set<String> {
        val css = Theme.resource("style/$file").readText()
        val root = Regex("""\.root\s*\{(.*?)}""", RegexOption.DOT_MATCHES_ALL).find(css)!!.groupValues[1]
        return Regex("""^\s*(-[a-z][a-z0-9-]*)\s*:""", RegexOption.MULTILINE).findAll(root)
            .map { it.groupValues[1] }.filterNot { it.startsWith("-fx-") }.toSet()
    }

    @TestFactory
    fun themesDefineAllBaseTokens(): List<DynamicTest> {
        val base = tokens("theme.css")
        return listOf("theme-shadcn.css", "theme-zone.css", "theme-frost.css").map { file ->
            DynamicTest.dynamicTest(file) {
                val missing = base - tokens(file) - "-transparent" // constant, inherited from theme.css
                assertTrue(missing.isEmpty(), "$file misses tokens $missing")
            }
        }
    }

    @Test
    fun zoneFontsRegister() {
        FxTestSupport.start()
        val families = FxTestSupport.onFx { Theme.zoneFonts }
        // Medium / Light / Regular share the family name "Roboto" (weight via -fx-font-weight)
        assertTrue(families.containsAll(listOf("Bebas Neue Regular", "Roboto")), "$families")
    }
}
