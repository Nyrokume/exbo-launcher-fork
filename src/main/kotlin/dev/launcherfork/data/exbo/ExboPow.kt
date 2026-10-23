package dev.launcherfork.data.exbo

import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Password proof the working launcher sends after `/auth` answers `POW_BCRYPT`.
 * The password is hashed in memory and placed only in the follow-up request.
 */
internal fun exboPasswordMaterial(password: String): String {
    val first = exboHexo(exboMd5(password.toByteArray(Charsets.UTF_8)))
    return exboHexo(exboMd5(first.toByteArray(Charsets.UTF_8)))
}

internal fun exboPowPassHash(password: String, secretHash: String, launcherJar: Path): String {
    val copy = Files.createTempFile("exbo-bcrypt-", ".jar")
    try {
        Files.copy(launcherJar, copy, StandardCopyOption.REPLACE_EXISTING)
        URLClassLoader(arrayOf(copy.toUri().toURL()), ClassLoader.getSystemClassLoader()).use { loader ->
            val radix = loader.loadClass("at.favre.lib.crypto.bcrypt.Radix64Encoder\$Default")
                .getDeclaredConstructor()
                .newInstance()
            val salt = radix.javaClass.getMethod("decode", ByteArray::class.java)
                .invoke(radix, secretHash.toByteArray(Charsets.UTF_8)) as ByteArray
            val versionClass = loader.loadClass("at.favre.lib.crypto.bcrypt.BCrypt\$Version")
            val version = versionClass.getField("VERSION_2Y").get(null)
            val hasher = loader.loadClass("at.favre.lib.crypto.bcrypt.BCrypt")
                .getMethod("with", versionClass)
                .invoke(null, version)
            val hashed = hasher.javaClass
                .getMethod("hash", Int::class.javaPrimitiveType, ByteArray::class.java, ByteArray::class.java)
                .invoke(hasher, 10, salt, exboPasswordMaterial(password).toByteArray(Charsets.UTF_8)) as ByteArray
            return String(hashed, Charsets.UTF_8)
        }
    } finally {
        runCatching { Files.deleteIfExists(copy) }
    }
}

internal fun exboMd5(bytes: ByteArray): ByteArray = MessageDigest.getInstance("MD5").digest(bytes)

internal fun exboReverseAnswer(secretHash: String): String {
    repeat(100_000) { index ->
        val candidate = buildString(5) {
            var value = index
            repeat(5) {
                append('0' + (value % 10))
                value /= 10
            }
        }
        if (exboSha1Hex(candidate.toByteArray(Charsets.UTF_8)) == secretHash) return candidate
    }
    throw IllegalStateException("no answer")
}

/** Full lowercase hex, including leading zeros. The official client calls this `hexo`. */
internal fun exboHexo(bytes: ByteArray): String = buildString(bytes.size * 2) {
    for (b in bytes) append("%02x".format(b.toInt() and 0xff))
}
