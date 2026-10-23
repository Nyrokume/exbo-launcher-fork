package dev.launcherfork

import dev.launcherfork.data.exbo.bakeRunCommand
import dev.launcherfork.data.exbo.parseRunArgs
import dev.launcherfork.data.exbo.parseServerCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.Base64

class ExboRunCommandTest {
    @Test
    fun placeholdersAreSubstitutedSlashesFlippedAndTheLauncherTokenIsAbsent() {
        val token = "launcher-token-should-not-appear"
        val login = "Player"
        val session = "game-session"
        val client = Path.of("C:/games/stalcraft")
        val args = parseRunArgs(
            "\"<java>/java/bin/game.exe\" --username <usr> --session <sess> -Xmx<mem>m --cd <cd> --dlogin <dlogin> dir/sub",
        )
        val baked = bakeRunCommand(args, client, login, session, 4096, archFolder = "win64")
        val joined = baked.joinToString("\n")
        assertFalse(joined.contains(token))
        assertFalse(joined.contains("<"))
        assertEquals(login, baked[2])
        assertEquals(session, baked[4])
        assertTrue(baked[0].contains("win64"))
        assertTrue(baked[0].contains("\\"))
        assertFalse(baked[0].contains("/"))
        assertTrue(baked.any { it.contains("4096") })
        assertTrue(baked.any { it == "dir\\sub" })
        val cd = baked.first { it.contains("stalcraft") && !it.contains("win64") }
        assertFalse(cd.contains("/"))
        val dlogin = Base64.getEncoder().encodeToString(login.toByteArray(Charsets.UTF_16))
        assertTrue(baked.any { it == dlogin })
        assertFalse(dlogin.contains(token))
    }

    @Test
    fun catalogKeepsRunCommandAndVersionOffTheServerCard() {
        val body = """
            [{"id":"stalcraft","caption":"STALCRAFT","onlineCurrent":1,"runCommand":"<java>/bin/game","version":"abc"}]
        """.trim()
        val catalog = parseServerCatalog(body, Path.of("C:/unused"))
        assertEquals("<java>/bin/game", catalog.runCommands["stalcraft"])
        assertEquals("abc", catalog.versions["stalcraft"])
        assertEquals("stalcraft", catalog.servers.single().id)
    }
}
