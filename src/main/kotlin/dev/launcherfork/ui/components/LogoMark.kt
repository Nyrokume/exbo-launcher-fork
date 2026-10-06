package dev.launcherfork.ui.components

import dev.launcherfork.theme.Theme
import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.scene.Group
import javafx.scene.layout.Region
import javafx.scene.shape.FillRule
import javafx.scene.shape.SVGPath

/**
 * The EXBO logo as vector shapes: the <path> elements of ui/style/image/exbo-logo.svg (viewBox 0 0 106 34) become
 * [SVGPath] nodes (one per path, fill rule kept), grouped and scaled UNIFORMLY as one Group (scaleX = scaleY =
 * logoWidth / 106), so the proportions never change. Colour comes from CSS (`.logo-path { -fx-fill: -foreground; }`),
 * the fill colour written in the SVG file is ignored; fill-rule="evenodd" is kept per path.
 * Usage in FXML: `<LogoMark logoWidth="170" styleClass="logo-mark"/>`.
 */
class LogoMark : Region() {
    /** The paths in viewBox units; scaled with scaleX/scaleY around its centre ... */
    private val group = Group()
    /** ... and wrapped, so the wrapper's layout bounds include the scale and can be placed at the insets. */
    private val holder = Group(group)

    /** Rendered width in px; height follows the 106:34 aspect ratio. */
    val logoWidthProperty: DoubleProperty = SimpleDoubleProperty(this, "logoWidth", DEFAULT_WIDTH)
    var logoWidth: Double
        get() = logoWidthProperty.get()
        set(v) = logoWidthProperty.set(v)

    init {
        styleClass += "logo-mark"
        group.children += paths().map { (d, evenOdd) ->
            SVGPath().apply {
                content = d
                fillRule = if (evenOdd) FillRule.EVEN_ODD else FillRule.NON_ZERO
                styleClass += "logo-path"
            }
        }
        holder.isManaged = false
        children += holder
        logoWidthProperty.addListener { _, _, _ -> requestParentLayout(); requestLayout() }
    }

    val logoHeight: Double get() = logoWidth * VIEW_HEIGHT / VIEW_WIDTH

    override fun computePrefWidth(height: Double) = snappedLeftInset() + logoWidth + snappedRightInset()
    override fun computePrefHeight(width: Double) = snappedTopInset() + logoHeight + snappedBottomInset()
    override fun computeMinWidth(height: Double) = computePrefWidth(height)
    override fun computeMinHeight(width: Double) = computePrefHeight(width)
    override fun computeMaxWidth(height: Double) = computePrefWidth(height)
    override fun computeMaxHeight(width: Double) = computePrefHeight(width)

    /** Uniform scale factor (viewBox px -> screen px). */
    val scale: Double get() = logoWidth / VIEW_WIDTH

    override fun layoutChildren() {
        val k = scale
        group.scaleX = k
        group.scaleY = k
        // place the scaled paths so that viewBox (0,0) lands on the top-left inset
        val inner = group.layoutBounds
        val outer = holder.layoutBounds
        holder.layoutX = snappedLeftInset() + k * inner.minX - outer.minX
        holder.layoutY = snappedTopInset() + k * inner.minY - outer.minY
    }

    /** The SVGPath nodes (tests). */
    val pathNodes: List<SVGPath> get() = group.children.filterIsInstance<SVGPath>()

    /** Path count in the logo (tests). */
    val pathCount: Int get() = group.children.size

    companion object {
        const val VIEW_WIDTH = 106.0
        const val VIEW_HEIGHT = 34.0
        const val DEFAULT_WIDTH = 170.0
        const val RESOURCE = Theme.IMAGE_ROOT + "exbo-logo.svg"

        private val pathTag = Regex("""<path\b([^>]*)/?>""")
        private val dAttr = Regex("""\sd="([^"]+)"""")
        private val evenOddAttr = Regex("""fill-rule="evenodd"""")

        /** (path data, even-odd) of every <path> in the SVG resource, parsed once. */
        val paths: List<Pair<String, Boolean>> by lazy {
            val svg = requireNotNull(LogoMark::class.java.getResource(RESOURCE)) { "missing $RESOURCE" }.readText()
            pathTag.findAll(svg).map { m ->
                val attrs = m.groupValues[1]
                requireNotNull(dAttr.find(attrs)).groupValues[1] to evenOddAttr.containsMatchIn(attrs)
            }.toList()
        }

        private fun paths() = paths
    }
}
