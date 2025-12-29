package com.example.tetris_android_tv_frontend.game

/**
 * Core Tetris data models and interfaces.
 * These are UI-agnostic and suitable for unit testing.
 */

// PUBLIC_INTERFACE
data class Coord(val x: Int, val y: Int) {
    /** Utility add function for coordinates. */
    operator fun plus(other: Coord): Coord = Coord(x + other.x, y + other.y)
}

// PUBLIC_INTERFACE
enum class TetrominoType {
    I, O, T, S, Z, J, L
}

// PUBLIC_INTERFACE
enum class Rotation {
    SPAWN, R90, R180, R270;

    /** Returns next rotation state when rotating clockwise. */
    fun cw(): Rotation = when (this) {
        SPAWN -> R90
        R90 -> R180
        R180 -> R270
        R270 -> SPAWN
    }

    /** Returns next rotation state when rotating counter-clockwise. */
    fun ccw(): Rotation = when (this) {
        SPAWN -> R270
        R90 -> SPAWN
        R180 -> R90
        R270 -> R180
    }
}

// PUBLIC_INTERFACE
data class PieceState(
    val type: TetrominoType,
    val rotation: Rotation,
    val origin: Coord // origin on board grid (column, row)
) {
    /** Returns blocks occupied by this piece on the board for current rotation/origin. */
    fun blocks(): List<Coord> = TetrominoShapes.blocks(type, rotation).map { origin + it }
}

// PUBLIC_INTERFACE
data class BoardCell(
    val filled: Boolean,
    val type: TetrominoType? = null
)

// PUBLIC_INTERFACE
data class BoardState(
    val width: Int,
    val height: Int,
    val grid: List<BoardCell>,
) {
    init {
        require(grid.size == width * height) { "Grid size must equal width*height" }
    }

    /** Returns the cell at (x,y). Top-left is (0,0). */
    fun get(x: Int, y: Int): BoardCell = grid[y * width + x]

    /** Updates the cell at (x,y) and returns a new BoardState. */
    fun set(x: Int, y: Int, cell: BoardCell): BoardState {
        val idx = y * width + x
        val newGrid = grid.toMutableList()
        newGrid[idx] = cell
        return copy(grid = newGrid)
    }

    /** Checks whether coordinate is within board bounds. */
    fun inBounds(c: Coord): Boolean = c.x in 0 until width && c.y in 0 until height

    /** Returns true if any cell at provided coordinates is filled or out of bounds.
     * Production behavior:
     * - Horizontal bounds are strict (collide when outside [0,width)).
     * - Vertical: allow y < 0 (spawn area above board) to not collide.
     * - Collide when y >= height (below board).
     * - Collide when y within [0,height) and the target cell is filled.
     */
    fun collides(coords: List<Coord>): Boolean {
        for (c in coords) {
            if (c.x !in 0 until width) return true
            if (c.y >= height) return true
            if (c.y >= 0 && get(c.x, c.y).filled) return true
        }
        return false
    }
}

/**
 * Defines tetromino relative block coordinates for each rotation using SRS orientation.
 * Coordinates are relative to an origin (column, row).
 */
