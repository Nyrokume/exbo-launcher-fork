package dev.launcherfork

import dev.launcherfork.data.exbo.ExboClients
import dev.launcherfork.data.exbo.catalogClientDownloadable
import dev.launcherfork.data.exbo.collapseAliasedServers
import dev.launcherfork.data.exbo.installedClientNeedsUpdate
import dev.launcherfork.data.exbo.readClientsLocations
import dev.launcherfork.data.exbo.mainClientTouchedAt
import dev.launcherfork.data.exbo.readInstalledHash
import dev.launcherfork.data.exbo.writeInstalledHash
import dev.launcherfork.domain.Server
import dev.launcherfork.ui.screens.main.liveDownloadLocked
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.DataOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

class ExboClientsTest {
    @Test
    fun readsTheBinaryMapAndFallsBackWhenTheIdIsAbsent(@TempDir dir: Path) {
        val file = dir.resolve("clients")
        DataOutputStream(Files.newOutputStream(file)).use { out ->
            out.writeInt(1)
            out.writeUTF("stalcraft")
            out.writeUTF("D:/games/stalcraft")
        }
        val before = Files.readAllBytes(file)
        assertEquals("D:/games/stalcraft", readClientsLocations(file)["stalcraft"])
        assertEquals(Path.of("D:/games/stalcraft"), ExboClients.directory("stalcraft", file, dir.resolve("runtime")))
        val missing = ExboClients.directory("other", file, dir.resolve("runtime"))
        assertEquals(dir.resolve("runtime").resolve("other"), missing)
        assertFalse(Files.exists(missing))
        assertEquals(before.toList(), Files.readAllBytes(file).toList())
    }

    @Test
    fun catalogHashIsRuntimeIdHashAndTheGameVersionFileIsNotTheCheck(@TempDir dir: Path) {
        val runtime = dir.resolve("runtime")
        val client = runtime.resolve("stalcraft")
        Files.createDirectories(client)
        Files.writeString(client.resolve("version"), "17baeee1-df6c-4a38-b14d-4b684e5ab6b6")
        val catalog = "9b85b186cb9d5ef8d6f847cb62de2ede6e8e4656"
        assertTrue(writeInstalledHash("stalcraft", catalog, runtime))
        assertEquals(catalog, readInstalledHash("stalcraft", runtime)?.trim())
        assertEquals("17baeee1-df6c-4a38-b14d-4b684e5ab6b6", Files.readString(client.resolve("version")))
        assertFalse(installedClientNeedsUpdate(catalog, readInstalledHash("stalcraft", runtime), clientPresent = true))
        assertTrue(installedClientNeedsUpdate("other-hash", readInstalledHash("stalcraft", runtime), clientPresent = true))
        assertTrue(installedClientNeedsUpdate(catalog, installedHash = null, clientPresent = true))
        assertFalse(installedClientNeedsUpdate(catalog, installedHash = null, clientPresent = false))
        assertFalse(installedClientNeedsUpdate(remoteVersion = null, installedHash = catalog, clientPresent = true))
        assertFalse(writeInstalledHash("../stalcraft", catalog, runtime))
        assertNull(ExboClients.installedHashFile("../stalcraft", runtime))
        assertFalse(Files.exists(dir.resolve("stalcraft.hash")))
    }

    @Test
    fun catalogStalcraftUsesTheMappedGlobalClientNotALeftoverFolder(@TempDir dir: Path) {
        val runtime = dir.resolve("runtime")
        val leftover = runtime.resolve("stalcraft")
        val mapped = runtime.resolve("stalcraft_global")
        Files.createDirectories(leftover)
        Files.writeString(leftover.resolve("classes.jar"), "old")
        Files.createDirectories(mapped)
        Files.writeString(mapped.resolve("classes.jar"), "game")
        val file = runtime.resolve("clients")
        writeClients(file, "stalcraft_global" to mapped.toString())
        assertEquals(mapped, ExboClients.directory("stalcraft", file, runtime))
        assertEquals(mapped, ExboClients.directory("stalcraft_global", file, runtime))
        val catalog = "247eeac5569d2e5814c1cf04a9d7f420e3a9183e"
        assertTrue(writeInstalledHash("stalcraft_global", catalog, runtime))
        assertEquals(catalog, readInstalledHash("stalcraft", runtime)?.trim())
        assertFalse(installedClientNeedsUpdate(catalog, readInstalledHash("stalcraft", runtime), clientPresent = true))
    }

