package dev.launcherfork.data.exbo

import dev.launcherfork.domain.ExboPaths
import dev.launcherfork.stalzone.WindowsRegistry
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.readBytes

/** Thrown when every launcher host failed. The message never includes the request (it can carry a password or token). */
internal class ExboTransportException(message: String) : IOException(message)

/**
 * The installed EXBO launcher's backend: registry session, bootstrap hash, HWID from the 32-bit JRE,
 * then `/auth` and `/listServers` on the hosts in [ExboHosts].
 */
class ExboApi(
    private val root: Path = ExboPaths.roamingRoot(),
    private val registry: WindowsRegistry = WindowsRegistry(),
) {
    @Volatile var token: String? = null
        private set

    @Volatile var accountLogin: String? = null
        private set

    /** Raw `/listServers` body, filled on the auth thread before the UI asks for cards. */
    @Volatile var serverListJson: String? = null
        private set

    fun installed(): Boolean =
        root.resolve("bootstrap.jar").exists() &&
            root.resolve("launcher").resolve("launcher.jar").exists() &&
            root.resolve("java").resolve("bin").resolve("java.exe").exists()

    /** Login stored by the working launcher, if auto-auth was used before. */
    fun savedLogin(): String? = registry.readValues(EXBO_KEY)["EXBO_LOGIN"]?.takeIf { it.isNotBlank() }

    /**
     * Session login the working client runs on startup (`state=false`).
     * Returns the auth `type` (`OK`, `NEW_PLACE`, …). A token is kept only for `OK`.
     */
    fun autoAuth(): String {
        val values = registry.readValues(EXBO_KEY)
        val login = values["EXBO_LOGIN"]?.takeIf { it.isNotBlank() } ?: return "NO_SESSION"
        val session = values["EXBO_SESSION"]?.takeIf { it.isNotBlank() } ?: return "NO_SESSION"
        val reply = call(exboSessionPath(login, session, bootstrap(), hwid(), userId()))
        accept(reply, login)
        return reply.type
    }

    internal fun passwordAuth(login: String, password: String): ExboAuthReply {
        var reply = call(exboPasswordPath(login, password, bootstrap(), hwid(), userId()))
        repeat(3) {
            val secret = reply.secretHash
            reply = when {
                reply.type == "POW_BCRYPT" && !secret.isNullOrEmpty() ->
                    solved {
                        val hash = exboPowPassHash(password, secret, root.resolve("launcher").resolve("launcher.jar"))
                        exboPasswordHashPath(login, password, hash, bootstrap(), hwid(), userId())
                    }
                reply.type == "NEED_SECRET" && !secret.isNullOrEmpty() ->
                    solved {
                        exboSecretPath(login, exboReverseAnswer(secret), bootstrap(), hwid(), userId())
                    }
                else -> return@repeat
            } ?: return@repeat
        }
        accept(reply, login)
        return reply
    }

    private fun solved(path: () -> String): ExboAuthReply? =
        try {
            call(path())
        } catch (e: Exception) {
            System.err.println("[launcher-fork] auth_step=${e.javaClass.simpleName}")
            null
        }

    /** Code from the authenticator app. The query is `secret`, not the password-step `answer`. */
    internal fun totpAuth(login: String, code: String): ExboAuthReply {
        val reply = call(exboTotpPath(login, code, userId()))
        accept(reply, login)
        return reply
    }

    /** Code from the confirmation e-mail. */
    internal fun mailCodeAuth(login: String, code: String): ExboAuthReply {
        val reply = call(exboMailCodePath(login, code, hwid(), userId()))
        accept(reply, login)
        return reply
    }

    /** Writes the new session the way the official client does, and only after a successful auth. */
    fun remember(login: String, session: String?) {
        val values = linkedMapOf("EXBO_AUTO_AUTH" to "true", "EXBO_LOGIN" to login)
        if (!session.isNullOrBlank()) values["EXBO_SESSION"] = session
        registry.writeValues(EXBO_KEY, values)
    }

    /** The server list drops its cache here. One listener is enough: the repository registers itself. */
    internal var onTokenCleared: (() -> Unit)? = null

    fun clearToken() {
        token = null
        accountLogin = null
        serverListJson = null
        onTokenCleared?.invoke()
    }

    /**
     * Game session from `GET /joinGame`. The body is the session the process receives.
     * It is not logged and it is not the launcher token.
     */
    fun joinGame(login: String): String? {
        val current = token ?: return null
        if (login.isBlank()) return null
        return try {
            val body = get(exboJoinPath(current, login))
            if (token != current) null else body.takeIf { it.isNotBlank() }
        } catch (_: IOException) {
            null
        }
    }

    /**
     * File list for an installed client (`GET /torrentData`). The body is the launcher's own binary catalog,
     * not logged. Requires the token from a successful auth.
     */
    fun torrentData(id: String): ByteArray {
        require(SAFE_ID.matches(id)) { "bad id" }
        val current = token ?: throw ExboTransportException("no session")
        val login = accountLogin ?: throw ExboTransportException("no session")
        return getBytes(exboTorrentPath(current, login, id), accept = null)
    }

    /** One `/listServers` refetch. Does nothing without a token. The body is not logged. */
    fun refreshServers() {
        val current = token ?: return
        val login = accountLogin ?: return
        val body = get(exboListPath(current, login))
        if (token == current) serverListJson = body
    }

    private fun accept(reply: ExboAuthReply, login: String) {
        if (reply.type == "OK" && reply.token != null) {
            token = reply.token
            accountLogin = reply.trueLogin ?: login
            val created = generatedId
            if (created != null) {
                registry.writeValues("SOFTWARE", mapOf("user_id" to created))
                generatedId = null
            }
            try {
                serverListJson = get(exboListPath(reply.token, accountLogin!!))
            } catch (_: IOException) {
                serverListJson = null
            }
        }
    }

    private fun call(path: String): ExboAuthReply {
        val body = get(path)
        val reply = parseAuthReply(body)
        val obj = parseJson(body) as? Json.Obj
        fun len(name: String) = obj?.text(name)?.length ?: -1
        val urlHost = obj?.text("loginURL")?.let { raw ->
            try {
                val uri = java.net.URI(raw)
                listOf(uri.scheme, uri.host, uri.path).filterNotNull().joinToString("")
            } catch (_: Exception) {
                "bad"
            }
        } ?: ""
        System.err.println(
            "[launcher-fork] auth_type=${reply.type} token=${reply.token != null} " +
                "secretLen=${len("secretHash")} sessionLen=${len("session")} loginLen=${len("trueLogin")} " +
                "errLen=${len("genericError")} time=${obj?.int("timeLeft")} url=$urlHost",
        )
        return reply
    }

    private fun get(path: String): String = getBytes(path, accept = "application/json").toString(Charsets.UTF_8)

    private fun getBytes(path: String, accept: String?): ByteArray {
        var last: Exception? = null
        for (base in ExboHosts.all) {
            try {
                return request(base, path, accept)
            } catch (e: IOException) {
                last = e
            }
        }
        throw ExboTransportException(last?.javaClass?.simpleName ?: "no endpoint")
    }

    private fun request(base: String, path: String, accept: String?): ByteArray {
        val conn = (URI(base + path).toURL().openConnection() as HttpURLConnection)
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        if (accept != null) conn.setRequestProperty("Accept", accept)
        try {
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val body = stream?.readBytes() ?: ByteArray(0)
            if (code >= 400) throw ExboTransportException("http $code")
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun bootstrap(): String {
        cachedHash?.let { return it }
        val hash = exboSha1Hex(root.resolve("bootstrap.jar").readBytes())
        cachedHash = hash
        return hash
    }

    private fun userId(): String {
        cachedId?.let { return it }
        val existing = registry.readValues("SOFTWARE")["user_id"]?.takeIf { it.isNotBlank() }
        if (existing != null) {
            cachedId = existing
            return existing
        }
        val created = UUID.randomUUID().toString()
        generatedId = created
        cachedId = created
        return created
    }

    /**
     * The native HWID lives in the 32-bit `jmt.dll`. The installed `java.exe` is a stub that only
     * runs `../launcher/args_new.txt` next to its own exe, so the helper uses a copy of that JRE
     * and never touches the real launcher arguments. The copy is checked against the installed tree
     * before the process starts; a mismatch does not run `java.exe`.
     */
    private fun hwid(): String {
        cachedHwid?.let { return it }
        val work = try {
            jreLayout(root)
        } catch (_: IOException) {
            throw ExboTransportException(JRE_INTEGRITY_ERROR)
        }
        try {
            val classDir = try {
                realWorkDirectory(work, "classes")
            } catch (_: IOException) {
                throw ExboTransportException(JRE_INTEGRITY_ERROR)
            }
            val launcherDir = try {
                realWorkDirectory(work, "launcher")
            } catch (_: IOException) {
                throw ExboTransportException(JRE_INTEGRITY_ERROR)
            }
            val bytes = ExboApi::class.java.getResourceAsStream("/exbo/HwidDump.class")?.readBytes()
                ?: throw ExboTransportException("hwid helper missing")
            Files.write(classDir.resolve("HwidDump.class"), bytes)
            val javaExe = work.resolve("java").resolve("bin").resolve("java.exe")
            val cp = listOf(
                classDir,
                root.resolve("launcher").resolve("launcher.jar"),
                root.resolve("bootstrap.jar"),
            ).joinToString(";") { it.toString() }
            val native = root.resolve("launcher").resolve("native")
            val args = "-Djava.library.path=$native -Djava.class.path=$cp -Djava.net.preferIPv4Stack=true HwidDump"
            Files.writeString(launcherDir.resolve("args_new.txt"), args)
            if (!jreTreeMatches(root.resolve("java"), work.resolve("java"))) {
                throw ExboTransportException(JRE_INTEGRITY_ERROR)
            }
            val proc = ProcessBuilder(javaExe.toString())
                .directory(work.resolve("java").toFile())
                .redirectErrorStream(false)
                .start()
            val output = StringBuilder()
            val errors = StringBuilder()
            val reader = Thread { output.append(proc.inputStream.reader(Charsets.UTF_8).readText()) }
            val errReader = Thread { errors.append(proc.errorStream.reader(Charsets.UTF_8).readText()) }
            reader.isDaemon = true
            errReader.isDaemon = true
            reader.start()
            errReader.start()
            val finished = proc.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) proc.destroyForcibly()
            reader.join(2_000)
            errReader.join(2_000)
            val line = (output.lineSequence() + errors.lineSequence()).firstOrNull { it.matches(HEX) }
                ?: throw ExboTransportException(hwidFailure(output.toString() + errors.toString()))
            cachedHwid = line
            return line
        } finally {
            deleteTree(work.resolve("classes"))
        }
    }

    private fun hwidFailure(text: String): String {
        val safe = text.lineSequence()
            .map { it.replace(HEX, "hex") }
            .filter { it.isNotBlank() }
            .take(8)
            .joinToString(" | ")
        return if (safe.isBlank()) "hwid unavailable" else safe.take(400)
    }

    private var cachedHash: String? = null
    private var cachedId: String? = null
    private var generatedId: String? = null
    private var cachedHwid: String? = null

    private companion object {
        const val EXBO_KEY = "SOFTWARE\\EXBO"
        val HEX = Regex("[0-9a-fA-F]{32,}")
        val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
    }
}
