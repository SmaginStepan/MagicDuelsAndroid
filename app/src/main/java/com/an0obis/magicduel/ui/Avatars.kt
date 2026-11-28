package com.an0obis.magicduel.ui

import com.an0obis.magicduel.R
import com.an0obis.magicduel.game.Economy

/**
 * A player avatar: a Midjourney character (see art/avatars/PROMPTS.md) with an idle pose and three
 * spell-cast animations, a defeat animation and its defeated pose, cut by tools/art/cut-avatar.ps1.
 * One of the casts is picked at random for
 * every cast.
 *
 * [tipX], [tipY]: where the staff's glowing crystal is, as a share of the frame's width and height,
 * so cast glows and bolts start there. [glow]: the crystal's colour. [scale]: the frame's height in
 * the duel relative to the others', for a frame with extra room above the figure (Google Flow clips
 * keep room for magic over the head), so every figure stands as tall. [price]: the essence it costs
 * to unlock (0 for the free one); once unlocked it is yours for good.
 */
class Avatar(
    val id: String,
    val nameRes: Int,
    val idleRes: Int,
    val castRes: IntArray,
    /** Its collapse when you lose (a sheet like the casts) and the defeated pose it ends in. */
    val defeatRes: Int,
    val defeatedRes: Int,
    val tipX: Float,
    val tipY: Float,
    val glow: Int,
    val price: Int = 0,
    val scale: Float = 1f,
    /**
     * Its revive in the revive popup (a sheet like the casts): rising from the defeated pose to the
     * idle one. Without one the popup plays the collapse backwards.
     */
    val reviveRes: Int? = null,
) {
    val paid: Boolean get() = price > 0
}

object Avatars {
    val MAGE = Avatar(
        "mage", R.string.avatar_mage, R.drawable.avatar_mage,
        intArrayOf(R.drawable.avatar_mage_cast1, R.drawable.avatar_mage_cast2, R.drawable.avatar_mage_cast3),
        R.drawable.avatar_mage_defeat, R.drawable.avatar_mage_defeated,
        // Casts and defeat made with Google Flow (art/avatars/PROMPTS.md); room for magic over his head.
        tipX = 0.173f, tipY = 0.280f, glow = 0xFFBFE4FF.toInt(), scale = 1.10f,
        reviveRes = R.drawable.avatar_mage_revive,
    )
    val SORCERESS = Avatar(
        "sorceress", R.string.avatar_sorceress, R.drawable.avatar_sorceress,
        intArrayOf(R.drawable.avatar_sorceress_cast1, R.drawable.avatar_sorceress_cast2, R.drawable.avatar_sorceress_cast3),
        R.drawable.avatar_sorceress_defeat, R.drawable.avatar_sorceress_defeated,
        // Casts and defeat made with Google Flow (art/avatars/PROMPTS.md); the frame has room for magic
        // over her head, so it's drawn taller.
        tipX = 0.238f, tipY = 0.223f, glow = 0xFFB98CFF.toInt(), scale = 1.16f,
        reviveRes = R.drawable.avatar_sorceress_revive, price = Economy.PRICE_NORMAL,
    )
    val CONJURER = Avatar(
        "conjurer", R.string.avatar_conjurer, R.drawable.avatar_conjurer,
        intArrayOf(R.drawable.avatar_conjurer_cast1, R.drawable.avatar_conjurer_cast2, R.drawable.avatar_conjurer_cast3),
        R.drawable.avatar_conjurer_defeat, R.drawable.avatar_conjurer_defeated,
        // Casts and defeat made with Google Flow (art/avatars/PROMPTS.md); room for magic over his head.
        tipX = 0.856f, tipY = 0.222f, glow = 0xFFFFB347.toInt(), scale = 1.09f,
        reviveRes = R.drawable.avatar_conjurer_revive, price = Economy.PRICE_NORMAL,
    )

    /** Every avatar, the free one first (it is the default): the Archmage; then the paid ones. */
    val all = listOf(MAGE, SORCERESS, CONJURER)

    /** The avatars on sale (shown in the shop). */
    val paid: List<Avatar> get() = all.filter { it.paid }

    fun byId(id: String?): Avatar = all.firstOrNull { it.id == id } ?: all.first()

    /** Cast sheets: 48 frames, 8 across and 6 down, looping back to the idle pose. */
    const val CAST_COLS = 8
    const val CAST_ROWS = 6
}

/**
 * An enemy mage's avatar (art/enemies/PLAN.md): a Midjourney character facing left, with
 * cybernetic parts that grow from level to level. It has an idle pose, one cast animation (48 frames
 * ending in a throw to the left) and a defeated pose for the victory card. [tipX], [tipY]: its
 * crystal, as a share of the frame. [scale]: the frame's height relative to the others', when its
 * frame has extra room (bot_02's cast raises a ring of fire above its head), so the figures match.
 */
class EnemyAvatar(
    val id: String,
    val idleRes: Int,
    val castRes: Int,
    val defeatedRes: Int,
    val tipX: Float,
    val tipY: Float,
    val glow: Int,
    val scale: Float = 1f,
)

object EnemyAvatars {
    /** By mage: the first mage (game level 3) wears the first. Mages without one yet are drawn. */
    val all = listOf(
        EnemyAvatar("bot_01", R.drawable.bot_01, R.drawable.bot_01_cast1, R.drawable.bot_01_defeated, 0.22f, 0.74f, 0xFF3FE0C8.toInt()),
        // Cast made with Google Flow (Veo 3.1 Lite); its frame has room for the fire ring above the head.
        EnemyAvatar("bot_02", R.drawable.bot_02, R.drawable.bot_02_cast1, R.drawable.bot_02_defeated, 0.21f, 0.37f, 0xFFFFA63C.toInt(), scale = 1.19f),
    )

    /** The avatar of the k-th mage (0 = the first), or null if it has none yet. */
    fun forMage(k: Int): EnemyAvatar? = all.getOrNull(k)
}
