package com.example.tetris_android_tv_frontend.game

import kotlin.random.Random

/**
 * Deterministic 7-bag generator for Tetromino sequences.
 * Uses a seedable Random instance to ensure reproducibility in tests.
 */
// PUBLIC_INTERFACE
class SevenBagRNG(seed: Long) {
    private var random: Random = Random(seed)
    private val bag = ArrayDeque<TetrominoType>()

    /** Draws and returns the next TetrominoType from the 7-bag. */
    fun next(): TetrominoType {
        if (bag.isEmpty()) refill()
        return bag.removeFirst()
    }

    /** Re-seeds the generator and resets current bag state. */
    fun reseed(seed: Long) {
        random = Random(seed)
        bag.clear()
    }

    /** Ensures at least one full shuffled set of 7 pieces is available. */
    private fun refill() {
        val list = TetrominoType.values().toMutableList()
        list.shuffle(random)
        bag.addAll(list)
    }
}
