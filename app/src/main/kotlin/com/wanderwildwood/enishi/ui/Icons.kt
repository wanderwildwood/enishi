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
 * units wide with round ends on a 28-unit grid, so a glyph at the bar's 28dp has a 2dp line and
 * the big tiles under a name a little more. Nothing is filled but a dot and the starred star.
 * Everything else on the screen is a word.
 *
 * Each is drawn here from measurements of the phone's own — the arrow's reach, where the
 * handset's ears sit, how far apart the four squares are — not copied from it.
 */
object Icons {

    private const val GRID = 28f

    /**
     * The glyph in a Call or Message tile is drawn at 49dp, with the line still 2.5dp as the
     * phone's own is; More's four squares at 56dp, their line 2.2dp. Thinner here, so the
     * larger drawing comes out right.
     */
    private const val TILE = 1.45f
    private const val MORE = 1.1f

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

    /** Three sliders, as the phone's own Phone app marks its settings. */
    val Settings: ImageVector = lined(
        "Settings",
        Stroke("M3,7H14.5M21.5,7H25M3,14H5.5M12.5,14H25M3,21H16.5M23.5,21H25"),
        Stroke("M18,7m-3.5,0a3.5,3.5 0 1 1 7,0a3.5,3.5 0 1 1 -7,0M9,14m-3.5,0a3.5,3.5 0 1 1 7,0a3.5,3.5 0 1 1 -7,0M20,21m-3.5,0a3.5,3.5 0 1 1 7,0a3.5,3.5 0 1 1 -7,0"),
    )

    val Edit: ImageVector = lined("Edit", "M1.5,26.5L2.9,20L20.6,2.3A2.7,2.7 0 0 1 24.4,2.3L25.7,3.6A2.7,2.7 0 0 1 25.7,7.4L8,25.1ZM18.3,4.6L23.4,9.7")

    val Add: ImageVector = lined("Add", "M14,4V24M4,14H24")

    /** Filled for a favourite, outlined for not. */
    val Star: ImageVector = lined("Star", starPath, filled = starPath)

    val StarBorder: ImageVector = lined("StarBorder", starPath)

    val ChevronDown: ImageVector = lined("ChevronDown", "M7,11L14,18L21,11")

    /**
     * The handset, its two ears joined by the curve of the grip: the outline of Material
     * Symbols' `call` (Apache-2.0), brought onto this grid and drawn as a line instead of filled.
     */
    val Call: ImageVector = lined(
        "Call",
        Stroke(
            "M23.3,24.5Q19.7,24.5 16.1,22.9T9.6,18.4Q6.7,15.5 5.1,11.9T3.5,4.7Q3.5,4.2 3.85,3.85T4.7,3.5H9.45" +
            "Q9.86,3.5 10.2,3.78T10.56,4.43L11.3,8.5Q11.36,8.97 11.27,9.29T10.97,9.86L8.1,12.7" +
            "Q8.68,13.8 9.49,14.8T11.3,16.7Q12.2,17.6 13.2,18.4T15.3,19.8L18,17.1Q18.3,16.8 18.7,16.7T19.5,16.6" +
                "L23.6,17.4Q24,17.5 24.3,17.8T24.5,18.6V23.3Q24.5,23.8 24.15,24.15T23.3,24.5Z",
            TILE,
        ),
    )

    /** The round speech bubble with three dots — a message. */
    val Sms: ImageVector = lined(
        "Sms",
        Stroke("M6,18.5A10.1,10.1 0 1 1 10.5,22.3L3.5,25Z", TILE),
        filled = "M9.5,13m-1.3,0a1.3,1.3 0 1 1 2.6,0a1.3,1.3 0 1 1 -2.6,0M14.5,13m-1.3,0a1.3,1.3 0 1 1 2.6,0a1.3,1.3 0 1 1 -2.6,0M19.5,13m-1.3,0a1.3,1.3 0 1 1 2.6,0a1.3,1.3 0 1 1 -2.6,0",
    )

    /** The envelope, for a person with only an email address. */
    val Mail: ImageVector = lined("Mail", Stroke("M5,6H23A2,2 0 0 1 25,8V20A2,2 0 0 1 23,22H5A2,2 0 0 1 3,20V8A2,2 0 0 1 5,6ZM3,8.5L14,16L25,8.5", TILE))

    /** Four rounded squares — More. */
    val GridView: ImageVector = lined(
        "GridView",
        Stroke(
            "M3.75,1.75H9.75A2,2 0 0 1 11.75,3.75V9.75A2,2 0 0 1 9.75,11.75H3.75A2,2 0 0 1 1.75,9.75V3.75A2,2 0 0 1 3.75,1.75Z" +
                "M18.25,1.75H24.25A2,2 0 0 1 26.25,3.75V9.75A2,2 0 0 1 24.25,11.75H18.25A2,2 0 0 1 16.25,9.75V3.75A2,2 0 0 1 18.25,1.75Z" +
                "M3.75,16.25H9.75A2,2 0 0 1 11.75,18.25V24.25A2,2 0 0 1 9.75,26.25H3.75A2,2 0 0 1 1.75,24.25V18.25A2,2 0 0 1 3.75,16.25Z" +
                "M18.25,16.25H24.25A2,2 0 0 1 26.25,18.25V24.25A2,2 0 0 1 24.25,26.25H18.25A2,2 0 0 1 16.25,24.25V18.25A2,2 0 0 1 18.25,16.25Z",
            MORE,
        ),
    )

    // The three ways a call went, as the phone's own call log marks them: an arrow that came
    // in, one that went out, and the bent one that was missed.

    val CallIn: ImageVector = lined("CallIn", "M23,6L7,22M7,10V22H19")

    val CallOut: ImageVector = lined("CallOut", "M5,23L21,7M9,7H21V19")

    val CallMissed: ImageVector = lined("CallMissed", "M3,9L14,20L25,9M3,19V9H13")
}
