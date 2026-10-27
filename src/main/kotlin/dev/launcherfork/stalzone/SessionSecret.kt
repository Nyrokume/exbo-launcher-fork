package dev.launcherfork.stalzone

import java.io.IOException
import java.util.Base64

/** A sealed session blob could not be opened. Callers must not treat the ciphertext as a token. */
class SessionSecretException : IOException("session unreadable")

/**
 * Protects `EXBO_SESSION` at rest in the fork's `sessions.json`.
 *
 * The HKCU value stays the raw session string. `exboSessionPath` sends that string as `session=`
 * on the official query, and the installed launcher reads the same registry value. Sealing HKCU
 * would send ciphertext to `/auth` and would sign the official client out. That query shape is
 * the EXBO protocol already implemented in `ExboProtocol`; it is not changed here.
 */
interface SessionSecretProtector {
    /** Returns the value to write into `sessions.json`. */
    fun protect(plain: String): String

    /** Returns the session string. A sealed blob that does not open throws [SessionSecretException]. */
    fun unprotect(stored: String): String
}

internal const val SESSION_SECRET_PREFIX = "dpapi1:"

/**
 * Seals bytes with [seal] and stores `dpapi1:` plus base64.
 * A value without the prefix is a legacy plaintext snapshot and is returned as-is, so an older
 * `sessions.json` still opens. The next save writes the prefix.
 */
class SealedSessionSecrets(
    private val seal: (ByteArray) -> ByteArray,
    private val open: (ByteArray) -> ByteArray,
) : SessionSecretProtector {
    override fun protect(plain: String): String {
        if (plain.isEmpty()) return plain
        if (plain.startsWith(SESSION_SECRET_PREFIX)) throw SessionSecretException()
        val sealed = seal(plain.toByteArray(Charsets.UTF_8))
        return SESSION_SECRET_PREFIX + Base64.getEncoder().encodeToString(sealed)
    }

    override fun unprotect(stored: String): String {
        if (!stored.startsWith(SESSION_SECRET_PREFIX)) return stored
        val raw = try {
            Base64.getDecoder().decode(stored.removePrefix(SESSION_SECRET_PREFIX))
        } catch (_: IllegalArgumentException) {
            throw SessionSecretException()
        }
        val plain = try {
            open(raw)
        } catch (_: SessionSecretException) {
            throw SessionSecretException()
        } catch (_: Exception) {
            throw SessionSecretException()
        }
        val text = plain.toString(Charsets.UTF_8)
        if (text.startsWith(SESSION_SECRET_PREFIX)) throw SessionSecretException()
        return text
    }
}

/** Test and mock desk. Refuses to present a `dpapi1:` blob as a session token. */
object PlainSessionSecrets : SessionSecretProtector {
    override fun protect(plain: String): String {
        if (plain.startsWith(SESSION_SECRET_PREFIX)) throw SessionSecretException()
        return plain
    }

    override fun unprotect(stored: String): String {
        if (stored.startsWith(SESSION_SECRET_PREFIX)) throw SessionSecretException()
        return stored
    }
}

/**
 * Used when DPAPI is not available. A new session token is not written in the clear.
 * A legacy plaintext snapshot still loads.
 */
object RefusingSessionSecrets : SessionSecretProtector {
    override fun protect(plain: String): String {
        if (plain.isEmpty()) return plain
        throw SessionSecretException()
    }

    override fun unprotect(stored: String): String {
        if (stored.startsWith(SESSION_SECRET_PREFIX)) throw SessionSecretException()
        return stored
    }
}

object SessionSecrets {
    /** Windows seals with DPAPI (current user). Any other OS refuses to persist a new token. */
    fun platform(): SessionSecretProtector =
        if (System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true)) {
            SealedSessionSecrets(WindowsDpapi::protect, WindowsDpapi::unprotect)
        } else {
            RefusingSessionSecrets
        }
}

/**
 * DPAPI via `ProtectedData` (current user), which is the Windows Credential store primitive
 * already used for per-user secrets. The session bytes go to the child process on stdin.
 * They are not placed on the command line. A failure throws [SessionSecretException] and does
 * not fall back to plaintext.
 */
internal object WindowsDpapi {
    private const val ENTROPY = "launcher-fork-exbo-session"

    internal val script: String =
        "\$ErrorActionPreference = 'Stop'\n" +
            "Add-Type -AssemblyName System.Security\n" +
            "\$b64 = [Console]::In.ReadToEnd().Trim()\n" +
            "\$data = [Convert]::FromBase64String(\$b64)\n" +
            "\$entropy = [Text.Encoding]::UTF8.GetBytes('$ENTROPY')\n" +
            "\$op = [Environment]::GetEnvironmentVariable('EXBO_DPAPI_OP')\n" +
            "if (\$op -eq 'protect') {\n" +
            "  \$out = [Security.Cryptography.ProtectedData]::Protect(\$data, \$entropy, 'CurrentUser')\n" +
            "} elseif (\$op -eq 'unprotect') {\n" +
            "  \$out = [Security.Cryptography.ProtectedData]::Unprotect(\$data, \$entropy, 'CurrentUser')\n" +
            "} else {\n" +
            "  throw 'session unreadable'\n" +
            "}\n" +
            "[Console]::Out.Write([Convert]::ToBase64String(\$out))\n"

    internal fun command(): List<String> = listOf(
        "powershell.exe",
        "-NoProfile",
        "-NonInteractive",
        "-Command",
        script,
    )

    fun protect(plain: ByteArray): ByteArray = invoke("protect", plain)

    fun unprotect(blob: ByteArray): ByteArray = invoke("unprotect", blob)

    private fun invoke(op: String, bytes: ByteArray): ByteArray {
        check(op == "protect" || op == "unprotect")
        val proc = ProcessBuilder(command())
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .apply { environment()["EXBO_DPAPI_OP"] = op }
            .start()
        proc.outputStream.use { it.write(Base64.getEncoder().encode(bytes)) }
        val stdout = proc.inputStream.readBytes()
        val code = proc.waitFor()
        if (code != 0 || stdout.isEmpty()) throw SessionSecretException()
        return try {
            Base64.getDecoder().decode(stdout.toString(Charsets.US_ASCII).trim())
        } catch (_: IllegalArgumentException) {
            throw SessionSecretException()
        }
    }
}
