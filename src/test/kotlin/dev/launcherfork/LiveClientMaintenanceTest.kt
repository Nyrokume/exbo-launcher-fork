package dev.launcherfork

import dev.launcherfork.data.exbo.DiskInstall
import dev.launcherfork.data.exbo.ExboTorrent
import dev.launcherfork.data.exbo.ExboTorrentFile
import dev.launcherfork.data.exbo.ExboTransportException
import dev.launcherfork.data.exbo.LiveClientSource
import dev.launcherfork.data.exbo.LiveUpdateService
import dev.launcherfork.data.exbo.UnsafeClientDelete
import dev.launcherfork.data.exbo.clientOperationFailure
import dev.launcherfork.data.exbo.deleteClientDirectory
import dev.launcherfork.data.exbo.diskInstall
import dev.launcherfork.data.exbo.exboSha1
import dev.launcherfork.data.exbo.readInstalledHash
import dev.launcherfork.data.exbo.verifyClientFiles
import dev.launcherfork.data.exbo.writeExboTorrent
import dev.launcherfork.domain.Cancellable
import dev.launcherfork.domain.ClientOperation
import dev.launcherfork.domain.GameLauncher
import dev.launcherfork.domain.Progress
import dev.launcherfork.domain.ProgressListener
import dev.launcherfork.domain.Server
import dev.launcherfork.domain.UpdatePhase
import dev.launcherfork.ui.state.ClientState
import dev.launcherfork.ui.state.ClientStateStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Install detection, verify, and delete without a Windows EXBO install.
 * Reinstall's download half is a stand-in: the torrent swarm needs the installed launcher.
 */
class LiveClientMaintenanceTest {
    @Test
    fun diskInstallComparesTheClientDirectoryWithTheCatalogHash(@TempDir dir: Path) {
        val client = dir.resolve("runtime").resolve("stalcraft")
        val absent = diskInstall(client, "remote", installedHash = null)
        assertEquals(DiskInstall(present = false, current = false), absent)

        Files.createDirectories(client)
        assertFalse(diskInstall(client, "remote", "remote").present, "an empty folder is not an install")

        Files.writeString(client.resolve("classes.jar"), "game")
        assertEquals(DiskInstall(present = true, current = false), diskInstall(client, "remote", installedHash = null))
        assertEquals(DiskInstall(present = true, current = false), diskInstall(client, "remote", "other"))
        assertEquals(DiskInstall(present = true, current = true), diskInstall(client, "remote", "remote"))
        assertEquals(DiskInstall(present = true, current = true), diskInstall(client, remoteVersion = null, "remote"))
    }

    @Test
    fun isInstalledIgnoresTheMockFlagAndTheStoreShowsPlay(@TempDir dir: Path) {
        val source = MemorySource(dir)
        val version = "catalog-9"
        source.version = version
        val service = LiveUpdateService(source, { error("no network") }, null)
        val missing = server("missing", installed = true)
        val store = ClientStateStore(service, object : GameLauncher {
            override fun launch(server: Server, listener: dev.launcherfork.domain.GameListener) = Cancellable {}
        }, ui = { it() })
        assertFalse(service.isInstalled(missing))
        assertEquals(ClientState.NotInstalled, store.state(missing).get())

        val id = "stalcraft"
        val client = source.directory(server(id))!!
        Files.createDirectories(client)
        Files.writeString(client.resolve("classes.jar"), "game")
        Files.writeString(dir.resolve("runtime").resolve("$id.hash"), version)
        val installed = server(id, installed = false)
        assertTrue(service.isInstalled(installed))
        assertEquals(ClientState.Ready, store.state(installed).get(), "ИГРАТЬ: hash matches")

        val outdated = server("outdated", installed = false)
        val oldDir = source.directory(outdated)!!
        Files.createDirectories(oldDir)
        Files.writeString(oldDir.resolve("classes.jar"), "game")
        Files.writeString(dir.resolve("runtime").resolve("outdated.hash"), "previous")
        assertTrue(service.isInstalled(outdated))
        assertFalse(diskInstall(oldDir, version, readInstalledHash("outdated", source.runtime())).current, "Обновить")
        assertEquals(ClientState.Ready, store.state(outdated).get())
    }

