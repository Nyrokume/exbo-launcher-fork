package dev.launcherfork

import dev.launcherfork.data.exbo.ExboClients
import dev.launcherfork.data.exbo.catalogClientDownloadable
import dev.launcherfork.data.exbo.installedClientNeedsUpdate
import dev.launcherfork.data.exbo.readClientsLocations
import dev.launcherfork.data.exbo.readInstalledHash
import dev.launcherfork.data.exbo.writeInstalledHash
import dev.launcherfork.ui.screens.main.liveDownloadLocked
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.DataOutputStream
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
    fun catalogClientWithVersionAndSeedsCanBeDownloaded() {
        assertTrue(catalogClientDownloadable("abc", listOf("https://cdn.example/files")))
        assertFalse(catalogClientDownloadable(null, listOf("https://cdn.example/files")))
        assertFalse(catalogClientDownloadable("abc", emptyList()))
        assertFalse(catalogClientDownloadable("  ", listOf("https://cdn.example/files")))
    }

    @Test
    fun liveDownloadStaysOpenForACatalogClientAndAnUpdate() {
        assertFalse(liveDownloadLocked(liveBackend = true, needsUpdate = false, canDownload = true, awaitingDownload = true))
        assertFalse(liveDownloadLocked(liveBackend = true, needsUpdate = true, canDownload = false, awaitingDownload = false))
        assertTrue(liveDownloadLocked(liveBackend = true, needsUpdate = false, canDownload = false, awaitingDownload = true))
        assertFalse(liveDownloadLocked(liveBackend = false, needsUpdate = false, canDownload = false, awaitingDownload = true))
    }
}
