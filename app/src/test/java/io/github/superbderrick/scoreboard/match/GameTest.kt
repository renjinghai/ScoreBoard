package io.github.superbderrick.scoreboard.match

import org.junit.Assert.*
import org.junit.Test

class GameTest {
    private fun game(seq: String, first: Side = Side.LEFT) = Game.decode(first, seq)

    @Test
    fun serverChangesEveryTwoBalls() {
        val expected = listOf(Side.LEFT, Side.LEFT, Side.RIGHT, Side.RIGHT, Side.LEFT, Side.LEFT)
        expected.forEachIndexed { n, side ->
            assertEquals("ball $n", side, game("L".repeat(n)).server)
        }
    }

    @Test
    fun eachServerServesTwoBallsThenOneInDeuce() {
        assertEquals(listOf(1, 2, 1, 2, 1), (0..4).map { game("L".repeat(it)).serveNumber })
        assertFalse(game("L".repeat(19)).isDeuceServing)
        val deuce = game("LR".repeat(10))
        assertTrue(deuce.isDeuceServing)
        assertEquals(1, deuce.serveNumber)
        deuce.add(Rally.LEFT)
        assertEquals(1, deuce.serveNumber)
    }

    @Test
    fun serverChangesEveryBallAfterTenAll() {
        val g = game("LR".repeat(10)) // 10:10, 20 balls played
        assertEquals(Side.LEFT, g.server)
        g.add(Rally.LEFT)
        assertEquals(Side.RIGHT, g.server)
        g.add(Rally.RIGHT)
        assertEquals(Side.LEFT, g.server)
        g.add(Rally.LEFT)
        assertEquals(Side.RIGHT, g.server)
    }

    @Test
    fun serveRotationRespectsFirstServer() {
        assertEquals(Side.RIGHT, game("", Side.RIGHT).server)
        assertEquals(Side.LEFT, game("RR", Side.RIGHT).server)
    }

    @Test
    fun unknownBallCountsForServeButNotScore() {
        val g = game("L?")
        assertEquals(1, g.leftScore)
        assertEquals(0, g.rightScore)
        assertEquals(1, g.unknownCount)
        assertEquals(Side.RIGHT, g.server) // 2 balls played
    }

    @Test
    fun gameEndsAtElevenWithTwoClear() {
        val g = game("L".repeat(11))
        assertEquals(Game.Status.OVER, g.status)
        assertEquals(Side.LEFT, g.winner)
        assertFalse(g.add(Rally.RIGHT))
    }

    @Test
    fun gameContinuesAtElevenTen() {
        val g = game("R".repeat(10) + "L".repeat(10) + "L")
        assertEquals(Game.Status.IN_PROGRESS, g.status)
        g.add(Rally.LEFT)
        assertEquals(Game.Status.OVER, g.status)
        assertEquals(12, g.leftScore)
    }

    @Test
    fun unknownBallsCanMeanGameMightBeOver() {
        assertEquals(Game.Status.IN_PROGRESS, game("L?").status)
        val g = game("L".repeat(10) + "?")
        assertEquals(Game.Status.MAYBE_OVER, g.status)
        assertNull(g.winner)
        assertTrue(g.add(Rally.RIGHT)) // may still continue
    }

    @Test
    fun resolveAssignsWinnerAndFinishesGame() {
        val g = game("L".repeat(10) + "?")
        assertTrue(g.resolve(10, Side.LEFT))
        assertEquals(Game.Status.OVER, g.status)
        assertEquals(Side.LEFT, g.winner)
    }

    @Test
    fun resolveRejectsKnownBallAndBadIndex() {
        val g = game("L?")
        assertFalse(g.resolve(0, Side.RIGHT))
        assertFalse(g.resolve(5, Side.RIGHT))
        assertEquals("L?", g.encode())
    }

    @Test
    fun resolveRejectedIfGameWouldHaveEndedEarlier() {
        // 1 unknown, 10 left points, then a right point: 10:1 + unknown.
        val g = game("?" + "L".repeat(10) + "R")
        assertEquals(Game.Status.IN_PROGRESS, g.status.let { if (it == Game.Status.MAYBE_OVER) Game.Status.IN_PROGRESS else it })
        // Giving the unknown ball to LEFT would have ended the game (11:0) before the last ball.
        assertFalse(g.resolve(0, Side.LEFT))
        assertEquals("?LLLLLLLLLLR", g.encode())
        assertTrue(g.resolve(0, Side.RIGHT))
        assertEquals(Game.Status.IN_PROGRESS, g.status)
    }

    @Test
    fun undoRemovesLastBall() {
        val g = game("LR?")
        assertEquals(Rally.UNKNOWN, g.undo())
        assertEquals("LR", g.encode())
        assertNull(game("").undo())
    }

    @Test
    fun encodeDecodeRoundTrip() {
        assertEquals("LR?L", game("LR?L").encode())
    }

    @Test
    fun calibrateFillsUnknownsAndAddsMissedBalls() {
        val g = game("L?R?")
        assertTrue(g.calibrate(3, 2))
        assertEquals(3, g.leftScore)
        assertEquals(2, g.rightScore)
        assertEquals(0, g.unknownCount)
        assertEquals(5, g.ballsPlayed)
    }

    @Test
    fun calibrateDropsSurplusUnknownBalls() {
        val g = game("L?R?")
        assertTrue(g.calibrate(1, 1))
        assertEquals("LR", g.encode())
    }

    @Test
    fun calibrateKeepsKnownBallsInPlace() {
        val g = game("L?R")
        assertTrue(g.calibrate(1, 2))
        assertEquals("LRR", g.encode())
    }

    @Test
    fun calibrateSetsServeFromRealScore() {
        val g = game("L?")
        assertEquals(Side.RIGHT, g.server)
        assertTrue(g.calibrate(2, 2)) // 4 balls played
        assertEquals(Side.LEFT, g.server)
        assertEquals(1, g.serveNumber)
    }

    @Test
    fun calibrateRejectsScoreBelowKnownPoints() {
        val g = game("LL?")
        assertFalse(g.calibrate(1, 0))
        assertEquals("LL?", g.encode())
    }

    @Test
    fun calibrateRejectsImpossibleScore() {
        val g = game("L?")
        assertFalse(g.calibrate(13, 5))
        assertFalse(g.calibrate(14, 11))
        assertEquals("L?", g.encode())
    }

    @Test
    fun calibrateCanFinishTheGame() {
        val g = game("L".repeat(9) + "?")
        assertTrue(g.calibrate(11, 3))
        assertEquals(Game.Status.OVER, g.status)
        assertEquals(Side.LEFT, g.winner)
    }

    @Test
    fun validScores() {
        assertTrue(Game.isValidScore(11, 11))
        assertTrue(Game.isValidScore(12, 10))
        assertTrue(Game.isValidScore(11, 0))
        assertFalse(Game.isValidScore(12, 9))
        assertFalse(Game.isValidScore(-1, 0))
    }
}