    @Test
    fun verifyRepairsAMismatchAndLeavesExclusionsAndOutsideFiles(@TempDir dir: Path) {
        val client = dir.resolve("client")
        Files.createDirectories(client.resolve("keep"))
        Files.writeString(client.resolve("classes.jar"), "old")
        Files.writeString(client.resolve("keep").resolve("note.txt"), "user")
        val fresh = "fresh-jar".toByteArray()
        val torrent = ExboTorrent(
            "game",
            16384,
            listOf(
                ExboTorrentFile("classes.jar", fresh.size.toLong(), exboSha1(fresh)),
                ExboTorrentFile("keep/note.txt", 4, exboSha1("nope".toByteArray())),
                ExboTorrentFile("../outside.txt", 1, ByteArray(20)),
            ),
        )
        val phases = mutableListOf<UpdatePhase>()
        val fractions = mutableListOf<Double>()
        var opened = 0
        verifyClientFiles(
            directory = client,
            catalog = writeExboTorrent(torrent),
            seeds = listOf("http://seeds.test/files"),
            exclusions = listOf("keep/"),
            open = {
                opened++
                ByteArrayInputStream(fresh)
            },
            onPhase = { phases += it },
            onProgress = { fractions += it.fraction },
        )
        assertEquals("fresh-jar", Files.readString(client.resolve("classes.jar")))
        assertEquals("user", Files.readString(client.resolve("keep").resolve("note.txt")))
        assertFalse(Files.exists(dir.resolve("outside.txt")))
        assertFalse(Files.exists(client.resolve("outside.txt")))
        assertEquals(1, opened)
        assertTrue(UpdatePhase.CHECKING in phases)
        assertTrue(UpdatePhase.DOWNLOADING in phases)
        assertTrue(fractions.isNotEmpty())
        assertEquals(1.0, fractions.last(), 0.0001)
    }

    @Test
    fun verifyOfAMatchingClientDoesNotDownload(@TempDir dir: Path) {
        val body = "fresh-jar".toByteArray()
        val client = dir.resolve("client")
        Files.createDirectories(client)
        Files.write(client.resolve("classes.jar"), body)
        val torrent = ExboTorrent(
            "game",
            16384,
            listOf(ExboTorrentFile("classes.jar", body.size.toLong(), exboSha1(body))),
        )
        var opened = false
        verifyClientFiles(
            client,
            writeExboTorrent(torrent),
            listOf("http://seeds.test/files"),
            emptyList(),
            open = {
                opened = true
                throw IOException("should not download")
            },
        )
        assertFalse(opened)
        assertEquals("fresh-jar", Files.readString(client.resolve("classes.jar")))
    }

    @Test
    fun verifyFailsClearlyAndDoesNotPretendTheClientUpdated(@TempDir dir: Path) {
        val client = dir.resolve("empty")
        Files.createDirectories(client)
        var opened = false
        val missing = assertThrows<IOException> {
            verifyClientFiles(client, ByteArray(0), emptyList(), emptyList(), open = {
                opened = true
                ByteArrayInputStream(byteArrayOf(1))
            })
        }
        assertEquals("no client", missing.message)
        assertFalse(opened)

        Files.writeString(client.resolve("classes.jar"), "old-jar")
        val wanted = "zzzz".toByteArray()
        val torrent = ExboTorrent(
            "game",
            16384,
            listOf(ExboTorrentFile("classes.jar", wanted.size.toLong(), exboSha1(wanted))),
        )
        val mismatch = assertThrows<IOException> {
            verifyClientFiles(
                client,
                writeExboTorrent(torrent),
                listOf("http://seeds.test/files"),
                emptyList(),
                open = { ByteArrayInputStream("nope".toByteArray()) },
            )
        }
        assertEquals("file mismatch", mismatch.message)
        assertEquals("old-jar", Files.readString(client.resolve("classes.jar")))
        assertFalse(Files.exists(client.resolve("classes.jar.part")))
    }

    @Test
    fun liveVerifyWritesTheCatalogHashOnlyAfterTheFilesMatch(@TempDir dir: Path) {
        val source = MemorySource(dir)
        val id = "stalcraft"
        val client = exboTree(dir, id)
        Files.writeString(client.resolve("classes.jar"), "old-jar")
        val fresh = "fresh-jar".toByteArray()
        source.version = "catalog-9"
        source.catalog = writeExboTorrent(
            ExboTorrent("game", 16384, listOf(ExboTorrentFile("classes.jar", fresh.size.toLong(), exboSha1(fresh)))),
        )
        val service = LiveUpdateService(source, { ByteArrayInputStream(fresh) }, null)
        val listener = RecordingListener()
        service.runOperation(server(id, installed = false), ClientOperation.VERIFY, listener)
        listener.await()
        assertTrue(listener.finished)
        assertEquals(null, listener.failed)
        assertEquals("fresh-jar", Files.readString(client.resolve("classes.jar")))
        assertEquals("catalog-9", readInstalledHash(id, source.runtime())?.trim())
        assertTrue(service.isInstalled(server(id, installed = false)))
        assertBootstrapUntouched(dir, id, hash = "catalog-9")
    }

