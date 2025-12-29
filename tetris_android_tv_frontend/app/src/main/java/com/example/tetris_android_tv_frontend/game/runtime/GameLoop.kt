package com.example.tetris_android_tv_frontend.game.runtime

import com.example.tetris_android_tv_frontend.game.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max

/**
 * PUBLIC_INTERFACE
 * GameLoop is a coroutine-driven runtime controller for the TetrisEngine.
 * It manages:
 *  - Gravity ticks based on current level
 *  - Lock delay when a piece lands (cancelled if movement/rotation continues)
 *  - Line clear animation delay that pauses gravity
 *  - Pause/Resume/Reset lifecycle
 *  - Exposes observable flows for UI wiring (LiveData can be bridged later)
 *
 * This class is UI-agnostic and intended to be used from a ViewModel.
 */
// PUBLIC_INTERFACE
class GameLoop(
    private val engine: TetrisEngine,
    private val externalScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    /**
     * PUBLIC_INTERFACE
     * Snapshot stream of the game state. UI layers can observe this to render frames.
     */
    val snapshot: StateFlow<GameSnapshot> get() = _snapshot
    private val _snapshot = MutableStateFlow(engine.snapshot())

    /**
     * PUBLIC_INTERFACE
     * One-shot event stream for notable changes (optional for UI effects).
     */
    val events: SharedFlow<GameEvent> get() = _events
    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 64)

    /**
     * PUBLIC_INTERFACE
     * Lifecycle flags.
     */
    val isRunning: StateFlow<Boolean> get() = _isRunning
    private val _isRunning = MutableStateFlow(false)

    val isPaused: StateFlow<Boolean> get() = _isPaused
    private val _isPaused = MutableStateFlow(false)

    // Coroutine jobs
    private var gravityJob: Job? = null
    private var lockDelayJob: Job? = null

    // Timing constants (milliseconds)
    // Gravity timing derived from level; we clamp to a safe minimum.
    private val minGravityMs = 50L

    // Per Tetris guideline typical lock delay ~500ms (tunable)
    private val lockDelayMsDefault = 500L

    // Short animation delay for line clears (tunable)
    private val lineClearDelayMs = 300L

    // Internal state for lock handling
    private var lastOnGround = false

    // PUBLIC_INTERFACE
    /**
     * Start the game loop, begins gravity ticks.
     * If already running, this is a no-op.
     */
    fun start() {
        if (_isRunning.value) return
        _isRunning.value = true
        _isPaused.value = false
        startGravityLoop()
        publishSnapshot()
    }

    // PUBLIC_INTERFACE
    /**
     * Stop the game loop and cancel all running jobs.
     */
    fun stop() {
        _isRunning.value = false
        _isPaused.value = false
        gravityJob?.cancel()
        gravityJob = null
        cancelLockDelay()
    }

    // PUBLIC_INTERFACE
    /**
     * Reset the engine and restart gravity if running.
     */
    fun reset(seed: Long = 0L) {
        engine.reset(seed)
        cancelLockDelay()
        publishSnapshot()
        if (_isRunning.value && !_isPaused.value) {
            restartGravityLoop()
        }
        externalScope.launch {
            _events.emit(GameEvent.Reset)
        }
    }

    // PUBLIC_INTERFACE
    /**
     * Pause gravity and lock-delay progression. Does not mutate engine state.
     */
    fun pause() {
        if (!_isRunning.value) return
        if (_isPaused.value) return
        _isPaused.value = true
        gravityJob?.cancel()
        gravityJob = null
        // Keep lockDelayJob paused by cancellation; we'll reschedule on resume if needed.
        cancelLockDelay()
        publishSnapshot()
        externalScope.launch { _events.emit(GameEvent.Paused) }
    }

    // PUBLIC_INTERFACE
    /**
     * Resume gravity and continue gameplay.
     */
    fun resume() {
        if (!_isRunning.value) return
        if (!_isPaused.value) return
        _isPaused.value = false
        startGravityLoop()
        publishSnapshot()
        externalScope.launch { _events.emit(GameEvent.Resumed) }
    }

    // PUBLIC_INTERFACE
    /**
     * Players' movement/rotation actions should call this to ensure lock delay is cancelled if
     * the piece was on the ground and is moved/rotated to continue.
     * Returns true if the engine accepted the action and it cancelled a pending lock.
     */
    fun onPlayerActionAccepted(cancelLockIfAny: Boolean = true): Boolean {
        if (!_isRunning.value || _isPaused.value) return false
        if (cancelLockIfAny) {
            cancelLockDelay()
        }
        publishSnapshot()
        return true
    }

    // Gravity loop and timing

    private fun startGravityLoop() {
        gravityJob?.cancel()
        gravityJob = externalScope.launch {
            while (isActive && _isRunning.value) {
                if (_isPaused.value) {
                    delay(50)
                    continue
                }

                val delayMs = gravityDelayMs(engine.snapshot().score.level)
                delay(delayMs)

                if (_isPaused.value || !_isRunning.value) continue

                // Attempt gravity tick
                val before = engine.snapshot()
                if (before.gameOver) {
                    handleGameOver()
                    break
                }

                val moved = engine.tickGravity()
                val after = engine.snapshot()
                publishSnapshot()

                if (after.gameOver) {
                    handleGameOver()
                    break
                }

                if (moved) {
                    // piece fell successfully; if previously on ground, keep monitoring
                    lastOnGround = false
                    continue
                } else {
                    // Could not move down -> on ground; begin lock delay if not already scheduled
                    if (!lastOnGround) {
                        lastOnGround = true
                        scheduleLockDelay()
                    }
                }
            }
        }
    }

    private fun restartGravityLoop() {
        startGravityLoop()
    }

    /**
     * Gravity delay formula: reduce delay as level increases.
     * This is a simplified mapping, not the official are/gravity table,
     * but is suitable for an arcade-like feel.
     */
    private fun gravityDelayMs(level: Int): Long {
        // Start around 1 second at level 1 and decay to minGravityMs
        val base = 1000L
        val decay = (level - 1) * 60L
        return max(minGravityMs, base - decay)
    }

    private fun scheduleLockDelay() {
        cancelLockDelay()
        lockDelayJob = externalScope.launch {
            val startSnapshot = engine.snapshot()
            val lockDelay = lockDelayMsDefault
            delay(lockDelay)

            // After lock delay, if the piece hasn't moved (no player action canceled), lock is already performed by gravity?
            // Our engine locks only on blocked gravity/hard drop, so simulate "finalize" by issuing a soft nudge:
            // We perform a no-op touch: calling tickGravity() will lock if still blocked.
            if (!_isPaused.value && _isRunning.value && !startSnapshot.gameOver) {
                val moved = engine.tickGravity()
                // If moved, it means piece wasn't actually locked; continue normally (gravity loop continues).
                publishSnapshot()

                // If not moved again -> engine locked and handled spawn/line clear.
                if (!moved) {
                    // Check for line clears by comparing lines before/after; since engine internalizes clears,
                    // we just insert a short delay to simulate line clear animation if lines increased.
                    val beforeLines = startSnapshot.score.totalLines
                    val now = engine.snapshot()
                    val afterLines = now.score.totalLines
                    if (afterLines > beforeLines) {
                        // Pause gravity during animation
                        gravityJob?.cancel()
                        gravityJob = null
                        externalScope.launch { _events.emit(GameEvent.LinesCleared(afterLines - beforeLines)) }
                        delay(lineClearDelayMs)
                        if (_isRunning.value && !_isPaused.value && !now.gameOver) {
                            restartGravityLoop()
                        }
                    }
                    lastOnGround = false
                } else {
                    // If it moved during lock delay (shouldn't happen unless engine allowed), cancel ground assumption
                    lastOnGround = false
                }
                if (engine.snapshot().gameOver) {
                    handleGameOver()
                }
            }
        }
    }

    private fun cancelLockDelay() {
        lockDelayJob?.cancel()
        lockDelayJob = null
        lastOnGround = false
    }

    private fun publishSnapshot() {
        _snapshot.value = engine.snapshot()
    }

    private fun handleGameOver() {
        cancelLockDelay()
        gravityJob?.cancel()
        gravityJob = null
        _isRunning.value = false
        externalScope.launch { _events.emit(GameEvent.GameOver) }
        publishSnapshot()
    }
}

/**
 * PUBLIC_INTERFACE
 * Events emitted by the GameLoop for UI layers to optionally react to.
 */
sealed class GameEvent {
    /** Emitted when one or more lines were cleared. count = number of lines cleared. */
    data class LinesCleared(val count: Int) : GameEvent()
    /** Emitted when the game is paused. */
    data object Paused : GameEvent()
    /** Emitted when the game is resumed. */
    data object Resumed : GameEvent()
    /** Emitted when the game is reset. */
    data object Reset : GameEvent()
    /** Emitted when the game is over. */
    data object GameOver : GameEvent()
}
