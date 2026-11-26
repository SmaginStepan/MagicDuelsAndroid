package com.an0obis.magicduel.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.an0obis.magicduel.game.Element
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Element-themed effects drawn in code: explosions where globes burst, the magic that flies to the
 * target (with its trail), and the impact where it hits.
 *
 * Fire = flames and embers, water = splash, droplets and ripples, earth = rock chunks and dust,
 * air (and lightning) = electric bolts, shadow = smoke. Sizes scale with [u], the field cell size.
 */
class Effects {
    private enum class Kind { FLAME, EMBER, DROP, RIPPLE, ROCK, DUST, FLASH, BOLT, SMOKE, WISP }

    private class P(
        val kind: Kind,
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val life: Float,
        val size: Float,
        val color: Int,
        val gravity: Float = 0f,
        val drag: Float = 0f,
        var rot: Float = 0f,
        val vrot: Float = 0f,
        val x2: Float = 0f,
        val y2: Float = 0f,
        val shape: FloatArray? = null,
    ) {
        var t = 0f
    }

    companion object {
        /** Hits are drawn this much bigger than globe bursts. */
        private const val IMPACT_SCALE = 1.25f

        /** Positions passed to [drawTravel]: where the magic is now and where it was before, newest first. */
        const val TAIL_POINTS = 10

        /** Seconds of flight between two of those positions. */
        const val TAIL_STEP = 0.012f
    }

    /** Field cell size in pixels; set by the game view on layout. */
    var u = 100f

    private val ps = ArrayList<P>()
    private val rnd = Random(11)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val oval = RectF()
    private val tailAlong = FloatArray(TAIL_POINTS)
    private val travelShape = FloatArray(6)
    private var trailDebt = 0f

    fun clear() = ps.clear()

    private fun r(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)

    private fun dir(): Float = r(0f, 2f * PI.toFloat())

    // ================================================================ colours

    private object C {
        val FLAME_HOT = 0xFFFFF0B0.toInt()
        val FLAME_MID = 0xFFFF9A2E.toInt()
        val FLAME_COOL = 0xFFC8321E.toInt()
        val EMBER = 0xFFFFC94A.toInt()
        val WATER = 0xFF6EC3FF.toInt()
        val FOAM = 0xFFDDF3FF.toInt()
        val ROCKS = intArrayOf(0xFF8B6A45.toInt(), 0xFF6E5236.toInt(), 0xFFA07E55.toInt(), 0xFF7A6148.toInt())
        val DUST = 0xFF9A8670.toInt()
        val MOTE = 0xFF8DE36B.toInt()
        val AIR_GLOW = 0xFF8FE3FF.toInt()
        val LIGHTNING_GLOW = 0xFFFFE04A.toInt()
        val SHADOW = 0xFF4B2A7A.toInt()
        val SHADOW_LIGHT = 0xFF8452C8.toInt()
        val SHADOW_SPARK = 0xFFB98CFF.toInt()
    }

    private fun boltGlow(e: Element) = if (e == Element.LIGHTNING) C.LIGHTNING_GLOW else C.AIR_GLOW

    /** True for elements whose magic travels as an instant lightning bolt instead of a flying missile. */
    fun travelsAsBolt(e: Element) = e == Element.AIR || e == Element.LIGHTNING

    // ================================================================ spawning

    /** A globe of [e] explodes at ([x], [y]). */
    fun explode(e: Element, x: Float, y: Float) = burst(e, x, y, 1f, explosion = true)

    /**
     * Magic of [e] hits at ([x], [y]); [strength] 1 = full hit, smaller for each extra spark. Hits are
     * a bit bigger than globe bursts, so they read against the much larger mages.
     */
    fun impact(e: Element, x: Float, y: Float, strength: Float) = burst(e, x, y, strength * IMPACT_SCALE, explosion = false)