internal object TetrominoShapes {
    // Relative block layouts per rotation (SRS-like orientations)
    // Shapes are given as relative coordinates from an origin. Origin choice
    // here is consistent and combined with SRS wall-kick tables for rotation.
    private val I = mapOf(
        Rotation.SPAWN to listOf(Coord(-1, 0), Coord(0, 0), Coord(1, 0), Coord(2, 0)),
        Rotation.R90 to listOf(Coord(1, -1), Coord(1, 0), Coord(1, 1), Coord(1, 2)),
        Rotation.R180 to listOf(Coord(-1, 1), Coord(0, 1), Coord(1, 1), Coord(2, 1)),
        Rotation.R270 to listOf(Coord(0, -1), Coord(0, 0), Coord(0, 1), Coord(0, 2)),
    )
    private val O = mapOf(
        Rotation.SPAWN to listOf(Coord(0, 0), Coord(1, 0), Coord(0, 1), Coord(1, 1)),
        Rotation.R90 to listOf(Coord(0, 0), Coord(1, 0), Coord(0, 1), Coord(1, 1)),
        Rotation.R180 to listOf(Coord(0, 0), Coord(1, 0), Coord(0, 1), Coord(1, 1)),
        Rotation.R270 to listOf(Coord(0, 0), Coord(1, 0), Coord(0, 1), Coord(1, 1)),
    )
    private val T = mapOf(
        Rotation.SPAWN to listOf(Coord(-1, 0), Coord(0, 0), Coord(1, 0), Coord(0, 1)),
        Rotation.R90 to listOf(Coord(0, -1), Coord(0, 0), Coord(0, 1), Coord(1, 0)),
        Rotation.R180 to listOf(Coord(-1, 0), Coord(0, 0), Coord(1, 0), Coord(0, -1)),
        Rotation.R270 to listOf(Coord(0, -1), Coord(0, 0), Coord(0, 1), Coord(-1, 0)),
    )
    private val S = mapOf(
        Rotation.SPAWN to listOf(Coord(0, 0), Coord(1, 0), Coord(-1, 1), Coord(0, 1)),
        Rotation.R90 to listOf(Coord(0, -1), Coord(0, 0), Coord(1, 0), Coord(1, 1)),
        Rotation.R180 to listOf(Coord(0, 0), Coord(1, 0), Coord(-1, 1), Coord(0, 1)),
        Rotation.R270 to listOf(Coord(-1, -1), Coord(-1, 0), Coord(0, 0), Coord(0, 1)),
    )
    private val Z = mapOf(
        Rotation.SPAWN to listOf(Coord(-1, 0), Coord(0, 0), Coord(0, 1), Coord(1, 1)),
        Rotation.R90 to listOf(Coord(1, -1), Coord(0, 0), Coord(1, 0), Coord(0, 1)),
        Rotation.R180 to listOf(Coord(-1, 0), Coord(0, 0), Coord(0, 1), Coord(1, 1)),
        Rotation.R270 to listOf(Coord(0, -1), Coord(-1, 0), Coord(0, 0), Coord(-1, 1)),
    )
    private val J = mapOf(
        Rotation.SPAWN to listOf(Coord(-1, 0), Coord(0, 0), Coord(1, 0), Coord(-1, 1)),
        Rotation.R90 to listOf(Coord(0, -1), Coord(0, 0), Coord(0, 1), Coord(1, -1)),
        Rotation.R180 to listOf(Coord(-1, 0), Coord(0, 0), Coord(1, 0), Coord(1, -1)),
        Rotation.R270 to listOf(Coord(0, -1), Coord(0, 0), Coord(0, 1), Coord(-1, 1)),
    )
    private val L = mapOf(
        Rotation.SPAWN to listOf(Coord(-1, 0), Coord(0, 0), Coord(1, 0), Coord(1, 1)),
        Rotation.R90 to listOf(Coord(0, -1), Coord(0, 0), Coord(0, 1), Coord(1, 1)),
        Rotation.R180 to listOf(Coord(-1, -1), Coord(-1, 0), Coord(0, 0), Coord(1, 0)),
        Rotation.R270 to listOf(Coord(-1, -1), Coord(0, -1), Coord(0, 0), Coord(0, 1)),
    )

    fun blocks(type: TetrominoType, rotation: Rotation): List<Coord> {
        val m = when (type) {
            TetrominoType.I -> I
            TetrominoType.O -> O
            TetrominoType.T -> T
            TetrominoType.S -> S
            TetrominoType.Z -> Z
            TetrominoType.J -> J
            TetrominoType.L -> L
        }
        return m[rotation] ?: error("Missing rotation mapping")
    }
}

