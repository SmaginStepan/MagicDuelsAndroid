package com.an0obis.magicduel.ui

internal val WHITE = 0xFFFFFFFF.toInt()
internal val BLACK = 0xFF000000.toInt()
internal val RED = 0xFFFF3030.toInt()
internal val GOLD = 0xFFF0C75E.toInt()
internal val SKIN = 0xFFEBC9A4.toInt()

/** Blends two ARGB colours: t = 0 gives [a], t = 1 gives [b]. */
internal fun mix(a: Int, b: Int, t: Float): Int {
    val k = t.coerceIn(0f, 1f)
    fun channel(shift: Int): Int {
        val x = (a shr shift) and 0xFF
        val y = (b shr shift) and 0xFF
        return (x + (y - x) * k).toInt() and 0xFF
    }
    return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

internal fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

internal fun smooth01(t: Float): Float {
    val k = t.coerceIn(0f, 1f)
    return k * k * (3f - 2f * k)
}

internal fun easeOut(t: Float): Float {
    val k = 1f - t.coerceIn(0f, 1f)
    return 1f - k * k
}

internal fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val k = t.coerceIn(0f, 1f) - 1f
    return 1f + c3 * k * k * k + c1 * k * k
}