    @Test
    fun liveVerifyFailureLeavesTheHashAndTheOldFile(@TempDir dir: Path) {
        val source = MemorySource(dir)
        val id = "stalcraft"
        val client = exboTree(dir, id)
        val wanted = "zzzz".toByteArray()
        source.catalog = writeExboTorrent(
            ExboTorrent("game", 16384, listOf(ExboTorrentFile("classes.jar", wanted.size.toLong(), exboSha1(wanted)))),
        )
        val service = LiveUpdateService(source, { ByteArrayInputStream("nope".toByteArray()) }, null)
        val listener = RecordingListener()
        service.runOperation(server(id), ClientOperation.VERIFY, listener)
        listener.await()
        assertEquals("file_mismatch", listener.failed)
        assertFalse(listener.finished)
        assertEquals("old-jar", Files.readString(client.resolve("classes.jar")))
        assertEquals("old-hash", readInstalledHash(id, source.runtime())?.trim())
        assertBootstrapUntouched(dir, id, hash = "old-hash")
    }

    @Test
    fun deleteRemovesOnlyTheClientDirectory(@TempDir dir: Path) {
        val id = "stalcraft"
        val client = exboTree(dir, id)
        Files.writeString(client.resolve("config").apply { Files.createDirectories(this) }.resolve("user.cfg"), "x")
        val outside = dir.resolve("outside.txt")
        Files.writeString(outside, "keep")
        Files.createSymbolicLink(client.resolve("to-boot"), dir.resolve("bootstrap.jar"))
        val home = dir.resolve("user-home")
        Files.createDirectories(home)
        val fractions = mutableListOf<Double>()
        deleteClientDirectory(client, dir, home = home) { fractions += it }
        assertFalse(Files.exists(client))
        assertEquals("keep", Files.readString(outside))
        assertBootstrapUntouched(dir, id, hash = "old-hash")
        assertEquals(1.0, fractions.last(), 0.0001)
    }

    @Test
    fun deleteRefusesBootstrapHomeSymlinksAndParents(@TempDir dir: Path) {
        val id = "stalcraft"
        exboTree(dir, id)
        val home = dir.resolve("user-home")
        Files.createDirectories(home)
        Files.writeString(home.resolve("secret"), "mine")
        val runtime = dir.resolve("runtime")
        listOf(
            dir,
            runtime,
            dir.resolve("launcher"),
            dir.resolve("java"),
            runtime.resolve("logs"),
            dir.parent,
            home,
        ).forEach { path ->
            assertThrows<UnsafeClientDelete> { deleteClientDirectory(path, dir, home = home) }
        }
        val link = runtime.resolve("linked")
        Files.createSymbolicLink(link, dir.resolve("launcher"))
        assertThrows<UnsafeClientDelete> { deleteClientDirectory(link, dir, home = home) }
        assertTrue(Files.isSymbolicLink(link))
        assertTrue(Files.exists(dir.resolve("launcher").resolve("launcher.jar")))
        assertEquals("mine", Files.readString(home.resolve("secret")))
        assertBootstrapUntouched(dir, id, hash = "old-hash")
        assertTrue(Files.exists(runtime.resolve(id).resolve("classes.jar")))
        val sandbox = dir.resolve("sandbox")
        val otherExbo = sandbox.resolve("EXBO")
        exboTree(otherExbo, id)
        val outsideHome = sandbox.resolve("home")
        Files.createDirectories(outsideHome)
        Files.writeString(outsideHome.resolve("secret"), "mine")
        assertThrows<UnsafeClientDelete> { deleteClientDirectory(outsideHome, otherExbo, home = outsideHome) }
        assertEquals("mine", Files.readString(outsideHome.resolve("secret")))
        assertBootstrapUntouched(otherExbo, id, hash = "old-hash")
    }

    @Test
    fun deleteOfAMappedFolderStaysOutsideTheExboTree(@TempDir dir: Path) {
        val exbo = dir.resolve("EXBO")
        exboTree(exbo, "stalcraft")
        val games = dir.resolve("games")
        val client = games.resolve("stalcraft")
        Files.createDirectories(client)
        Files.writeString(client.resolve("classes.jar"), "game")
        Files.writeString(games.resolve("other.txt"), "keep")
        val home = dir.resolve("home")
        Files.createDirectories(home)
        deleteClientDirectory(client, exbo, home = home)
        assertFalse(Files.exists(client))
        assertEquals("keep", Files.readString(games.resolve("other.txt")))
        assertBootstrapUntouched(exbo, "stalcraft", hash = "old-hash")
    }

