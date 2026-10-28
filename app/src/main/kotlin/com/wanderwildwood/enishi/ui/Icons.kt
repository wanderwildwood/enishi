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
     * The cog: Material Symbols' outlined `settings` (Apache-2.0), as Recipes draws it, drawn
     * 22dp inside a 48dp touch target (see [BarButton]'s `size`). Its path runs on a 960 grid
     * from -960 to 0 vertically, so the group moves it down by 960.
     */
    val Settings: ImageVector = ImageVector.Builder(
        name = "Settings",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
    )
        .addGroup(name = "Settings", translationY = 960f)
        .addPath(
            pathData = PathParser().parsePathString(
                "m370-80-16-128q-13-5-24.5-12T307-235l-119 50L78-375l103-78q-1-7-1-13.5v-27q0-6.5 1-13.5L78-585l110-190 119 50q11-8 23-15t24-12l16-128h220l16 128q13 5 24.5 12t22.5 15l119-50 110 190-103 78q1 7 1 13.5v27q0 6.5-2 13.5l103 78-110 190-118-50q-11 8-23 15t-24 12L590-80H370Zm70-80h79l14-106q31-8 57.5-23.5T639-327l99 41 39-68-86-65q5-14 7-29.5t2-31.5q0-16-2-31.5t-7-29.5l86-65-39-68-99 42q-22-23-48.5-38.5T533-694l-13-106h-79l-14 106q-31 8-57.5 23.5T321-633l-99-41-39 68 86 64q-5 15-7 30t-2 32q0 16 2 31t7 30l-86 65 39 68 99-42q22 23 48.5 38.5T427-266l13 106Zm42-180q58 0 99-41t41-99q0-58-41-99t-99-41q-59 0-99.5 41T342-480q0 58 40.5 99t99.5 41Zm-2-140Z",
            ).toNodes(),
            fill = SolidColor(Color.Black),
        )
        .clearGroup()
        .build()

    val Edit: ImageVector = lined("Edit", "M1.5,26.5L2.9,20L20.6,2.3A2.7,2.7 0 0 1 24.4,2.3L25.7,3.6A2.7,2.7 0 0 1 25.7,7.4L8,25.1ZM18.3,4.6L23.4,9.7")

    val Add: ImageVector = lined("Add", "M14,4V24M4,14H24")

    /** Filled for a favourite, outlined for not. */
    val Star: ImageVector = lined("Star", starPath, filled = starPath)

    val StarBorder: ImageVector = lined("StarBorder", starPath)

    val ChevronDown: ImageVector = lined("ChevronDown", "M7,11L14,18L21,11")

    /**
     * The handset, Phone's own (the outline of Material Symbols' `call`, Apache-2.0, drawn as a
     * line): call this number. It sits before the speech bubble at the end of a number's row.
     */
    val Call: ImageVector = lined(
        "Call",
        "M23.3,24.5Q19.7,24.5 16.1,22.9T9.6,18.4Q6.7,15.5 5.1,11.9T3.5,4.7Q3.5,4.2 3.85,3.85T4.7,3.5H9.45" +
            "Q9.86,3.5 10.2,3.78T10.56,4.43L11.3,8.5Q11.36,8.97 11.27,9.29T10.97,9.86L8.1,12.7" +
            "Q8.68,13.8 9.49,14.8T11.3,16.7Q12.2,17.6 13.2,18.4T15.3,19.8L18,17.1Q18.3,16.8 18.7,16.7T19.5,16.6" +
            "L23.6,17.4Q24,17.5 24.3,17.8T24.5,18.6V23.3Q24.5,23.8 24.15,24.15T23.3,24.5Z",
    )

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
