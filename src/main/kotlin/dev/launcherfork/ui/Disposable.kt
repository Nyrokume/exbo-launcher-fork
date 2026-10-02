package dev.launcherfork.ui

/** Controllers that hold timers or listeners on session-scoped state implement this; called when the screen is left. */
fun interface Disposable {
    fun dispose()
}
