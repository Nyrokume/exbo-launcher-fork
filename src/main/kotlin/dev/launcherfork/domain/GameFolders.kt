package dev.launcherfork.domain

import java.nio.file.Path

/**
 * Where a game client lives: `<LauncherSettings.gameRoot>/<server id>`. The ONE way the UI gets a folder to hand to
 * [FolderOpener]; the id must be a plain folder name (no separators, no "." / ".."), so the result always stays
 * inside the game root.
 */
object GameFolders {
    private val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")

    fun dirOf(gameRoot: Path, server: Server): Path {
        require(SAFE_ID.matches(server.id) && server.id != "." && server.id != "..") { "unsafe server id: ${server.id}" }
        val root = gameRoot.toAbsolutePath().normalize()
        val dir = root.resolve(server.id).normalize()
        require(dir.parent == root) { "game folder escapes the game root: $dir" }
        return dir
    }

    fun dirOf(settings: LauncherSettings, server: Server): Path = dirOf(settings.gameRoot, server)
}
