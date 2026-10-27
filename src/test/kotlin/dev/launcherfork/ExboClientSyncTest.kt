package dev.launcherfork

import dev.launcherfork.data.exbo.ExboTorrent
import dev.launcherfork.data.exbo.ExboTorrentFile
import dev.launcherfork.data.exbo.decodeExboTorrent
import dev.launcherfork.data.exbo.exboPathExcluded
import dev.launcherfork.data.exbo.exboSeedFileUrl
import dev.launcherfork.data.exbo.exboSha1
import dev.launcherfork.data.exbo.mapClientFiles
import dev.launcherfork.data.exbo.parseExboTorrent
import dev.launcherfork.data.exbo.syncClientFiles
import dev.launcherfork.data.exbo.writeExboTorrent
import com.frostwire.jlibtorrent.Priority
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText

class ExboClientSyncTest {
    @Test
    fun catalogRoundTripKeepsNameSizeAndHash() {
        val body = "hello".toByteArray()
        val torrent = ExboTorrent(
            name = "game",
            pieceLength = 16 * 1024,
            files = listOf(ExboTorrentFile("bin/a.txt", body.size.toLong(), exboSha1(body))),
        )
        val parsed = parseExboTorrent(writeExboTorrent(torrent))
        assertEquals(torrent.name, parsed.name)
        val gzipped = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(gzipped).use { it.write(writeExboTorrent(torrent)) }
        val wrapped = byteArrayOf(1, 2, 3, 4) + gzipped.toByteArray()
        assertEquals(torrent.files, parseExboTorrent(wrapped).files)
        assertEquals(torrent.pieceLength, parsed.pieceLength)
        assertEquals(torrent.files, parsed.files)
    }

    @Test
    fun syncDownloadsTheChangedFileAndLeavesAnExclusionAlone() {
        val fresh = "hello".toByteArray()
        val torrent = ExboTorrent(
            "game",
            16384,
            listOf(
                ExboTorrentFile("a.txt", fresh.size.toLong(), exboSha1(fresh)),
                ExboTorrentFile("keep/note.txt", 4, exboSha1("user".toByteArray())),
                ExboTorrentFile("../outside.txt", 1, ByteArray(20)),
            ),
        )
        val root = Files.createTempDirectory("exbo-sync")
        try {
            Files.writeString(root.resolve("a.txt"), "old")
            Files.createDirectories(root.resolve("keep"))
            Files.writeString(root.resolve("keep").resolve("note.txt"), "user")
            val seen = mutableListOf<String>()
            val seed = "https://cdn.example/files"
            syncClientFiles(root, torrent, listOf(seed), listOf("keep/"), open = { url ->
                seen += url
                java.io.ByteArrayInputStream(if (url.endsWith("/a.txt")) fresh else ByteArray(0))
            })
            assertEquals("hello", root.resolve("a.txt").readText())
            assertEquals("user", root.resolve("keep").resolve("note.txt").readText())
            assertFalse(root.resolve("outside.txt").exists())
            assertFalse(root.parent.resolve("outside.txt").exists())
            assertEquals(listOf(exboSeedFileUrl(seed, "game", "a.txt", withName = false)), seen)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun syncFailsWhenNoSeedHasTheFileAndDoesNotPretendItUpdated() {
        val root = Files.createTempDirectory("exbo-sync-miss")
        try {
            val torrent = ExboTorrent(
                "game",
                16384,
                listOf(ExboTorrentFile("a.txt", 1, exboSha1("z".toByteArray()))),
            )
            assertThrows<IOException> {
                syncClientFiles(root, torrent, listOf("https://cdn.example/files"), emptyList(), open = {
                    throw IOException("http 404")
                })
            }
            assertFalse(root.resolve("a.txt").exists())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun cachedCatalogParsesWhenTheLauncherHasOne() {
        val root = System.getenv("APPDATA") ?: return
        val file = java.nio.file.Path.of(root, "EXBO", "runtime", "stalcraft.torrent.bin")
        assumeTrue(Files.isRegularFile(file))
        val torrent = parseExboTorrent(Files.readAllBytes(file))
        assertTrue(torrent.files.isNotEmpty())
        assertTrue(torrent.pieceLength > 0)
        assertTrue(torrent.name.isNotBlank())
    }

    @Test
    fun officialCatalogRenamesFilesOntoTheClientFolder() {
        val root = System.getenv("APPDATA") ?: return
        val file = java.nio.file.Path.of(root, "EXBO", "runtime", "stalcraft.torrent.bin")
        val jar = java.nio.file.Path.of(root, "EXBO", "launcher", "launcher.jar")
        assumeTrue(Files.isRegularFile(file) && Files.isRegularFile(jar))
        val decoded = decodeExboTorrent(Files.readAllBytes(file), listOf("http://127.0.0.1/seed/"), jar)
        val parsed = parseExboTorrent(Files.readAllBytes(file))
        assertEquals(parsed.files.size, decoded.paths.size)
        assertEquals(parsed.files.first().path, decoded.paths.first())
        val keep = decoded.paths.first { !it.replace('\\', '/').startsWith("parts/") }
        val priorities = mapClientFiles(decoded.info, decoded.paths, listOf(keep))
        val kept = decoded.info.files().filePath(decoded.paths.indexOf(keep)).replace('\\', '/')
        assertEquals("parts/${keep.replace('\\', '/')}", kept)
        val game = decoded.paths.indexOfFirst {
            val path = it.replace('\\', '/')
            path != keep.replace('\\', '/') && !path.startsWith("parts/") && !exboPathExcluded(path, listOf(keep))
        }
        assertTrue(game >= 0)
        assertEquals(decoded.paths[game].replace('\\', '/'), decoded.info.files().filePath(game).replace('\\', '/'))
        assertEquals(Priority.IGNORE, priorities[decoded.paths.indexOf(keep)])
        assertEquals(Priority.NORMAL, priorities[game])
    }
}
