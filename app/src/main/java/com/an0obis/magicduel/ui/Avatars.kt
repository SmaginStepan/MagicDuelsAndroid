package com.an0obis.magicduel.ui

import com.an0obis.magicduel.R

/**
 * A player avatar: a Midjourney character (see art/avatars/PROMPTS.md) with an idle pose and three
 * spell-cast animations, a defeat animation and its defeated pose, cut by tools/art/cut-avatar.ps1.
 * One of the casts is picked at random for
 * every cast.
 *
 * [tipX], [tipY]: where the staff's glowing crystal is, as a share of the frame's width and height,
 * so cast glows and bolts start there. [glow]: the crystal's colour. [paid] avatars will be bought in
 * the store later; for now every avatar is free.
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
    val paid: Boolean = false,
)

object Avatars {
    val MAGE = Avatar(
        "mage", R.string.avatar_mage, R.drawable.avatar_mage,
        intArrayOf(R.drawable.avatar_mage_cast1, R.drawable.avatar_mage_cast2, R.drawable.avatar_mage_cast3),
        R.drawable.avatar_mage_defeat, R.drawable.avatar_mage_defeated,
        tipX = 0.286f, tipY = 0.172f, glow = 0xFFBFE4FF.toInt(),
    )
    val SORCERESS = Avatar(
        "sorceress", R.string.avatar_sorceress, R.drawable.avatar_sorceress,
        intArrayOf(R.drawable.avatar_sorceress_cast1, R.drawable.avatar_sorceress_cast2, R.drawable.avatar_sorceress_cast3),
        R.drawable.avatar_sorceress_defeat, R.drawable.avatar_sorceress_defeated,
        tipX = 0.241f, tipY = 0.084f, glow = 0xFFB98CFF.toInt(),
    )
    val CONJURER = Avatar(
        "conjurer", R.string.avatar_conjurer, R.drawable.avatar_conjurer,
        intArrayOf(R.drawable.avatar_conjurer_cast1, R.drawable.avatar_conjurer_cast2, R.drawable.avatar_conjurer_cast3),
        R.drawable.avatar_conjurer_defeat, R.drawable.avatar_conjurer_defeated,
        tipX = 0.825f, tipY = 0.156f, glow = 0xFFFFB347.toInt(),
    )

    /** Every avatar, free ones first; the first is the default. */
    val all = listOf(MAGE, SORCERESS, CONJURER)

    fun byId(id: String?): Avatar = all.firstOrNull { it.id == id } ?: all.first()

    /** Cast sheets: 48 frames, 8 across and 6 down, looping back to the idle pose. */
    const val CAST_COLS = 8
    const val CAST_ROWS = 6
}

/**
 * An enemy mage's avatar (art/enemies/PLAN.md): a Midjourney character facing left, with
 * cybernetic parts that grow from level to level. It has an idle pose, one cast animation (48 frames
 * ending in a throw to the left) and a defeated pose for the victory card. [tipX], [tipY]: its
 * crystal, as a share of the frame.
 */
class EnemyAvatar(
    val id: String,
    val idleRes: Int,
    val castRes: Int,
    val defeatedRes: Int,
    val tipX: Float,
    val tipY: Float,
    val glow: Int,
)

object EnemyAvatars {
    /** By mage: the first mage (game level 3) wears the first. Mages without one yet are drawn. */
    val all = listOf(
        EnemyAvatar("bot_01", R.drawable.bot_01, R.drawable.bot_01_cast1, R.drawable.bot_01_defeated, 0.22f, 0.74f, 0xFF3FE0C8.toInt()),
        EnemyAvatar("bot_02", R.drawable.bot_02, R.drawable.bot_02_cast1, R.drawable.bot_02_defeated, 0.19f, 0.25f, 0xFFFFA63C.toInt()),
    )

    /** The avatar of the k-th mage (0 = the first), or null if it has none yet. */
    fun forMage(k: Int): EnemyAvatar? = all.getOrNull(k)
}
