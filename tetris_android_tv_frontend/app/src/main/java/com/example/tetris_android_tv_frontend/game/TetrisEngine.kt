package com.example.tetris_android_tv_frontend.game

/**
 * Core Tetris engine independent from Android UI.
 * Handles piece spawning, movement, rotation with SRS-like kicks, collision detection,
 * line clearing, scoring/levels, 7-bag next queue, and optional hold.
 *
 * Coordinate system: (0,0) is top-left. x increases to right, y increases downward.
 */

// PUBLIC_INTERFACE
data class ScoreState(
    val score: Int = 0,
    val level: Int = 1,
    val totalLines: Int = 0
)

// PUBLIC_INTERFACE
data class GameSnapshot(
    val board: BoardState,
    val active: PieceState?,
    val queue: List<TetrominoType>,
    val hold: TetrominoType?,
    val canHold: Boolean,
    val score: ScoreState,
    val gameOver: Boolean
)

// PUBLIC_INTERFACE
class TetrisEngine(
    val width: Int = 10,
    val height: Int = 20,
    seed: Long = 0L,
    val enableHold: Boolean = true
) {
    private var board: BoardState =
        BoardState(width, height, List(width * height) { BoardCell(false, null) })
    private val rng = SevenBagRNG(seed)
    private val queue: ArrayDeque<TetrominoType> = ArrayDeque()
    private var active: PieceState? = null
    private var hold: TetrominoType? = null
    private var canHold: Boolean = true
    private var scoreState = ScoreState()
    private var over = false

    init {
        ensureQueue(5)
        spawnNext()
    }

    // PUBLIC_INTERFACE
    fun snapshot(): GameSnapshot = GameSnapshot(
        board = board,
        active = active,
        queue = queue.toList(),
        hold = hold,
        canHold = canHold,
        score = scoreState,
        gameOver = over
    )

    // PUBLIC_INTERFACE
    fun reset(seed: Long = 0L) {
        board = BoardState(width, height, List(width * height) { BoardCell(false, null) })
        rng.reseed(seed)
        queue.clear()
        hold = null
        canHold = true
        scoreState = ScoreState()
        over = false
        ensureQueue(5)
        spawnNext()
    }

    // PUBLIC_INTERFACE
    fun tickGravity(): Boolean {
        if (over) return false
        val a = active ?: return false
        val moved = tryMove(a, Coord(0, 1))
        if (moved != null) {
            active = moved
            return true
        }
        // Lock if cannot move down
        lockActiveAndProceed()
        return false
    }

    // PUBLIC_INTERFACE
    fun softDrop(): Int {
        if (over) return 0
        val a0 = active ?: return 0
        var a = a0
        var steps = 0
        while (true) {
            val moved = tryMove(a, Coord(0, 1)) ?: break
            a = moved
            steps++
        }
        active = a
        // Standard soft drop scoring: 1 point per cell
        if (steps > 0) {
            scoreState = scoreState.copy(score = scoreState.score + steps)
        }
        return steps
    }

    // PUBLIC_INTERFACE
    fun hardDrop(): Int {
        if (over) return 0
        val a0 = active ?: return 0
        var a = a0
        var steps = 0
        while (true) {
            val moved = tryMove(a, Coord(0, 1)) ?: break
            a = moved
            steps++
        }
        active = a
        // Standard hard drop: 2 points per cell
        if (steps > 0) {
            scoreState = scoreState.copy(score = scoreState.score + steps * 2)
        }
        lockActiveAndProceed()
        return steps
    }

    // PUBLIC_INTERFACE
    fun moveLeft(): Boolean = moveHorizontal(-1)

    // PUBLIC_INTERFACE
    fun moveRight(): Boolean = moveHorizontal(1)

    // PUBLIC_INTERFACE
    fun rotateCW(): Boolean = rotate(apply = Rotation::cw)

    // PUBLIC_INTERFACE
    fun rotateCCW(): Boolean = rotate(apply = Rotation::ccw)

    // PUBLIC_INTERFACE
    fun hold(): Boolean {
        if (!enableHold || !canHold || over) return false
        val current = active ?: return false

        val incomingType = resolveHoldIncoming(current.type)

        // Prevent consecutive hold until piece is locked
        canHold = false

        val spawn = spawnPosition(incomingType)
        return if (!board.collides(spawn.blocks())) {
            active = spawn
            true
        } else {
            // If the incoming piece cannot spawn, the game is over
            over = true
            active = null
            false
        }
    }

    // PUBLIC_INTERFACE
    fun isGameOver(): Boolean = over

    // -------- Internal mechanics --------

    private fun moveHorizontal(dx: Int): Boolean {
        if (over) return false
        val a = active ?: return false
        val moved = tryMove(a, Coord(dx, 0)) ?: return false
        active = moved
        return true
    }

    private fun rotate(apply: (Rotation) -> Rotation): Boolean {
        if (over) return false
        val a = active ?: return false
        val to = apply(a.rotation)
        val kicks = SRS.kicks(a.type, a.rotation, to)
        for (k in kicks) {
            val rotated = a.copy(rotation = to, origin = a.origin + k)
            if (!board.collides(rotated.blocks())) {
                active = rotated
                return true
            }
        }
        return false
    }

    private fun tryMove(state: PieceState, delta: Coord): PieceState? {
        val next = state.copy(origin = state.origin + delta)
        return if (!board.collides(next.blocks())) next else null
    }

    private fun lockActiveAndProceed() {
        val a = active ?: return
        // Place blocks onto board
        var b = board
        for (c in a.blocks()) {
            // Ignore blocks above the board (y < 0) on lock to prevent premature game over
            if (c.y < 0) continue
            if (b.inBounds(c)) {
                b = b.set(c.x, c.y, BoardCell(true, a.type))
            } else {
                // If block is below or outside horizontal bounds, it's a game over
                over = true
                active = null
                board = b
                return
            }
        }
        // Clear lines and score
        val (cleared, newBoard) = clearFullLines(b)
        board = newBoard
        if (cleared > 0) {
            scoreState = scoreStateAfterClear(scoreState, cleared)
        }
        // Next piece
        canHold = true
        spawnNext()
    }

    private fun spawnNext() {
        ensureQueue(5)
        val type = nextFromQueue()
        val spawn = spawnPosition(type)
        if (board.collides(spawn.blocks())) {
            over = true
            active = null
        } else {
            active = spawn
        }
    }

    private fun nextFromQueue(): TetrominoType {
        if (queue.isEmpty()) ensureQueue(7)
        return queue.removeFirst()
    }

    private fun ensureQueue(minSize: Int) {
        while (queue.size < minSize) {
            // push 7-bag
            repeat(7) { queue.addLast(rng.next()) }
        }
    }

    private fun spawnPosition(type: TetrominoType): PieceState {
        // Spawn near the horizontal center; y at -1 allows gravity/ticks to enter smoothly
        val origin = Coord(width / 2, -1)
        return PieceState(type, Rotation.SPAWN, origin)
    }

    private fun clearFullLines(b: BoardState): Pair<Int, BoardState> {
        val rowsToKeep = mutableListOf<List<BoardCell>>()
        var cleared = 0
        for (y in 0 until b.height) {
            var full = true
            for (x in 0 until b.width) {
                if (!b.get(x, y).filled) {
                    full = false
                    break
                }
            }
            if (!full) {
                val row = (0 until b.width).map { x -> b.get(x, y) }
                rowsToKeep.add(row)
            } else {
                cleared++
            }
        }
        if (cleared == 0) return 0 to b
        // Create new grid: prepend 'cleared' empty rows at top
        val emptyRow = List(b.width) { BoardCell(false, null) }
        val newRows = MutableList(cleared) { emptyRow } + rowsToKeep
        val newGrid = newRows.flatten()
        return cleared to b.copy(grid = newGrid)
    }

    private fun scoreStateAfterClear(prev: ScoreState, cleared: Int): ScoreState {
        // Standard Tetris line clear scoring (base values multiplied by current level).
        val base = when (cleared) {
            1 -> 100 // single
            2 -> 300 // double
            3 -> 500 // triple
            4 -> 800 // tetris
            else -> 0
        }
        val newLines = prev.totalLines + cleared
        // Level increases after each 10 lines cleared total
        val newLevel = 1 + (newLines / 10)
        // Apply multiplier with the updated level
        val add = base * newLevel
        return ScoreState(
            score = prev.score + add,
            level = newLevel,
            totalLines = newLines
        )
    }

    /** Resolve which piece should become active after a hold operation. */
    private fun resolveHoldIncoming(currentType: TetrominoType): TetrominoType {
        return if (hold == null) {
            // First hold: stash current, pull next from queue
            hold = currentType
            nextFromQueue()
        } else {
            // Swap with held piece without consuming queue
            val fromHold = hold!!
            hold = currentType
            fromHold
        }
    }
}
