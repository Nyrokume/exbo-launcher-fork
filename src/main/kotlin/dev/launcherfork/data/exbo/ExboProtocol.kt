package dev.launcherfork.data.exbo

import dev.launcherfork.domain.ExboPaths
import dev.launcherfork.domain.GameDescription
import dev.launcherfork.domain.Server
import java.math.BigInteger
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * Hosts from the installed launcher's `launcher_config.properties` (RU realm).
 * The working client tries the https addresses, then the http ones.
 */
internal object ExboHosts {
    val https = listOf(
        "https://launcher.stalcraft.net:443",
        "https://launcher.stalcraftx.ru:443",
        "https://launcher.stalcraft-tech.net:443",
    )
    val http = listOf(
        "http://launcher.stalcraft.net:80",
        "http://launcher.stalcraftx.ru:80",
        "http://launcher.stalcraft-tech.net:80",
    )
    val all = https + http
}

/** Same SHA-1 hex the official client sends: `BigInteger(1, sha1).toString(16)`, leading zeros dropped. */
internal fun exboSha1Hex(bytes: ByteArray): String =
    BigInteger(1, MessageDigest.getInstance("SHA-1").digest(bytes)).toString(16)

internal fun exboEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

/**
 * Password login. Parameter order matches `InitialAuthController.auth(login, pass, start)`.
 * [password] is the text the user typed; it is only placed in this request, never logged.
 */
internal fun exboPasswordPath(login: String, password: String, bootstrap: String, hwid: String, id: String): String =
    "/auth?state=true" +
        "&login=${exboEncode(login)}" +
        "&pass=${exboEncode(password)}" +
        "&bootstrap=$bootstrap" +
        "&hwid=${exboEncode(hwid)}" +
        "&id=${exboEncode(id)}" +
        "&protocol_version=3&start=true"

/**
 * Follow-up after `POW_BCRYPT`. Same fields as the password request, plus `passhash`, and no `start`.
 * [passHash] is the bcrypt string, not the password.
 */
internal fun exboPasswordHashPath(
    login: String,
    password: String,
    passHash: String,
    bootstrap: String,
    hwid: String,
    id: String,
): String =
    "/auth?state=true" +
        "&login=${exboEncode(login)}" +
        "&pass=${exboEncode(password)}" +
        "&passhash=${exboEncode(passHash)}" +
        "&bootstrap=$bootstrap" +
        "&hwid=${exboEncode(hwid)}" +
        "&id=${exboEncode(id)}" +
        "&protocol_version=3"

/** Answer to `NEED_SECRET`: the 5-digit string the working client finds, not the password. */
internal fun exboSecretPath(login: String, answer: String, bootstrap: String, hwid: String, id: String): String =
    "/auth?state=true" +
        "&login=${exboEncode(login)}" +
        "&answer=${exboEncode(answer)}" +
        "&bootstrap=$bootstrap" +
        "&hwid=${exboEncode(hwid)}" +
        "&id=${exboEncode(id)}" +
        "&new_protocol=true&protocol_version=3"

/** Saved-session login. `id` comes after `protocol_version`, unlike the password request. */
internal fun exboSessionPath(login: String, session: String, bootstrap: String, hwid: String, id: String): String =
    "/auth?state=false" +
        "&login=${exboEncode(login)}" +
        "&session=${exboEncode(session)}" +
        "&bootstrap=$bootstrap" +
        "&hwid=${exboEncode(hwid)}" +
        "&protocol_version=3" +
        "&id=${exboEncode(id)}"

/**
 * Authenticator code. Official `GoogleAuthController` sends `secret`, then login and id.
 * No bootstrap, hwid, or `state`: those belong to the password request.
 */
internal fun exboTotpPath(login: String, secret: String, id: String): String =
    "/auth?secret=${exboEncode(secret)}" +
        "&login=${exboEncode(login)}" +
        "&id=${exboEncode(id)}" +
        "&protocol_version=3"

/** E-mail code. Official `MailAuthController` sends `code`, then login, id, and hwid. */
internal fun exboMailCodePath(login: String, code: String, hwid: String, id: String): String =
    "/auth?code=${exboEncode(code)}" +
        "&login=${exboEncode(login)}" +
        "&id=${exboEncode(id)}" +
        "&protocol_version=3" +
        "&hwid=${exboEncode(hwid)}"

internal fun exboListPath(token: String, login: String): String =
    "/listServers?token=${exboEncode(token)}&login=${exboEncode(login)}"

/** Session the game process receives. The launcher token stays in this query and is not logged. */
internal fun exboJoinPath(token: String, login: String): String =
    "/joinGame?token=${exboEncode(token)}&login=${exboEncode(login)}&protocol_version=3"

internal data class ExboAuthReply(
    val type: String,
    val token: String?,
    val session: String?,
    val trueLogin: String?,
    val secretHash: String? = null,
    val timeLeft: Int? = null,
)

internal fun parseAuthReply(body: String): ExboAuthReply {
    val json = parseJson(body)
    return ExboAuthReply(
        type = json.text("type") ?: "GENERIC_ERROR",
        token = json.text("token")?.takeIf { it.isNotEmpty() },
        session = json.text("session")?.takeIf { it.isNotEmpty() },
        trueLogin = json.text("trueLogin")?.takeIf { it.isNotEmpty() },
        secretHash = json.text("secretHash")?.takeIf { it.isNotEmpty() },
        timeLeft = json.int("timeLeft")?.takeIf { it >= 0 },
    )
}

