package dev.launcherfork.ui.components

import javafx.application.Platform

/** Run on the FX thread (callbacks from services come from background threads). */
fun fx(block: () -> Unit) {
    if (Platform.isFxApplicationThread()) block() else Platform.runLater(block)
}

/**
 * Run a blocking service call off the FX thread and deliver its outcome on it.
 * Exceptions thrown by [task] never escape: they arrive as [Result.failure], so the caller can always
 * unlock the UI (hide the spinner, re-enable the form) and show an error.
 *
 * @param ui dispatcher for [onResult]; FX thread by default (tests pass a direct one).
 */
fun <T> background(
    task: () -> T,
    ui: (() -> Unit) -> Unit = ::fx,
    onResult: (Result<T>) -> Unit,
) {
    Thread({
        val r = runCatching(task)
        if (r.isFailure) System.err.println("[launcher-fork] background task failed: ${r.exceptionOrNull()}")
        ui { onResult(r) }
    }, "ui-background").apply { isDaemon = true }.start()
}

fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (i == 0) "$bytes B" else String.format("%.1f %s", v, units[i])
}