    private fun burst(e: Element, x: Float, y: Float, s: Float, explosion: Boolean) {
        val n = { base: Int -> max(1, (base * s).roundToInt()) }
        when (e) {
            Element.FIRE -> {
                flash(x, y, 0.6f * u * s, C.FLAME_MID, 0.22f)
                repeat(n(12)) {
                    val a = dir(); val sp = r(0.6f, 1.8f) * u * s
                    ps += P(Kind.FLAME, x, y, cos(a) * sp, sin(a) * sp - 0.9f * u, r(0.45f, 0.75f), r(0.14f, 0.24f) * u * s,
                        C.FLAME_MID, gravity = -2f * u, drag = 2.5f)
                }
                repeat(n(11)) {
                    val a = dir(); val sp = r(1.2f, 2.8f) * u * s
                    ps += P(Kind.EMBER, x, y, cos(a) * sp, sin(a) * sp - 0.5f * u, r(0.45f, 0.85f), r(0.025f, 0.04f) * u,
                        C.EMBER, gravity = -0.9f * u, drag = 1.5f)
                }
            }
            Element.WATER -> {
                flash(x, y, 0.4f * u * s, C.FOAM, 0.15f)
                repeat(n(13)) {
                    val a = r(-PI.toFloat() * 0.92f, -PI.toFloat() * 0.08f); val sp = r(1.6f, 3.4f) * u * s
                    ps += P(Kind.DROP, x, y, cos(a) * sp, sin(a) * sp, r(0.4f, 0.75f), r(0.035f, 0.065f) * u,
                        if (rnd.nextInt(4) == 0) C.FOAM else C.WATER, gravity = 7f * u)
                }
                ps += P(Kind.RIPPLE, x, y + 0.1f * u, 0f, 0f, 0.55f, 0.95f * u * s, C.FOAM)
                ps += P(Kind.RIPPLE, x, y + 0.1f * u, 0f, 0f, 0.8f, 0.7f * u * s, C.WATER)
            }
            Element.EARTH -> {
                flash(x, y, 0.45f * u * s, C.MOTE, 0.15f)
                repeat(n(3)) {
                    ps += P(Kind.DUST, x + r(-0.2f, 0.2f) * u, y + r(-0.1f, 0.2f) * u, r(-0.4f, 0.4f) * u, r(-0.5f, -0.1f) * u,
                        r(0.55f, 0.85f), r(0.22f, 0.34f) * u * s, C.DUST)
                }
                repeat(n(if (explosion) 7 else 6)) {
                    val a = r(-PI.toFloat() * 0.95f, PI.toFloat() * 0.15f); val sp = r(1.3f, 3f) * u * s
                    ps += P(Kind.ROCK, x, y, cos(a) * sp, sin(a) * sp - 0.6f * u, r(0.6f, 0.9f), r(0.1f, 0.17f) * u * s,
                        C.ROCKS[rnd.nextInt(C.ROCKS.size)], gravity = 8f * u, rot = dir(), vrot = r(-9f, 9f), shape = rockShape())
                }
                repeat(n(3)) {
                    val a = dir(); val sp = r(0.4f, 1.1f) * u
                    ps += P(Kind.EMBER, x, y, cos(a) * sp, sin(a) * sp - 0.4f * u, r(0.5f, 0.9f), 0.03f * u, C.MOTE, gravity = -0.6f * u)
                }
            }
            Element.AIR, Element.LIGHTNING -> {
                val glow = boltGlow(e)
                flash(x, y, 0.65f * u * s, glow, 0.16f)
                repeat(n(4)) {
                    val a = dir(); val len = r(0.5f, 1.05f) * u * s
                    ps += P(Kind.BOLT, x, y, 0f, 0f, r(0.12f, 0.26f), 0.7f, glow, x2 = x + cos(a) * len, y2 = y + sin(a) * len)
                }
                repeat(n(7)) {
                    val a = dir(); val sp = r(2f, 4f) * u * s
                    ps += P(Kind.EMBER, x, y, cos(a) * sp, sin(a) * sp, r(0.15f, 0.3f), 0.025f * u, glow, drag = 4f)
                }
            }
            Element.SHADOW -> {
                flash(x, y, 0.55f * u * s, C.SHADOW_SPARK, 0.18f)
                // Dark and lighter violet puffs, so the smoke shows on the dark sky and on the mages.
                repeat(n(7)) {
                    ps += P(Kind.SMOKE, x + r(-0.2f, 0.2f) * u, y + r(-0.2f, 0.2f) * u, r(-0.5f, 0.5f) * u, r(-0.7f, -0.2f) * u,
                        r(0.6f, 0.95f), r(0.2f, 0.32f) * u * s, if (it % 2 == 0) C.SHADOW_LIGHT else C.SHADOW)
                }
                repeat(n(8)) {
                    val a = dir(); val sp = r(1f, 2.2f) * u * s
                    ps += P(Kind.EMBER, x, y, cos(a) * sp, sin(a) * sp, r(0.4f, 0.7f), 0.03f * u, C.SHADOW_SPARK, drag = 2f)
                }
            }
        }
    }

