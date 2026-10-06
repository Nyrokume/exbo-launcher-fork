package dev.launcherfork.ui.components

import dev.launcherfork.theme.Theme
import javafx.beans.binding.Bindings
import javafx.geometry.VPos
import javafx.scene.control.PasswordField
import javafx.scene.control.skin.TextFieldSkin
import javafx.scene.layout.Pane
import javafx.scene.shape.Path
import javafx.scene.text.Font
import javafx.scene.text.Text

/**
 * Skin of every [PasswordField], installed from theme.css (`.password-field { -fx-skin: ... }`), so it covers all
 * themes and every form (login, registration, legacy login) without touching their FXML.
 *
 * Why: modena's TextFieldSkin masks with U+25CF BLACK CIRCLE. None of the bundled fonts (Roboto, Manrope, Europe)
 * has it, so JavaFX falls back to a system font where it is a cap-height disc - the huge dots of the login field.
 *
 * What: [maskText] puts one U+2022 BULLET per character, and the masked text node is drawn in the bundled
 * "Launcher Mask" font (style/font/LauncherMask.ttf, tools/generate_mask_font.py) at the field's font size: a round
 * dot ~0.36 em wide inside the x-height band, ~0.27 em apart, like the small dots of stalzone.com/login. The mask
 * font has Roboto's line metrics, so the caret keeps its height. The prompt text keeps the field font, and
 * [layoutChildren] keeps both baselines where the stock skin puts them for the field font (the stock skin centres
 * the prompt on the masked text node's metrics, which would move it by a pixel).
 *
 * Caret, selection and mouse hit-testing are computed by TextFieldSkin on that same text node, and the mask is
 * exactly one glyph per character, so they still map 1:1 to the password. If the mask font cannot be loaded the
 * field just shows the field font's own U+2022.
 */
class BulletPasswordFieldSkin(control: PasswordField) : TextFieldSkin(control) {
    private val textNode: Text? = findTextNode()
    /** Hidden empty text in the field font, next to the masked text: its line metrics (ascent / descent). */
    private val fieldFontProbe = Text().apply { isVisible = false; isManaged = false; styleClass += "text" }

    init {
        val family = maskFamily
        if (family != null && textNode != null) {
            textNode.fontProperty().unbind()
            textNode.fontProperty().bind(Bindings.createObjectBinding({ Font.font(family, control.font.size) }, control.fontProperty()))
            fieldFontProbe.fontProperty().bind(control.fontProperty())
            (textNode.parent as Pane).children += fieldFontProbe
        }
    }

    override fun maskText(txt: String): String = MASK.toString().repeat(txt.length)

    override fun layoutChildren(x: Double, y: Double, w: Double, h: Double) {
        super.layoutChildren(x, y, w, h)
        val pane = fieldFontProbe.parent as? Pane ?: return // mask font not loaded: stock layout
        // same formula as TextFieldSkin.layoutChildren, but with the field font's metrics instead of the mask font's
        val ascent = fieldFontProbe.baselineOffset
        val descent = fieldFontProbe.layoutBounds.height - ascent
        val baseline = when (skinnable.alignment.vpos) {
            VPos.TOP -> ascent
            VPos.CENTER -> (ascent + pane.height - descent) / 2
            else -> pane.height - descent
        }
        pane.children.filterIsInstance<Text>().forEach { it.y = baseline } // masked text + prompt (+ probe)
    }

    /** The text node the dots are drawn in (null only if TextFieldSkin's structure changed). */
    internal fun maskTextNode(): Text? = textNode

    /**
     * TextFieldSkin's text node is private: it sits in the text pane (the child Pane holding the selection-highlight
     * Path); the prompt node, if any, is inserted there too but has its visibility bound, so the masked node is the
     * Text with a free visible property.
     */
    private fun findTextNode(): Text? = children.filterIsInstance<Pane>()
        .firstOrNull { pane -> pane.children.any { it is Path } }
        ?.children?.filterIsInstance<Text>()?.firstOrNull { !it.visibleProperty().isBound }

    companion object {
        const val MASK: Char = '\u2022'
        const val MASK_FONT_FILE = "LauncherMask.ttf"

        /** Family name of the mask font, registered on first use; null if it could not be loaded. */
        val maskFamily: String? by lazy {
            BulletPasswordFieldSkin::class.java.getResourceAsStream(Theme.STYLE_ROOT + "font/" + MASK_FONT_FILE)
                ?.use { Font.loadFont(it, 12.0)?.family }
        }
    }
}
