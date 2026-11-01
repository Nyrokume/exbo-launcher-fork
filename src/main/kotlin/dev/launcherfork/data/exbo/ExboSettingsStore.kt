package dev.launcherfork.data.exbo

import dev.launcherfork.data.local.SettingsFile
import dev.launcherfork.domain.ExitMode
import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.P2pMode
import dev.launcherfork.domain.RamOptions
import dev.launcherfork.domain.SettingsStore
import dev.launcherfork.domain.ServerBlockState
import dev.launcherfork.stalzone.GameOptions
import dev.launcherfork.stalzone.ServerBlockCodec
import dev.launcherfork.stalzone.StalzoneDesk
import dev.launcherfork.ui.motion.Motion
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * Settings of the new front, kept in `~/.launcher-fork/settings.properties`.
 * On the first run the official `runtime/settings.json` is read and never written.
 * A stored RAM choice is also written into the active JVM profile, so the wrapper's heap matches the bar.
 */
class ExboSettingsStore(
    initial: LauncherSettings = LauncherSettings(),
    private val desk: StalzoneDesk,
    private val file: Path,
    private val exboJson: Path,
    private val totalRamMb: Long,
) : SettingsStore {
    @Volatile
    private var current: LauncherSettings = initial

    init {
        val props = SettingsFile.load(file)
        val imported = if (!hasOwnKeys(props)) readOfficial(exboJson, totalRamMb, initial.language) else null
        current = fromProperties(props, imported ?: initial)
        if (imported != null) persist(current)
        publishHeap(current)
    }

    override fun load(): LauncherSettings = current

    override fun save(settings: LauncherSettings) {
        current = settings
        persist(settings)
        publishHeap(settings)
    }

    /** Whole GB of a stored choice, clamped the same way as the bar. No stored choice leaves the profile alone. */
    private fun publishHeap(settings: LauncherSettings) {
        val mb = settings.ramMb ?: return
        try {
            desk.syncHeap(RamOptions.fromMb(mb, totalRamMb))
        } catch (_: Exception) {
            // the bar still keeps the value
        }
    }

    private fun readOfficial(path: Path, total: Long, language: String): LauncherSettings? {
        if (!Files.isRegularFile(path)) return null
        return try {
            importExboSettings(Files.readString(path), total, language)
        } catch (_: Exception) {
            null
        }
    }

    private fun persist(settings: LauncherSettings) {
        SettingsFile.write(file, "language", settings.language)
        settings.ramMb?.let { SettingsFile.write(file, "ramMb", it.toString()) }
        SettingsFile.write(file, "gameFov", GameOptions.clampFov(settings.gameFov).toString())
        SettingsFile.write(file, "gameShadowsOff", settings.gameShadowsOff.toString())
        SettingsFile.write(file, Motion.SETTINGS_KEY, Motion.settingValue(settings.animations))
        SettingsFile.write(file, "startOnDownloaded", settings.startOnDownloaded.toString())
        SettingsFile.write(file, "exitMode", settings.exitMode.name)
        SettingsFile.write(file, "runOnStartUp", settings.runOnStartUp.toString())
        SettingsFile.write(file, "defenderHint", settings.defenderHint.toString())
        SettingsFile.write(file, "hideToTray", settings.hideToTray.toString())
        SettingsFile.write(file, "p2pMode", settings.p2pMode.name)
        SettingsFile.write(file, "downloadLimitKb", settings.downloadLimitKb.toString())
        SettingsFile.write(file, "uploadLimitKb", settings.uploadLimitKb.toString())
        SettingsFile.write(file, "tunnel", settings.tunnel)
        SettingsFile.write(file, "sbMode", ServerBlockCodec.readMode(settings.serverBlock.mode))
        SettingsFile.write(file, "sbRegion", ServerBlockCodec.readRegion(settings.serverBlock.region))
        SettingsFile.write(file, "sbBlocked", ServerBlockCodec.writeBlocked(settings.serverBlock.blocked))
        SettingsFile.write(file, "sbPings", ServerBlockCodec.writePings(settings.serverBlock.pings))
        SettingsFile.write(file, "sbPreferred", ServerBlockCodec.writePreferred(settings.serverBlock.preferredByPool))
    }

    private fun hasOwnKeys(props: Properties): Boolean =
        props.getProperty("ramMb") != null ||
            props.getProperty("exitMode") != null ||
            props.getProperty("language") != null ||
            props.getProperty("startOnDownloaded") != null

    private fun fromProperties(props: Properties, fallback: LauncherSettings): LauncherSettings {
        fun bool(key: String, default: Boolean) = when (props.getProperty(key)) {
            "true" -> true
            "false" -> false
            else -> default
        }
        val animations = props.getProperty(Motion.SETTINGS_KEY)?.let { Motion.parseSetting(it) } ?: fallback.animations
        return fallback.copy(
            language = props.getProperty("language") ?: fallback.language,
            ramMb = props.getProperty("ramMb")?.toIntOrNull() ?: fallback.ramMb,
            gameFov = GameOptions.clampFov(props.getProperty("gameFov")?.toDoubleOrNull() ?: fallback.gameFov),
            gameShadowsOff = bool("gameShadowsOff", fallback.gameShadowsOff),
            animations = animations,
            startOnDownloaded = bool("startOnDownloaded", fallback.startOnDownloaded),
            exitMode = props.getProperty("exitMode")?.let { runCatching { ExitMode.valueOf(it) }.getOrNull() } ?: fallback.exitMode,
            runOnStartUp = bool("runOnStartUp", fallback.runOnStartUp),
            defenderHint = bool("defenderHint", fallback.defenderHint),
            hideToTray = bool("hideToTray", fallback.hideToTray),
            p2pMode = props.getProperty("p2pMode")?.let { runCatching { P2pMode.valueOf(it) }.getOrNull() } ?: fallback.p2pMode,
            downloadLimitKb = props.getProperty("downloadLimitKb")?.toIntOrNull() ?: fallback.downloadLimitKb,
            uploadLimitKb = props.getProperty("uploadLimitKb")?.toIntOrNull() ?: fallback.uploadLimitKb,
            tunnel = props.getProperty("tunnel") ?: fallback.tunnel,
            serverBlock = ServerBlockState(
                blocked = props.getProperty("sbBlocked")?.let(ServerBlockCodec::readBlocked) ?: fallback.serverBlock.blocked,
                region = ServerBlockCodec.readRegion(props.getProperty("sbRegion") ?: fallback.serverBlock.region),
                pings = props.getProperty("sbPings")?.let(ServerBlockCodec::readPings) ?: fallback.serverBlock.pings,
                preferredByPool = props.getProperty("sbPreferred")?.let(ServerBlockCodec::readPreferred)
                    ?: fallback.serverBlock.preferredByPool,
                mode = ServerBlockCodec.readMode(props.getProperty("sbMode") ?: fallback.serverBlock.mode),
            ),
        )
    }
}
