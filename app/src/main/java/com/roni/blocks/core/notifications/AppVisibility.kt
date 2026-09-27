package com.roni.blocks.core.notifications

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether any part of the app is currently on screen. Used to keep notifications silent while
 * the user is already looking at the same information.
 */
interface AppVisibility {
    val isForeground: Boolean
}

@Singleton
class ProcessAppVisibility @Inject constructor() : AppVisibility {
    override val isForeground: Boolean
        get() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
}
