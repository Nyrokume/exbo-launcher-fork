package dev.launcherfork

import dev.launcherfork.data.mock.MockData
import dev.launcherfork.domain.GameFolders
import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.LinkPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** «Открыть папку игры» only ever opens <gameRoot>/<server id>; LinkOpener's policy keeps refusing file: URLs. */
class GameFoldersTest {
    private val root = Path.of("/home/player/Games/EXBO")
    private val server = MockData.servers.single()

    @Test
    fun gameFolderIsTheServerIdUnderTheGameRoot() {
        val folder = GameFolders.dirOf(root, server)
        assertEquals(root.toAbsolutePath().normalize().resolve(server.id).normalize(), folder)
        assertEquals(Path.of("/home/player/Games/EXBO/${server.id}").toAbsolutePath().normalize(), folder)
        val settings = LauncherSettings(gameRoot = Path.of("/data/../data/games"))
        assertEquals(Path.of("/data/games/${server.id}").toAbsolutePath().normalize(), GameFolders.dirOf(settings, server))
    }

    @Test
    fun unsafeIdsAreRejected() {
        listOf("..", ".", "../etc", "a/b", "a\\b", "/abs", "", " x", "x\u0000").forEach { id ->
            assertThrows(IllegalArgumentException::class.java, { GameFolders.dirOf(root, server.copy(id = id)) }, "id '$id'")
        }
    }

    @Test
    fun linkPolicyStillRejectsFolders() {
        val uri = GameFolders.dirOf(root, server).toUri().toString()
        assertFalse(LinkPolicy.isAllowed(uri), uri)
    }
}
