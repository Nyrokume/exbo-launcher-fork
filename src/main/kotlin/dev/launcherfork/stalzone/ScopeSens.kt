package dev.launcherfork.stalzone

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

/**
 * Sensitivity table from Regedit Nyrokume Exbo (`src/sensitivity.ts`).
 * Formula from stalcraft.wiki: `8 * (u/200/mag*0.6+0.2)^3 / 0.044 * mag`.
 * Written into `options.json` as `mouse_sensitivity` and `sensitivity_zoom_*`.
 */
object ScopeSens {
    val MAGNIFICATIONS: List<Double> = listOf(
        1.0, 1.25, 1.5, 1.6, 1.8, 2.0, 2.4, 2.8, 3.0, 3.4, 3.5, 4.0, 6.0, 8.0,
    )

    fun wiki(u: Double, mag: Double): Double {
        val k = 8.0 / 0.044
        val inner = (u / 200.0 / mag) * 0.6 + 0.2
        return k * inner.pow(3.0) * mag
    }

    /** Newton solve so wiki(u, 1) equals the 1x base the player typed. */
    fun solveInternalU(base: Double): Double {
        var u = 30.0
        repeat(20) {
            val f = wiki(u, 1.0) - base
            val h = 1e-4
            val deriv = (wiki(u + h, 1.0) - base - f) / h
            if (abs(deriv) < 1e-6) return u
            val step = f / deriv
            u -= step
            if (abs(step) < 1e-6) return u
        }
        return u
    }

    fun round2(x: Double): Double = round(x * 100.0) / 100.0

    fun snapshot(base: Double): Map<Double, Double> {
        val u = solveInternalU(base)
        return MAGNIFICATIONS.associateWith { round2(wiki(u, it)) }
    }

    /** Same key shape as the Regedit page: `1.00` -> `1.0`, `1.25` stays `1.25`. */
    fun magKey(mag: Double): String {
        val fixed = String.format(java.util.Locale.US, "%.2f", mag)
        val trimmed = fixed.replace(Regex("0+$"), "")
        return if (trimmed.endsWith(".")) trimmed + "0" else trimmed
    }
}