    @Test
    fun liveDeleteAndReinstallDoNotTouchTheBootstrap(@TempDir dir: Path) {
        val source = MemorySource(dir)
        val id = "stalcraft"
        val client = exboTree(dir, id)
        val service = LiveUpdateService(source, { error("no network") }, null)
        val deleted = RecordingListener()
        service.runOperation(server(id, installed = true), ClientOperation.DELETE, deleted)
        deleted.await()
        assertTrue(deleted.finished, deleted.failed)
        assertFalse(Files.exists(client))
        assertFalse(service.isInstalled(server(id, installed = true)))
        assertBootstrapUntouched(dir, id, hash = "old-hash")

        val again = exboTree(dir, id)
        var fetchedAfterDelete = false
        val reinstall = LiveUpdateService(source, { error("no network") }) { _, _, listener ->
            fetchedAfterDelete = !Files.exists(again.resolve("classes.jar"))
            Files.createDirectories(again)
            Files.writeString(again.resolve("classes.jar"), "fresh")
            listener.onFinished()
        }
        val done = RecordingListener()
        reinstall.runOperation(server(id), ClientOperation.REINSTALL, done)
        done.await()
        assertTrue(fetchedAfterDelete)
        assertTrue(done.finished)
        assertEquals("fresh", Files.readString(again.resolve("classes.jar")))
        assertBootstrapUntouched(dir, id, hash = "old-hash")
        assertTrue(reinstall.isInstalled(server(id, installed = false)))
    }

    @Test
    fun reinstallWithoutACatalogDoesNotDelete(@TempDir dir: Path) {
        val source = MemorySource(dir)
        val client = exboTree(dir, "stalcraft")
        source.downloadable = false
        val fetched = AtomicBoolean(false)
        val service = LiveUpdateService(source, { error("no network") }) { _, _, listener ->
            fetched.set(true)
            listener.onFinished()
        }
        val listener = RecordingListener()
        service.runOperation(server("stalcraft"), ClientOperation.REINSTALL, listener)
        listener.await()
        assertEquals("no_catalog", listener.failed)
        assertFalse(fetched.get())
        assertEquals("old-jar", Files.readString(client.resolve("classes.jar")))
        assertBootstrapUntouched(dir, "stalcraft", hash = "old-hash")
    }

    @Test
    fun reinstallOfAnUnsafePathDoesNotDownloadOrDelete(@TempDir dir: Path) {
        val source = MemorySource(dir)
        exboTree(dir, "stalcraft")
        source.forcedDirectory = dir
        val fetched = AtomicBoolean(false)
        val service = LiveUpdateService(source, { error("no network") }) { _, _, listener ->
            fetched.set(true)
            listener.onFinished()
        }
        val listener = RecordingListener()
        service.runOperation(server("stalcraft"), ClientOperation.REINSTALL, listener)
        listener.await()
        assertEquals("unsafe_path", listener.failed)
        assertFalse(fetched.get())
        assertTrue(Files.exists(dir.resolve("bootstrap.jar")))
        assertTrue(Files.exists(dir.resolve("runtime").resolve("stalcraft").resolve("classes.jar")))
        assertBootstrapUntouched(dir, "stalcraft", hash = "old-hash")
    }

    @Test
    fun operationErrorsAreTranslated() {
        val keys = listOf(
            "main.exception.verify", "main.exception.verify.desc", "main.exception.verify.no_client",
            "main.exception.verify.no_catalog", "main.exception.verify.no_seeds", "main.exception.verify.file_mismatch",
            "main.exception.verify.no_session", "main.exception.verify.io",
            "main.exception.reinstall", "main.exception.reinstall.desc", "main.exception.reinstall.no_client",
            "main.exception.reinstall.unsafe_path", "main.exception.reinstall.no_catalog",
            "main.exception.reinstall.no_session", "main.exception.reinstall.io",
            "main.exception.delete", "main.exception.delete.desc", "main.exception.delete.no_client",
            "main.exception.delete.unsafe_path", "main.exception.delete.io",
        )
        listOf("messages.properties", "messages_ru.properties", "messages_en.properties").forEach { file ->
            val props = java.util.Properties()
            javaClass.getResourceAsStream("/dev/launcherfork/ui/i18n/$file")!!.reader(Charsets.UTF_8).use(props::load)
            keys.forEach { key -> assertFalse(props.getProperty(key).isNullOrBlank(), "$file misses $key") }
        }
    }

