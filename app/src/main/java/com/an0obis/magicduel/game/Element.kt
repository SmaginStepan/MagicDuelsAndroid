package com.an0obis.magicduel.game

/**
 * Magic elements. The first [Rules.START_ELEMENTS] are used from the start;
 * the rest are meant to unlock through levelling up.
 */
enum class Element {
    FIRE, WATER, EARTH, AIR, LIGHTNING, SHADOW;

    companion object {
        fun active(count: Int): List<Element> = entries.take(count.coerceIn(3, entries.size))
    }
}
