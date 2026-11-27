package com.sulthonuladib.keroid.term

/**
 * Kero's ten interface washes: pale, four-stop gradients that tint the whole
 * surface. Each wash also has a dark slot derived from the same hue, so the
 * light/dark pair stays recognisably one theme. The gradient is the only
 * gradient the interface is allowed to show.
 *
 * Stop values are taken from the Doop "Foundations · Color & themes" frame.
 */
data class KeroWash(
    val name: String,
    val hue: Int?,
    val lightStops: IntArray,
    val darkStops: IntArray,
) {
    init {
        require(lightStops.size == 4 && darkStops.size == 4) { "$name: four stops per slot" }
    }

    /** A flat window colour for the slot; the gradient only appears in swatches. */
    fun window(dark: Boolean): Int = if (dark) darkStops[1] else lightStops[1]

    /** The 22-colour terminal theme for this wash: Catppuccin ANSI on wash paper. */
    fun theme(dark: Boolean): TerminalTheme {
        val base = if (dark) TerminalThemes.byName("Catppuccin Mocha") else TerminalThemes.byName("Catppuccin Latte")
        val palette = base?.palette ?: keroidTheme(dark).palette
        val bg = window(dark)
        val fg = if (dark) 0xFFCDD6F4.toInt() else 0xFF4C4F69.toInt()
        val selection = blend(bg, palette[4], if (dark) 0.34f else 0.20f)
        return TerminalTheme(
            name,
            palette + intArrayOf(bg, fg, fg, bg, selection, fg),
        )
    }

    /** Display gradient for the picker, tinted to the slot. */
    fun gradient(dark: Boolean): IntArray = if (dark) darkStops else lightStops

    companion object {
        fun blend(a: Int, b: Int, fraction: Float): Int {
            val f = fraction.coerceIn(0f, 1f)
            fun channel(shift: Int): Int {
                val ca = (a ushr shift) and 0xFF
                val cb = (b ushr shift) and 0xFF
                return (ca * (1 - f) + cb * f).toInt().coerceIn(0, 255)
            }
            return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }

        private fun darkStops(vararg light: Long): IntArray {
            val base = 0xFF171727.toInt()
            return IntArray(light.size) { i ->
                blend(base, light[i].toInt(), 0.16f + 0.04f * i)
            }
        }

        private fun wash(name: String, hue: Int?, vararg light: Long) = KeroWash(
            name = name,
            hue = hue,
            lightStops = IntArray(light.size) { light[it].toInt() },
            darkStops = darkStops(*light),
        )

        val all: List<KeroWash> = listOf(
            wash("petal", 350, 0xFFEBE0F3, 0xFFF3E7F1, 0xFFF7EEE4, 0xFFF6EFCF),
            wash("mist", 255, 0xFFE3E9F4, 0xFFEBEFF6, 0xFFF1F2F3, 0xFFF2F1EC),
            wash("meadow", 140, 0xFFE2EEE5, 0xFFEBF2E7, 0xFFF3F2E2, 0xFFF5F0D6),
            wash("dusk", 315, 0xFFE4DBF3, 0xFFEFDEEE, 0xFFF6E3DC, 0xFFF7E6CF),
            wash("tide", 205, 0xFFDBEBEF, 0xFFE3EFF1, 0xFFECF3F0, 0xFFF2F2E8),
            wash("apricot", 50, 0xFFF5E1D6, 0xFFF8E7DC, 0xFFF8EEDF, 0xFFF6F0DA),
            wash("aurora", 170, 0xFFDBEFE8, 0xFFE1EBF2, 0xFFE8E4F4, 0xFFF0E6F1),
            wash("dune", 80, 0xFFEDE4D8, 0xFFF1E9DE, 0xFFF4EEE4, 0xFFF3EFE8),
            wash("paper", 260, 0xFFF6F6F7, 0xFFF7F7F8, 0xFFF8F8F8, 0xFFF2F2F3),
            wash("plain", null, 0xFFECEEF4, 0xFFECEEF4, 0xFFECEEF4, 0xFFECEEF4),
        )

        fun byName(name: String): KeroWash? = all.firstOrNull { it.name == name }
    }
}