    @Test
    fun failureCodesStayFreeOfPaths() {
        assertEquals("unsafe_path", clientOperationFailure(UnsafeClientDelete()))
        assertEquals("no_session", clientOperationFailure(ExboTransportException("token secret")))
        assertEquals("no_client", clientOperationFailure(IOException("no client")))
        assertEquals("no_seeds", clientOperationFailure(IOException("no seeds")))
        assertEquals("file_mismatch", clientOperationFailure(IOException("file mismatch")))
        assertEquals("no_catalog", clientOperationFailure(IOException("no catalog")))
        assertEquals("io", clientOperationFailure(IOException("/tmp/secret/classes.jar")))
        assertEquals("error", clientOperationFailure(IllegalStateException("boom")))
    }

    private class MemorySource(val exbo: Path) : LiveClientSource {
        var forcedDirectory: Path? = null
        var version: String? = "catalog-9"
        var seeds: List<String> = listOf("http://seeds.test/files")
        var exclusions: List<String> = emptyList()
        var catalog: ByteArray = ByteArray(0)
        var downloadable = true
        override fun directory(server: Server): Path? =
            forcedDirectory ?: exbo.resolve("runtime").resolve(server.id)
        override fun remoteVersion(id: String): String? = version
        override fun httpSeeds(id: String): List<String> = seeds
        override fun exclusions(id: String): List<String> = exclusions
        override fun catalog(id: String): ByteArray = catalog
        override fun canDownload(server: Server): Boolean = downloadable
        override fun runtime(): Path = exbo.resolve("runtime")
        override fun exboRoot(): Path = exbo
    }

    private class RecordingListener : ProgressListener {
        val fractions = CopyOnWriteArrayList<Double>()
        val done = CountDownLatch(1)
        var finished = false
        var failed: String? = null
        override fun onProgress(progress: Progress) {
            fractions += progress.fraction
        }
        override fun onFinished() {
            finished = true
            done.countDown()
        }
        override fun onFailed(reason: String) {
            failed = reason
            done.countDown()
        }
        fun await() {
            assertTrue(done.await(10, TimeUnit.SECONDS), "operation did not finish")
        }
    }

    private fun server(id: String, installed: Boolean = false) = Server(
        id = id,
        caption = id,
        online = false,
        players = 0,
        description = null,
        iconUri = URI.create("file:///icon.png"),
        backgroundUri = URI.create("file:///bg.png"),
        installed = installed,
        clientSizeBytes = 10,
    )

    private fun exboTree(root: Path, id: String): Path {
        val runtime = root.resolve("runtime")
        val client = runtime.resolve(id)
        Files.createDirectories(client.resolve("config"))
        Files.createDirectories(root.resolve("launcher"))
        Files.createDirectories(root.resolve("java").resolve("bin"))
        Files.createDirectories(runtime.resolve("logs"))
        Files.write(root.resolve("bootstrap.jar"), byteArrayOf(1, 2, 3))
        Files.write(root.resolve("launcher").resolve("launcher.jar"), byteArrayOf(4))
        Files.write(root.resolve("java").resolve("bin").resolve("java.exe"), byteArrayOf(5))
        Files.write(runtime.resolve("logs").resolve("game.log"), byteArrayOf(6))
        Files.write(runtime.resolve("clients"), byteArrayOf(7, 8))
        Files.write(runtime.resolve("settings.json"), byteArrayOf(9))
        Files.writeString(runtime.resolve("$id.hash"), "old-hash")
        Files.writeString(client.resolve("classes.jar"), "old-jar")
        return client
    }

    private fun assertBootstrapUntouched(root: Path, id: String, hash: String) {
        val runtime = root.resolve("runtime")
        assertEquals(listOf<Byte>(1, 2, 3), Files.readAllBytes(root.resolve("bootstrap.jar")).toList())
        assertEquals(listOf<Byte>(4), Files.readAllBytes(root.resolve("launcher").resolve("launcher.jar")).toList())
        assertEquals(listOf<Byte>(5), Files.readAllBytes(root.resolve("java").resolve("bin").resolve("java.exe")).toList())
        assertEquals(listOf<Byte>(6), Files.readAllBytes(runtime.resolve("logs").resolve("game.log")).toList())
        assertEquals(listOf<Byte>(7, 8), Files.readAllBytes(runtime.resolve("clients")).toList())
        assertEquals(listOf<Byte>(9), Files.readAllBytes(runtime.resolve("settings.json")).toList())
        assertEquals(hash, Files.readString(runtime.resolve("$id.hash")).trim())
    }
}