    /**
     * Short lightning arcs flicker around an air or lightning comet whose head is at ([x], [y]), flying
     * in direction [dir] (1 = right, -1 = left): about [rate] arcs a second, along the first cell of
     * the tail, snapping back and outwards. Other elements get none.
     */
    fun crackle(e: Element, x: Float, y: Float, dir: Float, dt: Float, rate: Float) {
        if (!travelsAsBolt(e) || rnd.nextFloat() >= dt * rate) return
        val sx = x - dir * r(0.05f, 1.1f) * u
        val sy = y + r(-0.15f, 0.15f) * u
        val a = (if (dir > 0f) PI.toFloat() else 0f) + r(-1.2f, 1.2f)
        val len = r(0.25f, 0.6f) * u
        ps += P(Kind.BOLT, sx, sy, 0f, 0f, r(0.08f, 0.16f), 0.55f, boltGlow(e), x2 = sx + cos(a) * len, y2 = sy + sin(a) * len)
    }

    /**
     * Streaks of wind peel off an air comet's head at ([x], [y]) (flying in direction [dir]) and stream
     * back along its tail, about [rate] a second, so the comet visibly rushes even when small.
     */
    fun windStreaks(e: Element, x: Float, y: Float, dir: Float, dt: Float, rate: Float) {
        if (e != Element.AIR || rnd.nextFloat() >= dt * rate) return
        ps += P(
            Kind.WISP, x - dir * r(0f, 0.35f) * u, y + r(-0.28f, 0.28f) * u, -dir * r(0.6f, 1.4f) * u, r(-0.12f, 0.12f) * u,
            r(0.25f, 0.45f), r(0.025f, 0.04f) * u, C.AIR_GLOW,
            x2 = -dir * r(0.35f, 0.75f) * u, y2 = r(-0.14f, 0.14f) * u,
        )
    }

    /** The full lightning bolt from ([x0], [y0]) to ([x1], [y1]) lingers and flickers out after it struck. */
    fun zap(e: Element, x0: Float, y0: Float, x1: Float, y1: Float) {
        ps += P(Kind.BOLT, x0, y0, 0f, 0f, 0.22f, 1f, boltGlow(e), x2 = x1, y2 = y1)
    }

    private fun flash(x: Float, y: Float, size: Float, color: Int, life: Float) {
        ps += P(Kind.FLASH, x, y, 0f, 0f, life, size, color)
    }

    private fun rockShape(): FloatArray {
        val n = 5 + rnd.nextInt(3)
        return FloatArray(n) { r(0.65f, 1f) }
    }

    /** Leaves a trail behind magic of [e] flying at ([x], [y]) this frame. */
    fun trail(e: Element, x: Float, y: Float, dt: Float) {
        trailDebt += dt * 45f
        val count = trailDebt.toInt()
        trailDebt -= count
        repeat(count) {
            when (e) {
                Element.FIRE -> ps += if (rnd.nextBoolean())
                    P(Kind.EMBER, x, y, r(-0.3f, 0.3f) * u, r(-0.6f, 0f) * u, r(0.25f, 0.45f), 0.025f * u, C.EMBER, gravity = -0.5f * u)
                else
                    P(Kind.FLAME, x, y, r(-0.2f, 0.2f) * u, r(-0.4f, 0f) * u, r(0.15f, 0.25f), r(0.06f, 0.09f) * u, C.FLAME_MID, drag = 2f)
                Element.WATER -> ps += P(Kind.DROP, x, y, r(-0.3f, 0.3f) * u, r(-0.2f, 0.3f) * u, r(0.25f, 0.4f), r(0.02f, 0.035f) * u,
                    C.WATER, gravity = 4f * u)
                // Pebbles chip off the flying rock and drop; now and then a soft puff of dust.
                Element.EARTH -> when (rnd.nextInt(3)) {
                    0 -> ps += P(Kind.ROCK, x, y, r(-0.4f, 0.4f) * u, r(-0.6f, 0f) * u, r(0.35f, 0.5f), r(0.03f, 0.05f) * u,
                        C.ROCKS[rnd.nextInt(C.ROCKS.size)], gravity = 6f * u, rot = dir(), vrot = r(-12f, 12f), shape = rockShape())
                    1 -> if (rnd.nextBoolean()) ps += P(Kind.DUST, x + r(-0.08f, 0.08f) * u, y + r(-0.08f, 0.08f) * u, 0f, r(-0.2f, 0f) * u,
                        r(0.3f, 0.45f), r(0.08f, 0.13f) * u, C.DUST)
                    else -> Unit
                }
                Element.SHADOW -> ps += P(Kind.SMOKE, x, y, r(-0.2f, 0.2f) * u, r(-0.3f, 0f) * u, r(0.3f, 0.5f), r(0.07f, 0.11f) * u, C.SHADOW_LIGHT)
                // Now and then a crackling spark jumps off.
                Element.AIR, Element.LIGHTNING -> if (rnd.nextInt(3) == 0) {
                    val a = dir(); val sp = r(0.8f, 1.8f) * u
                    ps += P(Kind.EMBER, x, y, cos(a) * sp, sin(a) * sp, r(0.12f, 0.25f), 0.022f * u, boltGlow(e), drag = 5f)
                }
            }
        }
    }