/**
 * SRS wall kick data minimal set for standard Tetris guideline.
 * For simplicity we include a commonly used kick table.
 * Coordinates are offsets to try (dx, dy) when rotating between specific states.
 */
internal object SRS {
    // For J, L, S, T, Z pieces
    private val JLSTZ_KICKS = mapOf(
        Pair(Rotation.SPAWN, Rotation.R90) to listOf(Coord(0, 0), Coord(-1, 0), Coord(-1, -1), Coord(0, 2), Coord(-1, 2)),
        Pair(Rotation.R90, Rotation.SPAWN) to listOf(Coord(0, 0), Coord(1, 0), Coord(1, 1), Coord(0, -2), Coord(1, -2)),

        Pair(Rotation.R90, Rotation.R180) to listOf(Coord(0, 0), Coord(1, 0), Coord(1, -1), Coord(0, 2), Coord(1, 2)),
        Pair(Rotation.R180, Rotation.R90) to listOf(Coord(0, 0), Coord(-1, 0), Coord(-1, 1), Coord(0, -2), Coord(-1, -2)),

        Pair(Rotation.R180, Rotation.R270) to listOf(Coord(0, 0), Coord(1, 0), Coord(1, 1), Coord(0, -2), Coord(1, -2)),
        Pair(Rotation.R270, Rotation.R180) to listOf(Coord(0, 0), Coord(-1, 0), Coord(-1, -1), Coord(0, 2), Coord(-1, 2)),

        Pair(Rotation.R270, Rotation.SPAWN) to listOf(Coord(0, 0), Coord(-1, 0), Coord(-1, 1), Coord(0, -2), Coord(-1, -2)),
        Pair(Rotation.SPAWN, Rotation.R270) to listOf(Coord(0, 0), Coord(1, 0), Coord(1, -1), Coord(0, 2), Coord(1, 2)),
    )

    // For I piece
    private val I_KICKS = mapOf(
        Pair(Rotation.SPAWN, Rotation.R90) to listOf(Coord(0, 0), Coord(-2, 0), Coord(1, 0), Coord(-2, -1), Coord(1, 2)),
        Pair(Rotation.R90, Rotation.SPAWN) to listOf(Coord(0, 0), Coord(2, 0), Coord(-1, 0), Coord(2, 1), Coord(-1, -2)),

        Pair(Rotation.R90, Rotation.R180) to listOf(Coord(0, 0), Coord(-1, 0), Coord(2, 0), Coord(-1, 2), Coord(2, -1)),
        Pair(Rotation.R180, Rotation.R90) to listOf(Coord(0, 0), Coord(1, 0), Coord(-2, 0), Coord(1, -2), Coord(-2, 1)),

        Pair(Rotation.R180, Rotation.R270) to listOf(Coord(0, 0), Coord(2, 0), Coord(-1, 0), Coord(2, 1), Coord(-1, -2)),
        Pair(Rotation.R270, Rotation.R180) to listOf(Coord(0, 0), Coord(-2, 0), Coord(1, 0), Coord(-2, -1), Coord(1, 2)),

        Pair(Rotation.R270, Rotation.SPAWN) to listOf(Coord(0, 0), Coord(1, 0), Coord(-2, 0), Coord(1, -2), Coord(-2, 1)),
        Pair(Rotation.SPAWN, Rotation.R270) to listOf(Coord(0, 0), Coord(-1, 0), Coord(2, 0), Coord(-1, 2), Coord(2, -1)),
    )

    fun kicks(type: TetrominoType, from: Rotation, to: Rotation): List<Coord> {
        return if (type == TetrominoType.I) {
            I_KICKS[Pair(from, to)] ?: listOf(Coord(0, 0))
        } else if (type == TetrominoType.O) {
            // O piece typically has no kicks (center-stable)
            listOf(Coord(0, 0))
        } else {
            JLSTZ_KICKS[Pair(from, to)] ?: listOf(Coord(0, 0))
        }
    }
}
