package com.an0obis.magicduel.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScoreClientTest {
    @Test
    fun signsLikeTheServer() {
        // Expected value computed with ScoreServer's own src/hmac.ts algorithm (Node's crypto):
        // HMAC-SHA256("s3cret", "1764000000\nPOST\n/v1/arcaneduel/boards/alltime/scores\n" + sha256hex(body)).
        val body = "{\"player_id\":\"p1\",\"name\":\"Степан\",\"score\":7}".toByteArray(Charsets.UTF_8)
        assertEquals(
            "b9e16de77153376a7824dc9687c8559b0e1820bfc364a1dc326d3074fcae7ba0",
            ScoreClient.signature("s3cret", "1764000000", "post", "/v1/arcaneduel/boards/alltime/scores", body),
        )
    }

    @Test
    fun aBuildWithoutServerSettingsIsNotConfigured() {
        assertFalse(ScoreClient("", "", "", "").configured)
        assertFalse(ScoreClient("https://example.com", "arcaneduel", "key", "").configured)
    }
}
