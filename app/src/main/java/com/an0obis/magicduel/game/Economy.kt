package com.an0obis.magicduel.game

/**
 * Essence, the resource a revive costs, and the daily gift that hands it out.
 *
 * Once a day (the phone's date, as an epoch day) the first launch collects [DAILY_ESSENCE] plus a
 * streak bonus: the n-th day in a row gets [STREAK_BONUS] of n, over a 7-day cycle that starts again
 * after day 7. Missing a day resets the streak to day 1. A new player starts with [START_ESSENCE].
 *
 * The state is three numbers ([essence], [lastClaimDay], [streakDay]); the screen saves them.
 */
class Economy(
    essence: Int = START_ESSENCE,
    /** The epoch day of the last collected gift, or [NEVER]. */
    lastClaimDay: Long = NEVER,
    /** The day of the streak cycle the last gift was for (1 to [STREAK_DAYS]), 0 before the first. */
    streakDay: Int = 0,
) {
    var essence = essence
        private set
    var lastClaimDay = lastClaimDay
        private set
    var streakDay = streakDay
        private set

    /** What today's gift is: the streak day it counts as, the base essence and the streak bonus. */
    data class Gift(val streakDay: Int, val base: Int, val bonus: Int) {
        val total: Int get() = base + bonus
    }

    /**
     * True if today's gift hasn't been collected yet. A day before the last gift's (the phone's clock
     * turned back) brings nothing, so moving the date back and forth doesn't hand out extra gifts.
     */
    fun giftDue(today: Long): Boolean = today > lastClaimDay

    /** Today's gift, without collecting it (null if it was already collected today). */
    fun giftFor(today: Long): Gift? {
        if (!giftDue(today)) return null
        val day = if (lastClaimDay != NEVER && today == lastClaimDay + 1) streakDay % STREAK_DAYS + 1 else 1
        return Gift(day, DAILY_ESSENCE, STREAK_BONUS[day - 1])
    }

    /** Collects today's gift: adds it to [essence] and moves the streak on. Null if already collected. */
    fun claim(today: Long): Gift? {
        val gift = giftFor(today) ?: return null
        essence += gift.total
        lastClaimDay = today
        streakDay = gift.streakDay
        return gift
    }

    /** Spends [amount] essence if there is enough; false (and nothing spent) if not. */
    fun spend(amount: Int): Boolean {
        if (amount < 0 || amount > essence) return false
        essence -= amount
        return true
    }

    /** Adds [amount] essence (a pack from the shop). */
    fun add(amount: Int) {
        if (amount > 0) essence += amount
    }

    companion object {
        const val START_ESSENCE = 3
        const val DAILY_ESSENCE = 1
        const val STREAK_DAYS = 7

        /** The bonus on day 1 to 7 of the streak, on top of [DAILY_ESSENCE]. */
        val STREAK_BONUS = intArrayOf(0, 1, 1, 2, 2, 3, 5)

        const val NEVER = Long.MIN_VALUE

        /** What the [n]-th revive in one duel costs (n = 1, 2, 3 …): 1, 2, 4, 8 … essence. */
        fun reviveCost(n: Int): Int = 1 shl (n - 1).coerceIn(0, 20)
    }
}