    // ================================================================ update and draw

    fun update(dt: Float) {
        val it = ps.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.t += dt
            if (p.t >= p.life) {
                it.remove()
                continue
            }
            if (p.drag > 0f) {
                val k = max(0f, 1f - p.drag * dt)
                p.vx *= k
                p.vy *= k
            }
            p.vy += p.gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.rot += p.vrot * dt
        }
    }

    fun draw(c: Canvas) {
        for (p in ps) {
            val k = p.t / p.life
            when (p.kind) {
                Kind.FLAME -> {
                    val col = if (k < 0.4f) mix(C.FLAME_HOT, C.FLAME_MID, k / 0.4f) else mix(C.FLAME_MID, C.FLAME_COOL, (k - 0.4f) / 0.6f)
                    val a = (1f - k)
                    val rad = p.size * (1.15f - 0.7f * k)
                    fill.color = withAlpha(col, (90 * a).toInt())
                    c.drawCircle(p.x, p.y, rad * 1.7f, fill)
                    fill.color = withAlpha(col, (230 * a).toInt())
                    c.drawCircle(p.x, p.y, rad, fill)
                }
                Kind.EMBER -> {
                    val flicker = 0.65f + 0.35f * sin(p.t * 38f + p.size * 1000f)
                    val a = (1f - k) * flicker
                    fill.color = withAlpha(p.color, (80 * a).toInt())
                    c.drawCircle(p.x, p.y, p.size * 2.6f, fill)
                    fill.color = withAlpha(mix(p.color, WHITE, 0.4f), (255 * a).toInt())
                    c.drawCircle(p.x, p.y, p.size, fill)
                }
                Kind.DROP -> {
                    val a = 1f - k * k
                    stroke.color = withAlpha(p.color, (230 * a).toInt())
                    stroke.strokeWidth = p.size * 2f
                    c.drawLine(p.x, p.y, p.x - p.vx * 0.03f, p.y - p.vy * 0.03f, stroke)
                    fill.color = withAlpha(WHITE, (200 * a).toInt())
                    c.drawCircle(p.x, p.y, p.size * 0.5f, fill)
                }
                Kind.RIPPLE -> {
                    val rad = p.size * (0.25f + 0.75f * easeOut(k))
                    stroke.color = withAlpha(p.color, (200 * (1f - k)).toInt())
                    stroke.strokeWidth = 0.045f * u * (1f - k) + 0.01f * u
                    c.drawOval(p.x - rad, p.y - rad * 0.38f, p.x + rad, p.y + rad * 0.38f, stroke)
                }
                Kind.ROCK -> {
                    val shape = p.shape ?: continue
                    drawRock(c, p.x, p.y, p.size, p.rot, shape, p.color, if (k > 0.75f) (1f - k) / 0.25f else 1f)
                }
                Kind.DUST -> {
                    fill.color = withAlpha(p.color, (85 * (1f - k)).toInt())
                    c.drawCircle(p.x, p.y, p.size * (0.6f + 0.9f * k), fill)
                }
                Kind.SMOKE -> {
                    fill.color = withAlpha(p.color, (170 * (1f - k)).toInt())
                    c.drawCircle(p.x, p.y, p.size * (0.6f + 1.1f * k), fill)
                }
                Kind.FLASH -> {
                    val a = (1f - k) * (1f - k)
                    val rad = p.size * (0.6f + 0.6f * k)
                    fill.color = withAlpha(p.color, (120 * a).toInt())
                    c.drawCircle(p.x, p.y, rad, fill)
                    fill.color = withAlpha(WHITE, (200 * a).toInt())
                    c.drawCircle(p.x, p.y, rad * 0.4f, fill)
                }
                Kind.BOLT -> {
                    val a = (1f - k) * (if (rnd.nextInt(5) == 0) 0.35f else 1f)
                    drawBoltLine(c, p.x, p.y, p.x2, p.y2, p.color, a, p.size)
                }
                Kind.WISP -> {
                    // A curved streak of wind that stretches out behind the comet as it fades.
                    val a = min(1f, k / 0.15f) * (1f - k)
                    val ex = p.x + p.x2 * (0.6f + 0.4f * k)
                    path.reset()
                    path.moveTo(p.x, p.y)
                    path.quadTo((p.x + ex) / 2f, p.y + p.y2, ex, p.y + p.y2 * 0.3f)
                    stroke.color = withAlpha(p.color, (90 * a).toInt())
                    stroke.strokeWidth = p.size * 2.4f
                    c.drawPath(path, stroke)
                    stroke.color = withAlpha(mix(p.color, WHITE, 0.6f), (210 * a).toInt())
                    stroke.strokeWidth = p.size * (1f - 0.5f * k)
                    c.drawPath(path, stroke)
                }
            }
        }
    }

    // ================================================================ the magic in flight

    /**
     * Draws magic of [e] that left ([x0], [y0]), [t] seconds into its flight. [tail] holds
     * [TAIL_POINTS] positions [TAIL_STEP] seconds apart, newest (where it is now) first.
     * Fire, water and shadow fly as comets with a tapering tail, earth as a tumbling rock; air and
     * lightning are a crackling bolt that has grown from the globe to its head.
     */
    fun drawTravel(c: Canvas, e: Element, x0: Float, y0: Float, tail: FloatArray, t: Float, seed: Int) {
        val x = tail[0]
        val y = tail[1]
        // A gentle flicker, different for each piece of magic.
        val f = 1f + 0.07f * sin(t * 45f + (seed and 0xff))
        when (e) {
            Element.AIR, Element.LIGHTNING -> drawBoltLine(c, x0, y0, x, y, boltGlow(e), 1f, 1f)
            Element.FIRE -> {
                drawTail(c, tail, 0.62f * f, 1.15f, withAlpha(C.FLAME_MID, 75))
                drawTail(c, tail, 0.36f * f, 0.95f, withAlpha(C.FLAME_MID, 220))
                drawTail(c, tail, 0.2f * f, 0.5f, C.FLAME_HOT)
                fill.color = withAlpha(WHITE, 230)
                c.drawCircle(x, y, 0.06f * u, fill)
            }
            Element.WATER -> {
                drawTail(c, tail, 0.5f, 0.9f, withAlpha(C.WATER, 70))
                drawTail(c, tail, 0.3f * f, 0.75f, withAlpha(C.WATER, 235))
                drawTail(c, tail, 0.11f, 0.45f, withAlpha(C.FOAM, 220))
                fill.color = withAlpha(WHITE, 230)
                c.drawCircle(x - 0.045f * u, y - 0.05f * u, 0.045f * u, fill)
            }
            Element.EARTH -> {
                // A tumbling rock with a faint green glow of earth magic.
                fill.color = withAlpha(C.MOTE, 40)
                c.drawCircle(x, y, 0.3f * u, fill)
                for (i in travelShape.indices) travelShape[i] = 0.7f + 0.3f * (((seed ushr (i * 2)) and 3) / 3f)
                drawRock(c, x, y, 0.19f * u, t * 7f + (seed and 0xff) / 40f, travelShape, C.ROCKS[(seed ushr 16) % C.ROCKS.size], 1f)
            }
            Element.SHADOW -> {
                drawTail(c, tail, 0.5f * f, 1f, withAlpha(C.SHADOW_LIGHT, 120))
                drawTail(c, tail, 0.2f, 0.4f, withAlpha(C.SHADOW_SPARK, 230))
            }
        }
    }

    /**
     * Fills a comet shape along [tail] (newest point first): round at the head, [width] cells across,
     * tapering to a point [reach] cells back along the path, or sooner if the magic has only just set
     * off. Before it has moved at all it is just a round head.
     */
    private fun drawTail(c: Canvas, tail: FloatArray, width: Float, reach: Float, color: Int) {
        val hx = tail[0]
        val hy = tail[1]
        val w0 = width * u / 2f
        fill.color = color
        // How far back from the head each tail point lies; [last] is the last point used.
        val max = reach * u
        var last = 0
        tailAlong[0] = 0f
        for (i in 1 until TAIL_POINTS) {
            val d = hypot(tail[i * 2] - tail[i * 2 - 2], tail[i * 2 + 1] - tail[i * 2 - 1])
            if (d < 0.01f * u) break
            tailAlong[i] = min(max, tailAlong[i - 1] + d)
            last = i
            if (tailAlong[i] >= max) break
        }
        if (last == 0) {
            c.drawCircle(hx, hy, w0, fill)
            return
        }
        // Left edge from head to tip, right edge back to the head, then round over the front.
        path.reset()
        for (i in 0..last) {
            val w = w0 * (1f - tailAlong[i] / max)
            normalAt(tail, i, last)
            val px = tail[i * 2] + nx * w
            val py = tail[i * 2 + 1] + ny * w
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        for (i in last downTo 0) {
            val w = w0 * (1f - tailAlong[i] / max)
            normalAt(tail, i, last)
            path.lineTo(tail[i * 2] - nx * w, tail[i * 2 + 1] - ny * w)
        }
        normalAt(tail, 0, last)
        oval.set(hx - w0, hy - w0, hx + w0, hy + w0)
        path.arcTo(oval, Math.toDegrees(atan2(-ny, -nx).toDouble()).toFloat(), 180f, false)
        path.close()
        c.drawPath(path, fill)
    }

    private var nx = 0f
    private var ny = 0f

    /** Sets ([nx], [ny]) to the unit normal (to the left of the direction of flight) at tail point [i]. */
    private fun normalAt(tail: FloatArray, i: Int, last: Int) {
        val a = max(0, i - 1)
        val b = min(last, i + 1)
        // From older to newer: the direction of flight.
        val tx = tail[a * 2] - tail[b * 2]
        val ty = tail[a * 2 + 1] - tail[b * 2 + 1]
        val len = max(0.0001f, hypot(tx, ty))
        nx = -ty / len
        ny = tx / len
    }

    /** Outline of a rock: [shape] holds the radius (in [size]s) of each corner, going round from [rot]. */
    private fun rockPath(x: Float, y: Float, size: Float, rot: Float, shape: FloatArray) {
        path.reset()
        for (i in shape.indices) {
            val ang = rot + i * 2f * PI.toFloat() / shape.size
            val px = x + cos(ang) * size * shape[i]
            val py = y + sin(ang) * size * shape[i]
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
    }

    /** A rock chunk: solid body, a lit facet toward the upper left and a dark outline. */
    private fun drawRock(c: Canvas, x: Float, y: Float, size: Float, rot: Float, shape: FloatArray, color: Int, alpha: Float) {
        rockPath(x, y, size, rot, shape)
        fill.color = withAlpha(color, (255 * alpha).toInt())
        c.drawPath(path, fill)
        stroke.color = withAlpha(mix(color, BLACK, 0.55f), (255 * alpha).toInt())
        stroke.strokeWidth = max(1.5f, size * 0.16f)
        c.drawPath(path, stroke)
        rockPath(x - size * 0.18f, y - size * 0.22f, size * 0.55f, rot, shape)
        fill.color = withAlpha(mix(color, WHITE, 0.35f), (220 * alpha).toInt())
        c.drawPath(path, fill)
    }

    /** A jagged, flickering lightning line: a coloured glow with a white-hot core. */
    private fun drawBoltLine(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, glow: Int, alpha: Float, width: Float) {
        val len = hypot(x1 - x0, y1 - y0)
        if (len < 1f) return
        val segs = max(4, min(14, (len / (0.22f * u)).toInt()))
        val nx = -(y1 - y0) / len
        val ny = (x1 - x0) / len
        val jag = min(0.16f * u, len * 0.12f)
        path.reset()
        path.moveTo(x0, y0)
        for (i in 1 until segs) {
            val t = i.toFloat() / segs
            val off = r(-1f, 1f) * jag * sin(t * PI.toFloat()).coerceAtLeast(0.3f)
            path.lineTo(x0 + (x1 - x0) * t + nx * off, y0 + (y1 - y0) * t + ny * off)
        }
        path.lineTo(x1, y1)
        stroke.color = withAlpha(glow, (110 * alpha).toInt())
        stroke.strokeWidth = 0.14f * u * width
        c.drawPath(path, stroke)
        stroke.color = withAlpha(mix(glow, WHITE, 0.5f), (200 * alpha).toInt())
        stroke.strokeWidth = 0.06f * u * width
        c.drawPath(path, stroke)
        stroke.color = withAlpha(WHITE, (255 * alpha).toInt())
        stroke.strokeWidth = 0.025f * u * width
        c.drawPath(path, stroke)
    }
}
