package dev.launcherfork.ui.components

import javafx.application.Platform

/** Run on the FX thread (callbacks from services come from background threads). */
fun fx(block: () -> Unit) {
    if (Platform.isFxApplicationThread()) block() else Platform.runLater(block)
}

/** Run a blocking service call off the FX thread and deliver the result on it. */
fun <T> background(task: () -> T, onResult: (T) -> Unit) {
    Thread({
        val r = task()
        fx { onResult(r) }
    }, "ui-background").apply { isDaemon = true }.start()
}

fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (i == 0) "$bytes B" else String.format("%.1f %s", v, units[i])
}
