package dev.launcherfork

import dev.launcherfork.data.exbo.ExboApi
import dev.launcherfork.data.exbo.ExboHosts
import dev.launcherfork.data.exbo.ExboTransportException
import dev.launcherfork.data.exbo.exboBaseIsTrustedHttps
import dev.launcherfork.data.exbo.exboHttpFailure
import dev.launcherfork.data.exbo.exboJoinPath
import dev.launcherfork.data.exbo.exboListPath
import dev.launcherfork.data.exbo.exboMailCodePath
import dev.launcherfork.data.exbo.exboPasswordHashPath
import dev.launcherfork.data.exbo.exboPasswordPath
import dev.launcherfork.data.exbo.exboPublicError
import dev.launcherfork.data.exbo.exboSecretPath
import dev.launcherfork.data.exbo.exboSessionPath
import dev.launcherfork.data.exbo.exboTorrentPath
import dev.launcherfork.data.exbo.exboTotpPath
import dev.launcherfork.data.exbo.exboTransportFailure
import dev.launcherfork.data.exbo.redactExboUrl
import dev.launcherfork.stalzone.WindowsRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.nio.file.Files

class ExboTransportSecurityTest {
    @Test
    fun credentialHostsAreTheOfficialTlsListAndCleartextIsRejected() {
        assertEquals(
            listOf(
                "https://launcher.stalcraft.net:443",
                "https://launcher.stalcraftx.ru:443",
                "https://launcher.stalcraft-tech.net:443",
            ),
            ExboHosts.credential,
        )
        assertEquals(
            listOf(
                "http://launcher.stalcraft.net:80",
                "http://launcher.stalcraftx.ru:80",
                "http://launcher.stalcraft-tech.net:80",
            ),
            ExboHosts.cleartext,
        )
        assertTrue(ExboHosts.credential.all { exboBaseIsTrustedHttps(it) })
        assertTrue(ExboHosts.cleartext.none { exboBaseIsTrustedHttps(it) })
        assertFalse(exboBaseIsTrustedHttps("https://user:pw@launcher.stalcraft.net:443"))
        assertFalse(exboBaseIsTrustedHttps("https://launcher.stalcraft.net:443/auth?pass=x"))
        assertFalse(exboBaseIsTrustedHttps("https://launcher.stalcraft.net.evil:443"))
        assertFalse(exboBaseIsTrustedHttps("https://127.0.0.1:9"))
    }

    @Test
    fun authQueriesStayInTheOfficialShape() {
        val paths = listOf(
            exboPasswordPath("a b", "p&w", "abc", "ff", "id"),
            exboPasswordHashPath("a b", "p&w", "\$2y\$10\$abc", "abc", "ff", "id"),
            exboSecretPath("name", "10000", "abc", "ff", "id"),
            exboSessionPath("name", "sess", "abc", "ff", "id"),
            exboTotpPath("name", "338410", "id"),
            exboMailCodePath("name", "268419249", "ff", "id"),
            exboListPath("tok", "name"),
            exboJoinPath("tok", "name"),
            exboTorrentPath("tok", "name", "stalcraft"),
        )
        assertTrue(paths.all { it.contains("pass=") || it.contains("answer=") || it.contains("session=") || it.contains("secret=") || it.contains("code=") || it.contains("token=") })
        assertTrue(paths.none { it.startsWith("http://") || it.startsWith("https://") })
    }

    @Test
    fun redactDropsUserInfoAndQuery() {
        val path = exboPasswordPath("a b", "p&w", "abc", "ff", "id")
        val shown = redactExboUrl("https://user:secret@launcher.stalcraft.net:443$path")
        assertEquals("https://launcher.stalcraft.net:443/auth", shown)
        assertFalse(shown.contains("p%26w"))
        assertFalse(shown.contains("secret"))
        assertFalse(shown.contains("?"))
        assertEquals(
            "https://exbo.ru/index.php",
            redactExboUrl("https://exbo.ru/index.php?do=register&token=abc"),
        )
        assertEquals("redacted", redactExboUrl("not a url"))
        assertEquals("", redactExboUrl("  "))
    }

    @Test
    fun errorsDropTheUrlAndKeepPlainStatusText() {
        val leaked = "Server returned HTTP response code: 500 for URL: " +
            "https://launcher.stalcraft.net/auth?pass=hunter2&token=abc"
        assertEquals("redacted", exboPublicError(leaked))
        assertEquals("redacted", ExboTransportException(leaked).message)
        assertEquals("IOException", exboTransportFailure(IOException(leaked)))
        assertEquals("http 503", ExboTransportException("http 503").message)
        assertEquals("encode=utf8", exboPublicError("encode=utf8"))
        assertEquals("redacted", exboPublicError("pass=hunter2"))
        assertEquals("redirect", exboTransportFailure(ExboTransportException("redirect")))
        assertEquals(250, exboPublicError("x".repeat(250)).length)
        assertEquals(400, exboPublicError("x".repeat(450)).length)
        assertNull(exboHttpFailure(200))
        assertEquals("redirect", exboHttpFailure(301))
        assertEquals("redirect", exboHttpFailure(302))
        assertEquals("http 400", exboHttpFailure(400))
        assertEquals("no status", exboHttpFailure(-1))
    }

    @Test
    fun cleartextAndUnlistedHostsAreNotContacted() {
        val root = Files.createTempDirectory("exbo-sec")
        ServerSocket(0).use { socket ->
            socket.soTimeout = 300
            val port = socket.localPort
            val api = ExboApi(root = root, registry = WindowsRegistry())
            val path = "/auth?pass=hunter2&token=sekret&hwid=ff"
            for (base in listOf("http://127.0.0.1:$port", "https://127.0.0.1:$port")) {
                val error = assertThrows<IllegalArgumentException> { api.request(base, path, "application/json") }
                val message = error.message.orEmpty()
                assertFalse(message.contains("hunter2"), base)
                assertFalse(message.contains("sekret"), base)
                assertFalse(message.contains("pass="), base)
                assertFalse(message.contains(path), base)
            }
            assertThrows<SocketTimeoutException> { socket.accept() }
        }
        root.toFile().deleteRecursively()
    }

    @Test
    fun aPathCannotSteerThePasswordAtAnotherHost() {
        val root = Files.createTempDirectory("exbo-sec-steer")
        ServerSocket(0).use { socket ->
            socket.soTimeout = 300
            val api = ExboApi(root = root, registry = WindowsRegistry())
            val error = assertThrows<ExboTransportException> {
                api.request(
                    ExboHosts.credential.first(),
                    "@127.0.0.1:${socket.localPort}/auth?pass=hunter2&token=sekret",
                    "application/json",
                )
            }
            val message = error.message.orEmpty()
            assertFalse(message.contains("hunter2"))
            assertFalse(message.contains("sekret"))
            assertFalse(message.contains("pass="))
            assertFalse(message.contains("://"))
            assertThrows<SocketTimeoutException> { socket.accept() }
        }
        root.toFile().deleteRecursively()
    }
}
