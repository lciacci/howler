package com.houseofyeti.howler.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

/**
 * CRT amber-phosphor palette for the meter screen. Literal values from the
 * design comp (design/howler-ui-spec.md §1).
 */
object Phosphor {
    val screen = Color(0xFF080400)        // screen background
    val readout = Color(0xFFFFC23A)       // readout / active text
    val label = Color(0xFF9A6F22)         // MAX/MIN/Leq labels
    val labelBright = Color(0xFFC98A2E)
    val caption = Color(0xFF8A6320)       // calibration caption
    val toggleActiveFill = Color(0xFF3A2600)
    val toggleActiveBorder = Color(0xFFFFB02E)
    val toggleInactiveBorder = Color(0xFF6E4A0F)
    val toggleInactiveText = Color(0xFFA9781F)
    val glow = Color(0xFFFF961C)          // reactive edge glow
    val overBg = Color(0xFF3A0E06)        // OVER / danger
    val overBorder = Color(0xFFFF4326)
    val overText = Color(0xFFFF5B3A)
    val scanline = Color(0xFF000000)      // overlay, ~0.42 alpha, 3px pitch
}