    @Test
    fun hashWithoutClientsFileFollowsTheSiblingGlobalHash(@TempDir dir: Path) {
        val runtime = dir.resolve("runtime")
        Files.createDirectories(runtime)
        val catalog = "global-hash"
        assertTrue(writeInstalledHash("stalcraft_global", catalog, runtime))
        assertEquals(catalog, readInstalledHash("stalcraft", runtime)?.trim())
        assertEquals(runtime.resolve("stalcraft_global"), ExboClients.directory("stalcraft", runtime.resolve("clients"), runtime))
    }

    @Test
    fun newerGlobalHashWinsWhenBothHashesExistWithoutAClientsFile(@TempDir dir: Path) {
        val runtime = dir.resolve("runtime")
        Files.createDirectories(runtime)
        assertTrue(writeInstalledHash("stalcraft", "old-ru", runtime))
        assertTrue(writeInstalledHash("stalcraft_global", "new-global", runtime))
        val ruHash = runtime.resolve("stalcraft.hash")
        val globalHash = runtime.resolve("stalcraft_global.hash")
        Files.setLastModifiedTime(ruHash, java.nio.file.attribute.FileTime.fromMillis(1_000))
        Files.setLastModifiedTime(globalHash, java.nio.file.attribute.FileTime.fromMillis(2_000))
        assertEquals(runtime.resolve("stalcraft_global"), ExboClients.directory("stalcraft", runtime.resolve("clients"), runtime))
        assertEquals("new-global", readInstalledHash("stalcraft", runtime)?.trim())
        assertEquals(2_000L, mainClientTouchedAt(dir))
    }

    @Test
    fun exactClientsRowStillWinsOverTheAlias(@TempDir dir: Path) {
        val runtime = dir.resolve("runtime")
        val ru = runtime.resolve("stalcraft")
        val global = runtime.resolve("stalcraft_global")
        val file = runtime.resolve("clients")
        writeClients(file, "stalcraft" to ru.toString(), "stalcraft_global" to global.toString())
        assertEquals(ru, ExboClients.directory("stalcraft", file, runtime))
        assertEquals(global, ExboClients.directory("stalcraft_global", file, runtime))
    }

    @Test
    fun catalogKeepsTheMappedClientWhenBothAliasIdsAreListed(@TempDir dir: Path) {
        val runtime = dir.resolve("runtime")
        Files.createDirectories(runtime)
        val mapped = runtime.resolve("stalcraft_global")
        val leftover = card("stalcraft")
        val official = card("stalcraft_global")
        val ots = card("stalcraft_ots")
        val collapsed = collapseAliasedServers(
            listOf(leftover, official, ots),
            mapOf("stalcraft_global" to mapped.toString()),
            runtime,
        )
        assertEquals(listOf("stalcraft_global", "stalcraft_ots"), collapsed.map { it.id })
    }

    @Test
    fun catalogClientWithVersionAndSeedsCanBeDownloaded() {
        assertTrue(catalogClientDownloadable("abc", listOf("https://cdn.example/files")))
        assertFalse(catalogClientDownloadable(null, listOf("https://cdn.example/files")))
        assertFalse(catalogClientDownloadable("abc", emptyList()))
        assertFalse(catalogClientDownloadable("abc", listOf("http://cdn.example/files")))
        assertFalse(catalogClientDownloadable("  ", listOf("https://cdn.example/files")))
    }

    @Test
    fun liveDownloadStaysOpenForACatalogClientAndAnUpdate() {
        assertFalse(liveDownloadLocked(liveBackend = true, needsUpdate = false, canDownload = true, awaitingDownload = true))
        assertFalse(liveDownloadLocked(liveBackend = true, needsUpdate = true, canDownload = false, awaitingDownload = false))
        assertTrue(liveDownloadLocked(liveBackend = true, needsUpdate = false, canDownload = false, awaitingDownload = true))
        assertFalse(liveDownloadLocked(liveBackend = false, needsUpdate = false, canDownload = false, awaitingDownload = true))
    }

    private fun writeClients(file: Path, vararg rows: Pair<String, String>) {
        Files.createDirectories(file.parent)
        DataOutputStream(Files.newOutputStream(file)).use { out ->
            out.writeInt(rows.size)
            for ((id, path) in rows) {
                out.writeUTF(id)
                out.writeUTF(path)
            }
        }
    }

    private fun card(id: String) = Server(
        id = id,
        caption = id,
        online = false,
        players = 0,
        description = null,
        iconUri = URI.create("file:///icon.png"),
        backgroundUri = URI.create("file:///bg.png"),
        installed = false,
        clientSizeBytes = 1,
    )
}
