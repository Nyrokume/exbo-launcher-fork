package dev.launcherfork

import dev.launcherfork.data.exbo.JLIBTORRENT_JNI_PROPERTY
import dev.launcherfork.data.exbo.JLIBTORRENT_LIBRARY_NAME
import dev.launcherfork.data.exbo.JLIBTORRENT_SHA256
import dev.launcherfork.data.exbo.JRE_INTEGRITY_ERROR
import dev.launcherfork.data.exbo.TORRENT_INTEGRITY_ERROR
import dev.launcherfork.data.exbo.bundledExboTorrentLibrary
import dev.launcherfork.data.exbo.deleteTree
import dev.launcherfork.data.exbo.ensureExboTorrentNative
import dev.launcherfork.data.exbo.exboSha256Hex
import dev.launcherfork.data.exbo.exboTorrentNativeCache
import dev.launcherfork.data.exbo.jreLayout
import dev.launcherfork.data.exbo.jreTreeMatches
import dev.launcherfork.data.exbo.realWorkDirectory
import dev.launcherfork.data.exbo.torrentNativeMatches
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * SEC-M1 / SEC-M2 without a Windows EXBO install: fake JRE trees and the bundled DLL bytes.
 * These tests never call `System.load`.
 */
class ExboExtractIntegrityTest {
    @Test
    fun sha256HexKeepsLeadingZeros() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            exboSha256Hex(ByteArray(0)),
        )
    }

    @Test
    fun jreLayoutStaysUnderTheExboTreeAndReplacesAPlantedRuntime() {
        val root = Files.createTempDirectory("exbo-m1")
        val legacy = Path.of(System.getProperty("java.io.tmpdir"), "exbo-fork-jre")
        val outside = Files.createTempDirectory("exbo-m1-outside")
        try {
            writeFakeJre(root, exe = "good-java", dll = "good-dll")
            val planted = legacy.resolve("java").resolve("bin")
            Files.createDirectories(planted)
            Files.writeString(planted.resolve("java.exe"), "evil-temp")
            Files.writeString(outside.resolve("kept.txt"), "kept")
            Files.createDirectories(root.resolve("launcher-fork").resolve("jre-layout"))
            Files.createSymbolicLink(root.resolve("launcher-fork").resolve("jre-layout").resolve("java"), outside)

            val layout = jreLayout(root)
            assertEquals(root.resolve("launcher-fork").resolve("jre-layout").normalize(), layout.normalize())
            assertFalse(layout.startsWith(legacy))
            assertEquals("good-java", layout.resolve("java/bin/java.exe").readText())
            assertEquals("good-dll", layout.resolve("java/bin/jmt.dll").readText())
            assertFalse(Files.isSymbolicLink(layout.resolve("java")))
            assertEquals("kept", outside.resolve("kept.txt").readText())
            assertFalse(Files.exists(legacy))
            assertTrue(jreTreeMatches(root.resolve("java"), layout.resolve("java")))

            Files.writeString(layout.resolve("java/bin/java.exe"), "swapped")
            Files.writeString(layout.resolve("java/bin/extra.dll"), "extra")
            val again = jreLayout(root)
            assertEquals("good-java", again.resolve("java/bin/java.exe").readText())
            assertFalse(Files.exists(again.resolve("java/bin/extra.dll")))
        } finally {
            deleteTree(root)
            deleteTree(legacy)
            deleteTree(outside)
        }
    }

    @Test
    fun jreLayoutFailsClosedWhenTheInstalledTreeHasNoExecutable() {
        val root = Files.createTempDirectory("exbo-m1-missing")
        try {
            Files.createDirectories(root.resolve("java").resolve("bin"))
            Files.writeString(root.resolve("java/bin/other.dll"), "x")
            val error = assertThrows<IOException> { jreLayout(root) }
            assertEquals(JRE_INTEGRITY_ERROR, error.message)
            assertFalse(Files.exists(root.resolve("launcher-fork").resolve("jre-layout").resolve("java/bin/other.dll")))
        } finally {
            deleteTree(root)
        }
    }

    @Test
    fun jreLayoutDoesNotFollowALinkedExecutable() {
        val root = Files.createTempDirectory("exbo-m1-link")
        val outside = Files.createTempFile("exbo-secret", ".bin")
        try {
            Files.writeString(outside, "secret")
            val bin = root.resolve("java").resolve("bin")
            Files.createDirectories(bin)
            Files.createSymbolicLink(bin.resolve("java.exe"), outside)
            Files.writeString(bin.resolve("jmt.dll"), "dll")
            val error = assertThrows<IOException> { jreLayout(root) }
            assertEquals(JRE_INTEGRITY_ERROR, error.message)
            assertEquals("secret", outside.readText())
            assertFalse(Files.exists(root.resolve("launcher-fork").resolve("jre-layout").resolve("java/bin/java.exe")))
        } finally {
            deleteTree(root)
            Files.deleteIfExists(outside)
        }
    }

    @Test
    fun mismatchedJreTreesDoNotCountAsTheInstalledLayout() {
        val root = Files.createTempDirectory("exbo-m1-match")
        val dest = Files.createTempDirectory("exbo-m1-dest")
        try {
            writeFakeJre(root, exe = "good-java", dll = "good-dll")
            val source = root.resolve("java")
            val copy = dest.resolve("java")
            Files.createDirectories(copy.resolve("bin"))
            Files.writeString(copy.resolve("bin/java.exe"), "good-java")
            Files.writeString(copy.resolve("bin/jmt.dll"), "good-dll")
            assertTrue(jreTreeMatches(source, copy))
            Files.writeString(copy.resolve("bin/java.exe"), "good-java-tampered")
            assertFalse(jreTreeMatches(source, copy))
            Files.writeString(copy.resolve("bin/java.exe"), "good-java")
            Files.createSymbolicLink(copy.resolve("bin/extra.dll"), copy.resolve("bin/jmt.dll"))
            assertFalse(jreTreeMatches(source, copy))
        } finally {
            deleteTree(dest)
            deleteTree(root)
        }
    }

    @Test
    fun workDirectoryRefusesALinkOutOfTheLayout() {
        val layout = Files.createTempDirectory("exbo-work")
        val outside = Files.createTempDirectory("exbo-args")
        try {
            Files.writeString(outside.resolve("args_new.txt"), "official")
            Files.createSymbolicLink(layout.resolve("launcher"), outside)
            val error = assertThrows<IOException> { realWorkDirectory(layout, "launcher") }
            assertEquals(JRE_INTEGRITY_ERROR, error.message)
            assertEquals("official", outside.resolve("args_new.txt").readText())
        } finally {
            deleteTree(layout)
            deleteTree(outside)
        }
    }

    @Test
    fun jreLayoutDoesNotCreateTheCopyThroughALinkedParent() {
        val root = Files.createTempDirectory("exbo-m1-parent-link")
        val outside = Files.createTempDirectory("exbo-m1-parent-out")
        try {
            writeFakeJre(root, exe = "good-java", dll = "good-dll")
            Files.createSymbolicLink(root.resolve("launcher-fork"), outside)
            val error = assertThrows<IOException> { jreLayout(root) }
            assertEquals(JRE_INTEGRITY_ERROR, error.message)
            assertFalse(Files.exists(outside.resolve("jre-layout").resolve("java").resolve("bin").resolve("java.exe")))
        } finally {
            deleteTree(root)
            deleteTree(outside)
        }
    }

    @Test
    fun nativeCacheLivesUnderTheExboTree() {
        val root = Files.createTempDirectory("exbo-m2-cache")
        try {
            assertEquals(
                root.resolve("launcher-fork").resolve("native"),
                exboTorrentNativeCache(root),
            )
        } finally {
            deleteTree(root)
        }
    }

    @Test
    fun bundledTorrentLibraryMatchesThePinnedSha256() {
        val bytes = bundledExboTorrentLibrary()
        assertEquals(JLIBTORRENT_SHA256, exboSha256Hex(bytes))
        assertTrue(bytes.size > 100_000)
    }

    @Test
    fun sizeAloneDoesNotMakeATempDllLoadable() {
        val cache = Files.createTempDirectory("exbo-m2-size")
        val legacy = Path.of(System.getProperty("java.io.tmpdir"), "launcher-fork", JLIBTORRENT_LIBRARY_NAME)
        val bytes = bundledExboTorrentLibrary()
        try {
            Files.createDirectories(legacy.parent)
            Files.write(legacy, ByteArray(200_000) { 1 })
            Files.write(cache.resolve(JLIBTORRENT_LIBRARY_NAME), ByteArray(200_000) { 2 })
            withoutJniPath {
                ensureExboTorrentNative(cache, bytes)
                val dest = cache.resolve(JLIBTORRENT_LIBRARY_NAME)
                assertEquals(dest.toAbsolutePath().toString(), System.getProperty(JLIBTORRENT_JNI_PROPERTY))
                assertTrue(torrentNativeMatches(dest))
                assertEquals(JLIBTORRENT_SHA256, exboSha256Hex(dest))
                assertFalse(Files.exists(legacy))
                assertFalse(Files.exists(cache.resolve("$JLIBTORRENT_LIBRARY_NAME.part")))
            }
        } finally {
            deleteTree(cache)
            Files.deleteIfExists(legacy)
        }
    }

    @Test
    fun configuredJniPathFailsClosedWhenTheHashDoesNotMatch() {
        val cache = Files.createTempDirectory("exbo-m2-bad-path")
        val evil = cache.resolve("evil.dll")
        Files.write(evil, ByteArray(200_000) { 3 })
        try {
            withoutJniPath {
                System.setProperty(JLIBTORRENT_JNI_PROPERTY, evil.toAbsolutePath().toString())
                val error = assertThrows<IOException> {
                    ensureExboTorrentNative(cache, bundledExboTorrentLibrary())
                }
                assertEquals(TORRENT_INTEGRITY_ERROR, error.message)
                assertNull(System.getProperty(JLIBTORRENT_JNI_PROPERTY))
                assertEquals(200_000, Files.size(evil))
                assertFalse(Files.exists(cache.resolve(JLIBTORRENT_LIBRARY_NAME)))
            }
        } finally {
            deleteTree(cache)
        }
    }

    @Test
    fun configuredJniPathWithThePinnedHashIsKept() {
        val dir = Files.createTempDirectory("exbo-m2-good-path")
        val cache = Files.createTempDirectory("exbo-m2-unused-cache")
        val bytes = bundledExboTorrentLibrary()
        val file = dir.resolve("checked.dll")
        Files.write(file, bytes)
        try {
            withoutJniPath {
                System.setProperty(JLIBTORRENT_JNI_PROPERTY, file.toAbsolutePath().toString())
                ensureExboTorrentNative(cache, byteArrayOf(9))
                assertEquals(file.toAbsolutePath().toString(), System.getProperty(JLIBTORRENT_JNI_PROPERTY))
                assertFalse(Files.exists(cache.resolve(JLIBTORRENT_LIBRARY_NAME)))
            }
        } finally {
            deleteTree(dir)
            deleteTree(cache)
        }
    }

    @Test
    fun badBundleIsNotExtracted() {
        val cache = Files.createTempDirectory("exbo-m2-bundle")
        try {
            withoutJniPath {
                val error = assertThrows<IOException> {
                    ensureExboTorrentNative(cache, ByteArray(120_000) { 7 })
                }
                assertEquals(TORRENT_INTEGRITY_ERROR, error.message)
                assertNull(System.getProperty(JLIBTORRENT_JNI_PROPERTY))
                assertEquals(0, Files.list(cache).use { it.count() })
            }
        } finally {
            deleteTree(cache)
        }
    }

    @Test
    fun torrentLibraryIsNotExtractedThroughALinkedCacheParent() {
        val base = Files.createTempDirectory("exbo-m2-parent")
        val outside = Files.createTempDirectory("exbo-m2-out")
        try {
            Files.createSymbolicLink(base.resolve("link"), outside)
            val cache = base.resolve("link").resolve("native")
            withoutJniPath {
                val error = assertThrows<IOException> {
                    ensureExboTorrentNative(cache, bundledExboTorrentLibrary())
                }
                assertEquals(TORRENT_INTEGRITY_ERROR, error.message)
                assertNull(System.getProperty(JLIBTORRENT_JNI_PROPERTY))
                assertFalse(Files.exists(outside.resolve("native").resolve(JLIBTORRENT_LIBRARY_NAME)))
            }
        } finally {
            deleteTree(base)
            deleteTree(outside)
        }
    }

    @Test
    fun symlinkToTheRealLibraryIsNotLoadedInPlace() {
        val cache = Files.createTempDirectory("exbo-m2-link")
        val bytes = bundledExboTorrentLibrary()
        val real = cache.resolve("real.dll")
        val link = cache.resolve(JLIBTORRENT_LIBRARY_NAME)
        Files.write(real, bytes)
        Files.createSymbolicLink(link, real.fileName)
        try {
            withoutJniPath {
                ensureExboTorrentNative(cache, bytes)
                assertFalse(Files.isSymbolicLink(link))
                assertTrue(torrentNativeMatches(link))
                assertEquals(link.toAbsolutePath().toString(), System.getProperty(JLIBTORRENT_JNI_PROPERTY))
            }
        } finally {
            deleteTree(cache)
        }
    }

    private fun writeFakeJre(root: Path, exe: String, dll: String) {
        val bin = root.resolve("java").resolve("bin")
        Files.createDirectories(bin)
        Files.writeString(bin.resolve("java.exe"), exe)
        Files.writeString(bin.resolve("jmt.dll"), dll)
    }

    private fun <T> withoutJniPath(block: () -> T): T {
        val previous = System.getProperty(JLIBTORRENT_JNI_PROPERTY)
        try {
            System.clearProperty(JLIBTORRENT_JNI_PROPERTY)
            return block()
        } finally {
            if (previous == null) System.clearProperty(JLIBTORRENT_JNI_PROPERTY)
            else System.setProperty(JLIBTORRENT_JNI_PROPERTY, previous)
        }
    }
}
