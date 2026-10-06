package dev.launcherfork

import dev.launcherfork.data.mock.MockData
import dev.launcherfork.domain.FolderTarget
import dev.launcherfork.domain.GameFolders
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * «Открыть папку игры» (Agent.Master's PR #8 review): the folder handed to the file manager is the game folder, or the
 * game root when the game is not installed yet, and NEVER anything above `settings.gameRoot` (no climbing to the home
 * folder or C:\); without an existing root nothing is opened.
 */
class FolderTargetTest {
    private val server = MockData.servers.single()

    @Test
    fun anExistingGameFolderOpensItself(@TempDir tmp: Path) {
        val root = Files.createDirectories(tmp.resolve("Games/EXBO"))
        val dir = Files.createDirectories(GameFolders.dirOf(root, server))
        assertEquals(FolderTarget.Open(dir.toAbsolutePath().normalize()), GameFolders.folderToOpen(dir, root))
    }

    @Test
    fun aMissingGameFolderOpensTheRootNotAnythingAbove(@TempDir tmp: Path) {
        val root = Files.createDirectories(tmp.resolve("Games/EXBO"))
        val dir = GameFolders.dirOf(root, server) // not created
        assertEquals(FolderTarget.Open(root.toAbsolutePath().normalize()), GameFolders.folderToOpen(dir, root))
        // a deeper missing path inside the root still stops at the root
        assertEquals(FolderTarget.Open(root.toAbsolutePath().normalize()), GameFolders.folderToOpen(dir.resolve("mods/cfg"), root))
        // an existing sub-folder on the way is the nearest one
        Files.createDirectories(dir)
        assertEquals(FolderTarget.Open(dir.toAbsolutePath().normalize()), GameFolders.folderToOpen(dir.resolve("mods/cfg"), root))
    }

    @Test
    fun aMissingRootOpensNothingEvenIfItsParentsExist(@TempDir tmp: Path) {
        // tmp (and the home folder, /) exist: the old parent walk would have opened one of them
        val root = tmp.resolve("Games/EXBO")
        val dir = GameFolders.dirOf(root, server)
        val r = GameFolders.folderToOpen(dir, root)
        assertTrue(r is FolderTarget.Missing, "got $r")
        assertEquals(root.toAbsolutePath().normalize(), (r as FolderTarget.Missing).root)
    }

    @Test
    fun pathsOutsideTheRootAreRefused(@TempDir tmp: Path) {
        val root = Files.createDirectories(tmp.resolve("Games/EXBO"))
        Files.createDirectories(tmp.resolve("Games/Other"))
        listOf(
            root.resolve(".."),
            root.resolve("../Other"),
            root.resolve("${server.id}/../../Other"),
            root.resolve("../EXBO-evil"), // a sibling sharing the root's name prefix: startsWith on Path, not on String
            tmp,
            tmp.root,
        ).forEach { dir ->
            val r = GameFolders.folderToOpen(dir, root)
            assertTrue(r is FolderTarget.OutsideRoot, "$dir: got $r")
        }
        Files.createDirectories(tmp.resolve("Games/EXBO-evil"))
        assertTrue(GameFolders.folderToOpen(root.resolve("../EXBO-evil"), root) is FolderTarget.OutsideRoot)
    }

    @Test
    fun relativeAndUnnormalizedPathsAreComparedNormalized(@TempDir tmp: Path) {
        val root = Files.createDirectories(tmp.resolve("Games/EXBO"))
        val dir = Files.createDirectories(root.resolve(server.id))
        val messyRoot = tmp.resolve("Games/./x/../EXBO")
        val messyDir = tmp.resolve("Games/EXBO/./${server.id}/a/..")
        assertEquals(FolderTarget.Open(dir.toAbsolutePath().normalize()), GameFolders.folderToOpen(messyDir, messyRoot))
        // the root itself is inside the root
        assertEquals(FolderTarget.Open(root.toAbsolutePath().normalize()), GameFolders.folderToOpen(root, root))
    }

    @Test
    fun theDirectoryCheckIsInjectable() {
        val root = Path.of("/games/EXBO").toAbsolutePath()
        val dir = root.resolve(server.id)
        val existing = setOf(root, Path.of("/games").toAbsolutePath(), Path.of("/").toAbsolutePath())
        assertEquals(FolderTarget.Open(root), GameFolders.folderToOpen(dir, root) { it in existing })
        assertTrue(GameFolders.folderToOpen(dir, root) { it != root && it in existing } is FolderTarget.Missing,
            "/games exists but is above the root")
    }
}
