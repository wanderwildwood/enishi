package com.wanderwildwood.enishi.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The icons this app draws, cut like the phone's own contacts app draws its: a line two
 * units wide with round ends on a 28-unit grid, so a glyph at the bar's 28dp has a 2dp line.
 * Nothing is filled but a dot, the starred star, and the cog, which is Phone's and Messaging's
 * so that all three open their settings behind the same mark. Everything else on the screen is
 * a word.
 *
 * Each is drawn here from measurements of the phone's own — the arrow's reach, where the
 * handset's ears sit, how far apart the four squares are — not copied from it.
 */
object Icons {

    private const val GRID = 28f

    private class Stroke(val d: String, val width: Float = 2f)

    /** One or more stroked paths, with an optional filled one (a dot, a filled star). */
    private fun lined(name: String, vararg strokes: Stroke, filled: String? = null): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 28.dp, defaultHeight = 28.dp, viewportWidth = GRID, viewportHeight = GRID)
            .apply {
                strokes.forEach { s ->
                    addPath(
                        pathData = PathParser().parsePathString(s.d).toNodes(),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = s.width,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
                if (filled != null) addPath(pathData = PathParser().parsePathString(filled).toNodes(), fill = SolidColor(Color.Black))
            }
            .build()

    private fun lined(name: String, d: String, filled: String? = null) = lined(name, Stroke(d), filled = filled)

    /** A filled glyph drawn on Material's 24-unit grid, centred in this one. */
    private fun filledIn24(name: String, d: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 28.dp, defaultHeight = 28.dp, viewportWidth = GRID, viewportHeight = GRID)
            .addGroup(translationX = 2f, translationY = 2f)
            .addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
            .clearGroup()
            .build()

    /** A five-pointed star about (14, 15), outer reach 13, inner 5.4, point up. */
    private val starPath: String = buildString {
        val cx = 14.0; val cy = 15.0
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) 13.0 else 5.4
            val a = Math.toRadians(-90.0 + i * 36.0)
            append(if (i == 0) "M" else "L")
            append("%.2f,%.2f".format(java.util.Locale.ROOT, cx + r * Math.cos(a), cy + r * Math.sin(a)))
        }
        append("Z")
    }

    val Back: ImageVector = lined("Back", "M26,14H2M2,14L13,3M2,14L13,25")

    val Close: ImageVector = lined("Close", "M5,5L23,23M23,5L5,23")

    val Info: ImageVector = lined(
        "Info",
        Stroke("M14,26.25A12.25,12.25 0 1 1 14,1.75A12.25,12.25 0 1 1 14,26.25Z"),
        Stroke("M11.2,20.6H16.8M14,20.6V14H12.1"),
        filled = "M14,9.4m-1.6,0a1.6,1.6 0 1 1 3.2,0a1.6,1.6 0 1 1 -3.2,0",
    )

    val Search: ImageVector = lined("Search", "M11.5,20.25A8.75,8.75 0 1 1 11.5,2.75A8.75,8.75 0 1 1 11.5,20.25ZM17.8,17.8L25,25")

    /**
     * The cog, as Messaging marks its settings: the two are a pair, and open the same door the
     * same way. Material's `settings` (Apache-2.0), the same outline Messaging draws, filled, and
     * set 24 units wide in the middle of this grid so it stands as large as Messaging's does.
     */
    val Settings: ImageVector = filledIn24(
        "Settings",
        "M19.14,12.94c0.04,-0.31 0.06,-0.63 0.06,-0.94c0,-0.31 -0.02,-0.63 -0.06,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61" +
            "l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81" +
            "c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29" +
            "L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58" +
            "C4.84,11.37 4.8,11.69 4.8,12s0.02,0.63 0.06,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32" +
            "c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41" +
            "h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22" +
            "l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94z" +
            "M12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z",
    )

    val Edit: ImageVector = lined("Edit", "M1.5,26.5L2.9,20L20.6,2.3A2.7,2.7 0 0 1 24.4,2.3L25.7,3.6A2.7,2.7 0 0 1 25.7,7.4L8,25.1ZM18.3,4.6L23.4,9.7")

    val Add: ImageVector = lined("Add", "M14,4V24M4,14H24")

    /** Filled for a favourite, outlined for not. */
    val Star: ImageVector = lined("Star", starPath, filled = starPath)

    val StarBorder: ImageVector = lined("StarBorder", starPath)

    val ChevronDown: ImageVector = lined("ChevronDown", "M7,11L14,18L21,11")

    /**
     * The round speech bubble with three dots: write to this number. It sits at the end of a
     * number's row on a person's page, where the row itself calls.
     */
    val Sms: ImageVector = lined(
        "Sms",
        Stroke("M6,18.5A10.1,10.1 0 1 1 10.5,22.3L3.5,25Z"),
        filled = "M9.5,13m-1.3,0a1.3,1.3 0 1 1 2.6,0a1.3,1.3 0 1 1 -2.6,0M14.5,13m-1.3,0a1.3,1.3 0 1 1 2.6,0a1.3,1.3 0 1 1 -2.6,0M19.5,13m-1.3,0a1.3,1.3 0 1 1 2.6,0a1.3,1.3 0 1 1 -2.6,0",
    )

    // The three ways a call went, as the phone's own call log marks them: an arrow that came
    // in, one that went out, and the bent one that was missed.

    val CallIn: ImageVector = lined("CallIn", "M23,6L7,22M7,10V22H19")

    val CallOut: ImageVector = lined("CallOut", "M5,23L21,7M9,7H21V19")

    val CallMissed: ImageVector = lined("CallMissed", "M3,9L14,20L25,9M3,19V9H13")
}
