package com.example.tetris_android_tv_frontend.game.runtime

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.example.tetris_android_tv_frontend.game.GameSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * PUBLIC_INTERFACE
 * Adapters to bridge Flows from GameLoop to LiveData for traditional Android Views.
 */
// PUBLIC_INTERFACE
fun StateFlow<GameSnapshot>.toLiveData(scope: CoroutineScope? = null): LiveData<GameSnapshot> {
    return if (scope != null) this.asLiveData(context = Dispatchers.Main.immediate)
    else this.asLiveData()
}

// PUBLIC_INTERFACE
fun <T> SharedFlow<T>.toLiveData(): LiveData<T> {
    // For one-shot events, asLiveData will replay on active observers depending on implementation.
    return this.asLiveData(context = Dispatchers.Main.immediate)
}
