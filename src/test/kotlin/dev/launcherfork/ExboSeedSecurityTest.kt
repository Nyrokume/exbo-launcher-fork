package dev.launcherfork

import dev.launcherfork.data.exbo.ExboTorrent
import dev.launcherfork.data.exbo.ExboTorrentFile
import dev.launcherfork.data.exbo.exboHttpsUrl
import dev.launcherfork.data.exbo.exboOpenSeed
import dev.launcherfork.data.exbo.exboSeedStatusRejection
import dev.launcherfork.data.exbo.exboSha1
import dev.launcherfork.data.exbo.httpsWebSeeds
import dev.launcherfork.data.exbo.parseServerCatalog
import dev.launcherfork.data.exbo.syncClientFiles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.readText

class ExboSeedSecurityTest {
    @Test
    fun httpsSeedsDropCleartextUserinfoAndDuplicates() {
        assertNull(exboHttpsUrl("http://cdn.example/files"))
        assertNull(exboHttpsUrl("https://user:secret@cdn.example/files"))
        assertNull(exboHttpsUrl("https:///missing-host/files"))
        assertEquals(
            listOf("https://cdn.example/a", "https://cdn.example/b"),
            httpsWebSeeds(
                listOf(
                    "http://cdn.example/clear",
                    " https://cdn.example/a ",
                    "https://user:secret@cdn.example/hidden",
                    "https://cdn.example/a",
                    "https://cdn.example/b",
                ),
            ),
        )
    }

    @Test
    fun catalogKeepsHttpsSeedsAndDropsCleartextArt() {
        val body = """
            [{"id":"stalcraft","caption":"STALZONE","icon":"http://cdn.example/icon.png","background":"bg.jpg","httpSeeds":["http://cdn.example/files","https://cdn.example/ok"],"version":"9"}]
        """.trim()
        val root = Files.createTempDirectory("exbo-seed-art")
        try {
            val catalog = parseServerCatalog(body, root)
            assertEquals(listOf("https://cdn.example/ok"), catalog.httpSeeds["stalcraft"])
            val server = catalog.servers.single()
            assertEquals("https://cdn.example/ok/bg.jpg", server.backgroundUri.toString())
            assertTrue(server.iconUri.toString().contains("game-stalzone.png"))
            assertFalse(server.iconUri.scheme.equals("http", ignoreCase = true))
            assertFalse(server.backgroundUri.scheme.equals("http", ignoreCase = true))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun seedStatusRejectsCleartextAndRedirects() {
        assertEquals("no seeds", exboSeedStatusRejection("http://cdn.example/a", 200))
        assertEquals("no seeds", exboSeedStatusRejection("https://user:pw@cdn.example/a", 200))
        assertEquals("no seeds", exboSeedStatusRejection("https://cdn.example/a", 302))
        assertEquals("http 404", exboSeedStatusRejection("https://cdn.example/a", 404))
        assertNull(exboSeedStatusRejection("https://cdn.example/a", 200))
    }

    @Test
    fun openSeedDoesNotConnectToCleartext() {
        val server = ServerSocket()
        server.bind(InetSocketAddress("127.0.0.1", 0))
        val accepted = AtomicInteger()
        val thread = Thread {
            try {
                while (true) {
                    server.accept().close()
                    accepted.incrementAndGet()
                }
            } catch (_: Exception) {
                // closed after the assertion
            }
        }
        thread.isDaemon = true
        thread.start()
        try {
            val port = server.localPort
            val clear = assertThrows<IOException> { exboOpenSeed("http://127.0.0.1:$port/a.txt") }
            val userinfo = assertThrows<IOException> { exboOpenSeed("https://user:secret@127.0.0.1:$port/a.txt") }
            assertEquals("no seeds", clear.message)
            assertEquals("no seeds", userinfo.message)
            Thread.sleep(100)
            assertEquals(0, accepted.get())
        } finally {
            server.close()
            thread.join(1000)
        }
    }

    @Test
    fun sameSizeWrongBodyIsNotKept() {
        val root = Files.createTempDirectory("exbo-sync-hash")
        try {
            Files.writeString(root.resolve("a.txt"), "old!")
            val wanted = "zzzz".toByteArray()
            val wrong = "nope".toByteArray()
            assertEquals(wanted.size, wrong.size)
            val torrent = ExboTorrent(
                "game",
                16384,
                listOf(ExboTorrentFile("a.txt", wanted.size.toLong(), exboSha1(wanted))),
            )
            val error = assertThrows<IOException> {
                syncClientFiles(root, torrent, listOf("https://cdn.example/files"), emptyList(), open = {
                    ByteArrayInputStream(wrong)
                })
            }
            assertEquals("file mismatch", error.message)
            assertEquals("old!", root.resolve("a.txt").readText())
            assertFalse(Files.exists(root.resolve("a.txt.part")))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun syncDoesNotWriteThroughADirectorySymlink() {
        val root = Files.createTempDirectory("exbo-sync-link")
        val outside = Files.createTempDirectory("exbo-sync-outside")
        try {
            symLink(root.resolve("bin"), outside)
            val body = "hello".toByteArray()
            val torrent = ExboTorrent(
                "game",
                16384,
                listOf(ExboTorrentFile("bin/a.txt", body.size.toLong(), exboSha1(body))),
            )
            assertThrows<IOException> {
                syncClientFiles(root, torrent, listOf("https://cdn.example/files"), emptyList(), open = {
                    ByteArrayInputStream(body)
                })
            }
            assertFalse(Files.exists(outside.resolve("a.txt")))
            assertFalse(Files.exists(outside.resolve("a.txt.part")))
        } finally {
            root.toFile().deleteRecursively()
            outside.toFile().deleteRecursively()
        }
    }

    @Test
    fun syncReplacesAFileSymlinkWithoutTouchingItsTarget() {
        val root = Files.createTempDirectory("exbo-sync-file-link")
        val outside = Files.createTempDirectory("exbo-sync-file-target")
        try {
            val planted = outside.resolve("planted.txt")
            Files.writeString(planted, "secret")
            symLink(root.resolve("a.txt"), planted)
            val body = "hello".toByteArray()
            val torrent = ExboTorrent(
                "game",
                16384,
                listOf(ExboTorrentFile("a.txt", body.size.toLong(), exboSha1(body))),
            )
            syncClientFiles(root, torrent, listOf("https://cdn.example/files"), emptyList(), open = {
                ByteArrayInputStream(body)
            })
            assertEquals("secret", planted.readText())
            assertEquals("hello", root.resolve("a.txt").readText())
            assertFalse(Files.isSymbolicLink(root.resolve("a.txt")))
        } finally {
            root.toFile().deleteRecursively()
            outside.toFile().deleteRecursively()
        }
    }
}
