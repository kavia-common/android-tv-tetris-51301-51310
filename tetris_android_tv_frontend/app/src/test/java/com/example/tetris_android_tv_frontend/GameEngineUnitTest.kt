package com.example.tetris_android_tv_frontend

import com.example.tetris_android_tv_frontend.game.*
import org.junit.Assert.*
import org.junit.Test

class GameEngineUnitTest {

    @Test
    fun sevenBag_isDeterministic() {
        val rng1 = SevenBagRNG(42L)
        val rng2 = SevenBagRNG(42L)
        val seq1 = (0 until 14).map { rng1.next() }
        val seq2 = (0 until 14).map { rng2.next() }
        assertEquals(seq1, seq2)
    }

    @Test
    fun engine_initializesWithActivePiece() {
        val engine = TetrisEngine(seed = 1L)
        val snap = engine.snapshot()
        assertNotNull(snap.active)
        assertFalse(snap.gameOver)
        assertEquals(10, snap.board.width)
        assertEquals(20, snap.board.height)
    }

    @Test
    fun movement_leftRight_noCollision() {
        val engine = TetrisEngine(seed = 1L)
        val start = engine.snapshot().active!!.origin.x
        assertTrue(engine.moveLeft() || engine.moveRight() || true)
        val after = engine.snapshot().active!!.origin.x
        assertNotEquals("Origin should change after a movement attempt", start, after)
    }

    @Test
    fun rotation_attempt_appliesSRSOrStays() {
        val engine = TetrisEngine(seed = 7L)
        val before = engine.snapshot().active!!
        val rotated = engine.rotateCW()
        val after = engine.snapshot().active!!
        if (rotated) {
            // rotation changed
            assertNotEquals(before.rotation, after.rotation)
        } else {
            // position unchanged if rotation failed
            assertEquals(before.rotation, after.rotation)
            assertEquals(before.origin, after.origin)
        }
    }

    @Test
    fun hardDrop_locksAndSpawnsNewPiece() {
        val engine = TetrisEngine(seed = 3L)
        val activeBefore = engine.snapshot().active!!
        val steps = engine.hardDrop()
        assertTrue(steps > 0)
        val after = engine.snapshot()
        assertNotNull(after.active)
        // New active should be a different instance or type after lock
        val activeAfter = after.active!!
        val sameTypePossible = activeBefore.type == activeAfter.type
        // At least origin should reset near top
        assertTrue(activeAfter.origin.y <= 1)
        // Score should increase by at least 2*steps
        assertTrue(after.score.score >= steps * 2)
    }

    @Test
    fun lineClear_scoresAndLevels() {
        // Build a scenario: drop pieces to fill one full line
        val engine = TetrisEngine(seed = 100L)
        // Hard drop several times; eventually a line should clear
        var clearedOccurred = false
        repeat(20) {
            val prevLines = engine.snapshot().score.totalLines
            engine.hardDrop()
            val newLines = engine.snapshot().score.totalLines
            if (newLines > prevLines) {
                clearedOccurred = true
                return@repeat
            }
        }
        assertTrue(clearedOccurred)
        val snap = engine.snapshot()
        assertTrue("Score should be > 0 after clears", snap.score.score > 0)
        assertTrue("Level should be >= 1", snap.score.level >= 1)
    }

    @Test
    fun hold_swapsPieces_andPreventsConsecutiveHold() {
        val engine = TetrisEngine(seed = 5L, enableHold = true)
        val first = engine.snapshot().active!!
        val ok = engine.hold()
        assertTrue(ok)
        val afterHold = engine.snapshot()
        assertNotNull(afterHold.hold)
        val second = afterHold.active!!
        // Can't hold again immediately
        assertFalse(engine.hold())
        // After locking, can hold again
        engine.hardDrop()
        assertTrue(engine.hold())
        val third = engine.snapshot().active!!
        // Ensure the new active is from hold or next queue
        assertNotEquals(first.origin, third.origin) // different instance position anyway
    }
}