/** `GET /torrentData`: the file list for one installed client. The token stays in the query and is not logged. */
internal fun exboTorrentPath(token: String, login: String, id: String): String =
    "/torrentData?id=${exboEncode(id)}" +
        "&token=${exboEncode(token)}" +
        "&login=${exboEncode(login)}"

/** Servers plus the launch command, version, web seeds and exclusion prefixes. Those stay off [Server]. */
internal data class ServerCatalog(
    val servers: List<Server>,
    val runCommands: Map<String, String>,
    val versions: Map<String, String>,
    val httpSeeds: Map<String, List<String>> = emptyMap(),
    val exclusions: Map<String, List<String>> = emptyMap(),
)

internal fun parseServerList(body: String, root: Path = ExboPaths.roamingRoot()): List<Server> =
    parseServerCatalog(body, root).servers

/**
 * `listServers` returns `TokenedResponse`, whose `data` is a JSON string of [ClientServerEntry] objects.
 * A raw array is accepted too, so a fixture can skip the wrapper.
 * `runCommand` and `version` stay beside [Server]: the domain card has no field for them.
 */
internal fun parseServerCatalog(body: String, root: Path = ExboPaths.roamingRoot()): ServerCatalog {
    val json = parseJson(body)
    val entries = when (json) {
        is Json.Arr -> json
        is Json.Obj -> when (val data = json.field("data")) {
            is Json.Arr -> data
            is Json.Str -> parseJson(data.text) as? Json.Arr ?: return ServerCatalog(emptyList(), emptyMap(), emptyMap())
            else -> return ServerCatalog(emptyList(), emptyMap(), emptyMap())
        }
        else -> return ServerCatalog(emptyList(), emptyMap(), emptyMap())
    }
    val servers = mutableListOf<Server>()
    val commands = linkedMapOf<String, String>()
    val versions = linkedMapOf<String, String>()
    val seeds = linkedMapOf<String, List<String>>()
    val exclusions = linkedMapOf<String, List<String>>()
    for (item in entries.items) {
        val entry = item as? Json.Obj ?: continue
        val server = serverFrom(entry, root) ?: continue
        servers += server
        entry.text("runCommand")?.takeIf { it.isNotEmpty() }?.let { commands[server.id] = it }
        entry.text("version")?.takeIf { it.isNotEmpty() }?.let { versions[server.id] = it }
        entry.strings("httpSeeds").filter { it.startsWith("https://") || it.startsWith("http://") }
            .takeIf { it.isNotEmpty() }?.let { seeds[server.id] = it }
        entry.strings("exclusions").filter { it.isNotBlank() }
            .takeIf { it.isNotEmpty() }?.let { exclusions[server.id] = it }
    }
    return ServerCatalog(servers, commands, versions, seeds, exclusions)
}

private fun serverFrom(entry: Json.Obj, root: Path): Server? {
    val id = entry.text("id")?.takeIf { it.isNotEmpty() } ?: return null
    val caption = entry.text("caption")?.takeIf { it.isNotEmpty() } ?: id
    val players = entry.int("onlineCurrent") ?: -1
    val seeds = entry.strings("httpSeeds")
    val desc = entry.strings("desc")
    val clientDir = root.resolve("runtime").resolve(id)
    return Server(
        id = id,
        caption = caption,
        online = players >= 0,
        players = players,
        description = description(caption, desc),
        iconUri = art(entry.text("icon"), seeds, ICON_FALLBACK),
        backgroundUri = art(entry.text("background"), seeds, BACKGROUND_FALLBACK),
        installed = clientDir.isDirectory() && clientDir.resolve("classes.jar").exists(),
        clientSizeBytes = entry.long("totalSize")?.coerceAtLeast(0) ?: 0L,
    )
}

private fun description(caption: String, desc: List<String>): GameDescription.Text {
    val closing = if (desc.size >= 4) desc.last() else ""
    val body = when {
        desc.size >= 4 -> desc.subList(2, desc.size - 1).joinToString("\n")
        desc.size >= 3 -> desc.drop(2).joinToString("\n")
        else -> ""
    }
    return GameDescription.Text(
        title = caption,
        lead = desc.getOrElse(0) { "" },
        subheading = desc.getOrElse(1) { "" },
        body = body,
        closing = closing,
    )
}

private fun art(path: String?, seeds: List<String>, fallback: String): URI {
    val raw = path?.trim().orEmpty()
    val candidate = when {
        raw.startsWith("https://") || raw.startsWith("http://") -> raw
        raw.isNotEmpty() && seeds.isNotEmpty() -> seeds.first().trimEnd('/') + "/" + raw.trimStart('/')
        else -> null
    }
    if (candidate != null) {
        try {
            return URI(candidate)
        } catch (_: IllegalArgumentException) {
            // fall through to the bundled art
        }
    }
    return requireNotNull(ExboHosts::class.java.getResource(fallback)?.toURI()) { "Missing $fallback" }
}

private const val ICON_FALLBACK = "/dev/launcherfork/mock/servers/game-stalzone.png"
private const val BACKGROUND_FALLBACK = "/dev/launcherfork/mock/servers/stalcraft_x_bg.jpg"
