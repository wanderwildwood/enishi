package com.wanderwildwood.enishi.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The icons this app draws, all Material Symbols (Apache-2.0) in the house cut — outlined, the
 * default weight — kept as the path data they ship as. A glyph any other app of this shop also
 * draws is that app's path byte for byte. Everything else on the screen is a word.
 *
 * Call, Message and Email are the standard glyphs for each, not the apps' launcher marks: a mark
 * is drawn by hand and belongs on the launcher; a button is interface, and draws from this set.
 */
object Icons {

    /**
     * Material Symbols are authored in a 960 grid whose origin sits at the bottom left,
     * so the path data runs from -960 to 0 vertically. Shifting the whole thing down by
     * 960 puts it in the top-left grid Compose uses.
     */
    private fun symbol(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        )
            .addGroup(name = name, translationY = 960f)
            .addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.Black),
            )
            .clearGroup()
            .build()

    val Back: ImageVector = symbol("Back", "m313-440 224 224-57 56-320-320 320-320 57 56-224 224h487v80H313Z")

    val Close: ImageVector = symbol("Close", "m256-200-56-56 224-224-224-224 56-56 224 224 224-224 56 56-224 224 224 224-56 56-224-224-224 224Z")

    val Info: ImageVector = symbol("Info", "M440-280h80v-240h-80v240Zm40-320q17 0 28.5-11.5T520-640q0-17-11.5-28.5T480-680q-17 0-28.5 11.5T440-640q0 17 11.5 28.5T480-600Zm0 520q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm0-80q134 0 227-93t93-227q0-134-93-227t-227-93q-134 0-227 93t-93 227q0 134 93 227t227 93Zm0-320Z")

    val Search: ImageVector = symbol("Search", "M784-120 532-372q-30 24-69 38t-83 14q-109 0-184.5-75.5T120-580q0-109 75.5-184.5T380-840q109 0 184.5 75.5T640-580q0 44-14 83t-38 69l252 252-56 56ZM380-400q75 0 127.5-52.5T560-580q0-75-52.5-127.5T380-760q-75 0-127.5 52.5T200-580q0 75 52.5 127.5T380-400Z")

    val Settings: ImageVector = symbol("Settings", "m370-80-16-128q-13-5-24.5-12T307-235l-119 50L78-375l103-78q-1-7-1-13.5v-27q0-6.5 1-13.5L78-585l110-190 119 50q11-8 23-15t24-12l16-128h220l16 128q13 5 24.5 12t22.5 15l119-50 110 190-103 78q1 7 1 13.5v27q0 6.5-2 13.5l103 78-110 190-118-50q-11 8-23 15t-24 12L590-80H370Zm70-80h79l14-106q31-8 57.5-23.5T639-327l99 41 39-68-86-65q5-14 7-29.5t2-31.5q0-16-2-31.5t-7-29.5l86-65-39-68-99 42q-22-23-48.5-38.5T533-694l-13-106h-79l-14 106q-31 8-57.5 23.5T321-633l-99-41-39 68 86 64q-5 15-7 30t-2 32q0 16 2 31t7 30l-86 65 39 68 99-42q22 23 48.5 38.5T427-266l13 106Zm42-180q58 0 99-41t41-99q0-58-41-99t-99-41q-59 0-99.5 41T342-480q0 58 40.5 99t99.5 41Zm-2-140Z")

    val Edit: ImageVector = symbol("Edit", "M200-200h57l391-391-57-57-391 391v57Zm-80 80v-170l528-527q12-11 26.5-17t30.5-6q16 0 31 6t26 18l55 56q12 11 17.5 26t5.5 30q0 16-5.5 30.5T817-647L290-120H120Zm640-584-56-56 56 56Zm-141 85-28-29 57 57-29-28Z")

    val Add: ImageVector = symbol("Add", "M440-440H200v-80h240v-240h80v240h240v80H520v240h-80v-240Z")

    /** Filled for a favourite, outlined for not: a hollow star alone reads as a smudge. */
    val Star: ImageVector = symbol("Star", "m233-120 65-281L80-590l288-25 112-265 112 265 288 25-218 189 65 281-247-149-247 149Z")

    val StarBorder: ImageVector = symbol("StarBorder", "m354-287 126-76 126 77-33-144 111-96-146-13-58-136-58 135-146 13 111 97-33 143ZM233-120l65-281L80-590l288-25 112-265 112 265 288 25-218 189 65 281-247-149-247 149Zm247-350Z")

    val ChevronDown: ImageVector = symbol("ChevronDown", "M480-344 240-584l56-56 184 184 184-184 56 56-240 240Z")

    val Call: ImageVector = symbol("Call", "M798-120q-125 0-247-54.5T329-329Q229-429 174.5-551T120-798q0-18 12-30t30-12h162q14 0 25 9.5t13 22.5l26 140q2 16-1 27t-11 19l-97 98q20 37 47.5 71.5T387-386q31 31 65 57.5t72 48.5l94-94q9-9 23.5-13.5T670-390l138 28q14 4 23 14.5t9 23.5v162q0 18-12 30t-30 12ZM241-600l66-66-17-94h-89q5 41 14 81t26 79Zm358 358q39 17 79.5 27t81.5 13v-88l-94-19-67 67ZM241-600Zm358 358Z")

    /** Message: the speech bubble with three dots — Messaging's own motif. */
    val Sms: ImageVector = symbol("Sms", "M348.5-531.5Q360-543 360-560t-11.5-28.5Q337-600 320-600t-28.5 11.5Q280-577 280-560t11.5 28.5Q303-520 320-520t28.5-11.5Zm160 0Q520-543 520-560t-11.5-28.5Q497-600 480-600t-28.5 11.5Q440-577 440-560t11.5 28.5Q463-520 480-520t28.5-11.5Zm160 0Q680-543 680-560t-11.5-28.5Q657-600 640-600t-28.5 11.5Q600-577 600-560t11.5 28.5Q623-520 640-520t28.5-11.5ZM80-80v-720q0-33 23.5-56.5T160-880h640q33 0 56.5 23.5T880-800v480q0 33-23.5 56.5T800-240H240L80-80Zm126-240h594v-480H160v525l46-45Zm-46 0v-480 480Z")

    /** Email, for a person with only an address: the envelope — Email's own motif. */
    val Mail: ImageVector = symbol("Mail", "M160-160q-33 0-56.5-23.5T80-240v-480q0-33 23.5-56.5T160-800h640q33 0 56.5 23.5T880-720v480q0 33-23.5 56.5T800-160H160Zm320-280L160-640v400h640v-400L480-440Zm0-80 320-200H160l320 200ZM160-640v-80 480-400Z")

    val GridView: ImageVector = symbol("GridView", "M120-520v-320h320v320H120Zm0 400v-320h320v320H120Zm400-400v-320h320v320H520Zm0 400v-320h320v320H520ZM200-600h160v-160H200v160Zm400 0h160v-160H600v160Zm0 400h160v-160H600v160Zm-400 0h160v-160H200v160Zm400-400Zm0 240Zm-240 0Zm0-240Z")
}
