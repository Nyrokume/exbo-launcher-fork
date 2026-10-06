package dev.launcherfork.domain

import java.nio.file.Files
import java.nio.file.Path

/** What «Открыть папку игры» may hand to the file manager ([GameFolders.folderToOpen]). */
sealed interface FolderTarget {
    /** Open this existing folder (the requested one or its nearest existing parent, never above the game root). */
    data class Open(val dir: Path) : FolderTarget
    /** Neither the folder nor anything up to the game root exists: open nothing. */
    data class Missing(val root: Path) : FolderTarget
    /** The requested path is not under the game root (e.g. through ".."): refused. */
    data class OutsideRoot(val dir: Path, val root: Path) : FolderTarget
}

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

    /**
     * The folder to open for [dir]: [dir] itself if it exists, else its nearest existing parent, but the walk never
     * goes above [gameRoot] (both compared absolute + normalized, `Path.startsWith`, so `EXBO-evil` is not inside
     * `EXBO`). A [dir] outside the root is refused; without an existing root nothing is opened.
     */
    fun folderToOpen(dir: Path, gameRoot: Path, isDirectory: (Path) -> Boolean = { Files.isDirectory(it) }): FolderTarget {
        val root = gameRoot.toAbsolutePath().normalize()
        val target = dir.toAbsolutePath().normalize()
        if (!target.startsWith(root)) return FolderTarget.OutsideRoot(target, root)
        val existing = generateSequence(target) { p -> p.parent?.takeIf { it.startsWith(root) } }.firstOrNull(isDirectory)
        return if (existing != null) FolderTarget.Open(existing) else FolderTarget.Missing(root)
    }
}
