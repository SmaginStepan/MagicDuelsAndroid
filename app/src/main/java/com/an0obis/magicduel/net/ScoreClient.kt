package com.an0obis.magicduel.net

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** One row of a leaderboard: [score] is the player's highest level. */
data class ScoreEntry(val rank: Int, val playerId: String, val name: String, val score: Int)

/**
 * A client for the leaderboard service (github.com/SmaginStepan/ScoreServer). A board keeps each
 * player's best score; ties go to whoever reached it first. Every request carries the game's API key;
 * a score submission is also signed: X-Sig = HMAC-SHA256(secret, "{ts}\n{METHOD}\n{path}\n{sha256hex(body)}"),
 * where path is the server's own path (`/v1/...`), without any prefix in [baseUrl] that a proxy strips.
 *
 * All calls block: run them off the main thread. They throw [IOException] when the server can't be
 * reached or answers with an error.
 */
class ScoreClient(
    baseUrl: String,
    private val game: String,
    private val apiKey: String,
    private val secret: String,
    private val board: String = "alltime",
) {
    private val base = baseUrl.trimEnd('/')

    /** False when the build has no server settings (see app/build.gradle.kts). */
    val configured: Boolean = base.isNotEmpty() && game.isNotEmpty() && apiKey.isNotEmpty() && secret.isNotEmpty()

    /** Sends [score] for [playerId] under [name] (the board keeps the best; the name always updates). */
    fun submit(playerId: String, name: String, score: Int) {
        val body = JSONObject()
            .put("player_id", playerId)
            .put("name", name)
            .put("score", score)
            .put("device_ts", System.currentTimeMillis() / 1000)
            .toString()
        val reply = request("POST", "/v1/$game/boards/$board/scores", body)
        if (!reply.optBoolean("accepted", false)) throw IOException("score refused: ${reply.optString("reason")}")
    }

    /** The best [limit] players. */
    fun top(limit: Int): List<ScoreEntry> = entries(request("GET", "/v1/$game/boards/$board/top?limit=$limit", null))

    /** [playerId]'s row with up to [span] players above and below it; empty if they aren't on the board. */
    fun around(playerId: String, span: Int): List<ScoreEntry> =
        entries(request("GET", "/v1/$game/boards/$board/around/$playerId?span=$span", null))

    private fun entries(reply: JSONObject): List<ScoreEntry> {
        val list = reply.optJSONArray("entries") ?: return emptyList()
        return (0 until list.length()).map { i ->
            val e = list.getJSONObject(i)
            ScoreEntry(e.getInt("rank"), e.getString("player_id"), e.getString("name"), e.getInt("score"))
        }
    }

    private fun request(method: String, pathAndQuery: String, body: String?): JSONObject {
        val conn = URL(base + pathAndQuery).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("X-Api-Key", apiKey)
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                val ts = (System.currentTimeMillis() / 1000).toString()
                val path = pathAndQuery.substringBefore('?')
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("X-Ts", ts)
                conn.setRequestProperty("X-Sig", signature(secret, ts, method, path, bytes))
                conn.doOutput = true
                conn.outputStream.use { it.write(bytes) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code: $text")
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val TIMEOUT_MS = 8000

        /** The server's signature for a write: HMAC-SHA256(secret, "{ts}\n{METHOD}\n{path}\n{sha256hex(body)}"), hex. */
        fun signature(secret: String, ts: String, method: String, path: String, body: ByteArray): String {
            val bodyHash = hex(MessageDigest.getInstance("SHA-256").digest(body))
            val payload = "$ts\n${method.uppercase()}\n$path\n$bodyHash"
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            return hex(mac.doFinal(payload.toByteArray(Charsets.UTF_8)))
        }

        private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    }
}
