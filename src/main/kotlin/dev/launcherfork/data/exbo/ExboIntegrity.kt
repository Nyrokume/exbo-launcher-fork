package dev.launcherfork.data.exbo

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

internal const val JRE_INTEGRITY_ERROR = "jre layout integrity check failed"
internal const val TORRENT_INTEGRITY_ERROR = "torrent library integrity check failed"

/** Lowercase SHA-256 of [bytes], with leading zeros kept. Integrity checks need a fixed hex form. */
internal fun exboSha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return buildString(digest.size * 2) {
        for (byte in digest) append("%02x".format(byte))
    }
}

/** Lowercase SHA-256 of a regular file. The caller has to reject links before this reads the target. */
internal fun exboSha256Hex(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return buildString(digest.digestLength * 2) {
        for (byte in digest.digest()) append("%02x".format(byte))
    }
}
