package dev.launcherfork.data.exbo

import dev.launcherfork.domain.ExitMode
import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.P2pMode

/**
 * First-run read of the official `runtime/settings.json`.
 * `ram` is the 0..1 fraction the official heap formula uses: `(clamp(totalMb - 1024, 0, 3584) * fraction) + 512`.
 * `p2p` and `exit_new` follow the official radio order. This function does not touch the file.
 */
internal fun importExboSettings(json: String, totalRamMb: Long, language: String): LauncherSettings {
    val obj = parseJson(json) as? Json.Obj ?: return LauncherSettings(language = language)
    return LauncherSettings(
        runOnStartUp = obj.bool("run_on_start") ?: false,
        startOnDownloaded = obj.bool("start_on_downloaded") ?: true,
        hideToTray = obj.bool("tray") ?: true,
        defenderHint = obj.bool("defender") ?: true,
        p2pMode = p2pMode(obj.int("p2p")),
        exitMode = exitMode(obj.int("exit_new") ?: obj.int("exit")),
        ramMb = obj.double("ram")?.let { exboHeapMb(it, totalRamMb) },
        downloadLimitKb = obj.int("download")?.coerceAtLeast(0) ?: 0,
        uploadLimitKb = obj.int("upload")?.coerceAtLeast(0) ?: 0,
        language = language,
    )
}

/** Official x64 heap: base is physical MB minus 1024, clamped to 0..3584, then `base * fraction + 512`. */
internal fun exboHeapMb(fraction: Double, totalRamMb: Long): Int {
    val base = (totalRamMb - 1024L).coerceIn(0L, 3584L)
    return (base * fraction.coerceIn(0.0, 1.0)).toInt() + 512
}

private fun p2pMode(value: Int?): P2pMode = when (value) {
    0 -> P2pMode.ALWAYS
    2 -> P2pMode.NEVER
    else -> P2pMode.EXCEPT_MOBILE
}

private fun exitMode(value: Int?): ExitMode = when (value) {
    0 -> ExitMode.ON_GAME_START
    1 -> ExitMode.ON_GAME_EXIT
    else -> ExitMode.NEVER
}
