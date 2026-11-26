package com.an0obis.magicduel.ui

import android.content.SharedPreferences
import com.an0obis.magicduel.net.ScoreClient
import com.an0obis.magicduel.net.ScoreEntry
import java.util.UUID
import java.util.concurrent.Executors

/**
 * The online leaderboard: each player's highest level, under their name. Ties go to whoever reached
 * the level first (the server's rule). You are a random player id kept on the phone.
 *
 * Your best level is sent whenever it rises or your name changes, and again later if the phone was
 * offline. [open] loads the top players and your own row with its neighbours. Network calls run on
 * one background thread; results reach the game through [post] (the UI thread).
 */
class Leaderboard(
    private val client: ScoreClient,
    private val prefs: SharedPreferences,
    private val post: (Runnable) -> Unit,
) {
    enum class State { IDLE, LOADING, READY, ERROR, UNAVAILABLE }

    var state = State.IDLE
        private set

    /** The best [TOP] players. */
    var top: List<ScoreEntry> = emptyList()
        private set

    /** Your row with its neighbours (empty until you're on the board). */
    var mine: List<ScoreEntry> = emptyList()
        private set

    val playerId: String = prefs.getString(KEY_ID, null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString(KEY_ID, it).apply()
    }

    /** Your highest level so far (it stays after "Restart game"). */
    var bestLevel = prefs.getInt(KEY_BEST, 0)
        private set

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "leaderboard").apply { isDaemon = true } }

    /** You reached level [levelNumber] (1 = the first duel); [name] is your name. */
    fun reached(levelNumber: Int, name: String) {
        if (levelNumber > bestLevel) {
            bestLevel = levelNumber
            prefs.edit().putInt(KEY_BEST, bestLevel).apply()
        }
        sync(name)
    }

    /** Sends your best level and name if the server doesn't have them yet (or after a rename). */
    fun sync(name: String) {
        if (!client.configured || bestLevel <= 0) return
        worker.execute { trySubmit(name) }
    }

    /** Loads the board: the top players and your row. */
    fun open(name: String) {
        if (!client.configured) {
            state = State.UNAVAILABLE
            return
        }
        state = State.LOADING
        worker.execute {
            try {
                trySubmit(name)
                val best = client.top(TOP)
                val around = if (best.any { it.playerId == playerId }) emptyList() else client.around(playerId, AROUND)
                post(Runnable {
                    top = best
                    mine = around
                    state = State.READY
                })
            } catch (e: Exception) {
                post(Runnable { state = State.ERROR })
            }
        }
    }

    private fun trySubmit(name: String) {
        if (bestLevel <= 0) return
        val sentLevel = prefs.getInt(KEY_SENT_LEVEL, 0)
        val sentName = prefs.getString(KEY_SENT_NAME, null)
        if (sentLevel == bestLevel && sentName == name) return
        try {
            client.submit(playerId, name, bestLevel)
            prefs.edit().putInt(KEY_SENT_LEVEL, bestLevel).putString(KEY_SENT_NAME, name).apply()
        } catch (e: Exception) {
            // Offline or refused: it is tried again on the next level, rename or visit to the board.
        }
    }

    companion object {
        const val TOP = 10
        const val AROUND = 1
        const val KEY_ID = "lbPlayerId"
        const val KEY_BEST = "bestLevel"
        const val KEY_SENT_LEVEL = "lbSentLevel"
        const val KEY_SENT_NAME = "lbSentName"
    }
}
