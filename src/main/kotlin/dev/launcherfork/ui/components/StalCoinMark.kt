package dev.launcherfork.ui.components

import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.scene.Group
import javafx.scene.layout.Region
import javafx.scene.shape.FillRule
import javafx.scene.shape.SVGPath

/**
 * STALZONE coin (stalzone.com `coin-*.svg`, 19×19 viewBox): ring + «SC» glyph as [SVGPath]s.
 * Colour from CSS (`.coin-path { -fx-fill: -foreground; }`). FXML: `<StalCoinMark coinSize="19"/>`.
 */
class StalCoinMark : Region() {
    private val group = Group()
    private val holder = Group(group)

    val coinSizeProperty: DoubleProperty = SimpleDoubleProperty(this, "coinSize", DEFAULT_SIZE)
    var coinSize: Double
        get() = coinSizeProperty.get()
        set(v) = coinSizeProperty.set(v)

    init {
        styleClass += "stal-coin"
        group.children += PATHS.map { (d, evenOdd) ->
            SVGPath().apply {
                content = d
                fillRule = if (evenOdd) FillRule.EVEN_ODD else FillRule.NON_ZERO
                styleClass += "coin-path"
            }
        }
        holder.isManaged = false
        children += holder
        coinSizeProperty.addListener { _, _, _ -> requestParentLayout(); requestLayout() }
    }

    override fun computePrefWidth(height: Double) = snappedLeftInset() + coinSize + snappedRightInset()
    override fun computePrefHeight(width: Double) = snappedTopInset() + coinSize + snappedBottomInset()
    override fun computeMinWidth(height: Double) = computePrefWidth(height)
    override fun computeMinHeight(width: Double) = computePrefHeight(width)
    override fun computeMaxWidth(height: Double) = computePrefWidth(height)
    override fun computeMaxHeight(width: Double) = computePrefHeight(width)

    override fun layoutChildren() {
        val k = coinSize / VIEW
        group.scaleX = k
        group.scaleY = k
        // place the scaled paths so that viewBox (0,0) lands on the top-left inset (same as LogoMark)
        val inner = group.layoutBounds
        val outer = holder.layoutBounds
        holder.layoutX = snappedLeftInset() + k * inner.minX - outer.minX
        holder.layoutY = snappedTopInset() + k * inner.minY - outer.minY
    }

    private companion object {
        const val VIEW = 19.0
        const val DEFAULT_SIZE = 19.0
        /** Paths from front_stalzone `coin-BNW6KWWX.svg` (fill #ECE7E1 → CSS token). */
        val PATHS = listOf(
            "M9.5 19C11.9453 19 14.1749 18.0761 15.8586 16.5583L14.4217 15.1214C13.1071 16.2732 11.3851 16.9714 9.5 16.9714 7.61182 16.9714 5.88723 16.2709 4.57189 15.1157L3.13504 16.5526C4.8195 18.0738 7.05158 19 9.5 19ZM19 9.5C19 11.9453 18.0761 14.1749 16.5583 15.8586L15.1214 14.4216C16.2732 13.1071 16.9714 11.3851 16.9714 9.5 16.9714 7.61181 16.2709 5.88722 15.1157 4.57188L16.5526 3.13503C18.0738 4.8195 19 7.05158 19 9.5ZM2.43592 15.8522 3.87295 14.4152C2.72449 13.1015 2.02865 11.382 2.02865 9.5 2.02865 7.61491 2.72678 5.89288 3.87861 4.57835L2.44167 3.14142C.923901 4.82514 0 7.05467 0 9.5 0 11.9422.921568 14.1692 2.43592 15.8522ZM3.14142 2.44168C4.82514.923902 7.05467 0 9.5 0 11.9422 0 14.1692.921566 15.8522 2.43592L14.4152 3.87294C13.1015 2.72449 11.382 2.02865 9.5 2.02865 7.61491 2.02865 5.89288 2.72678 4.57835 3.87861L3.14142 2.44168Z" to false,
            "M15.9818 9.5C15.9818 13.0798 13.0798 15.9818 9.5 15.9818C5.92022 15.9818 3.01823 13.0798 3.01823 9.5C3.01823 5.92022 5.92022 3.01823 9.5 3.01823C13.0798 3.01823 15.9818 5.92022 15.9818 9.5ZM12.2208 6.28385H9.84635V12.7161H12.2214V10.9518C11.9177 10.9518 11.6737 10.8053 11.4894 10.5122V11.9797H10.5783V7.02028H11.4888V8.12402C11.6735 7.83095 11.9175 7.68442 12.2208 7.68442V6.28385ZM6.77864 12.7156V10.9514C7.08227 10.9514 7.32619 10.8049 7.51041 10.5118V11.9792H8.42129V10.4668L6.77864 8.63095V6.28385H9.15364V8.04802C8.84963 8.04802 8.60551 8.19612 8.42129 8.49232V7.02021H7.51041V8.34837L9.15364 10.1848V12.7161L6.77864 12.7156Z" to true,
        )
    }
}
