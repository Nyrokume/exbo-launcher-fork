package dev.launcherfork.data.exbo

import java.nio.file.Path
import java.util.Base64

/**
 * `UpdatingSubController.parseArgs`: split on spaces, quotes toggle a quoted span and are not kept.
 * A space outside quotes always ends a token, including an empty one.
 */
internal fun parseRunArgs(command: String): List<String> {
    val out = ArrayList<String>()
    val current = StringBuilder()
    var quoted = false
    for (ch in command) {
        when {
            ch == '"' -> quoted = !quoted
            !quoted && ch == ' ' -> {
                out += current.toString()
                current.clear()
            }
            else -> current.append(ch)
        }
    }
    if (current.isNotEmpty()) out += current.toString()
    return out
}

/**
 * `UpdatingSubController.bakeRunCommand`, in the official order:
 * `<java>` (client dir + arch folder), `<cd>`, `<usr>`, `<sess>`, `<mem>`,
 * then `/` to `\`, then `<dlogin>` (Base64 of the login in UTF-16, BOM included).
 * The launcher token is not an input.
 */
internal fun bakeRunCommand(
    args: List<String>,
    clientDir: Path,
    login: String,
    session: String,
    memMb: Int,
    archFolder: String = if (System.getProperty("os.arch").contains("64")) "win64" else "win32",
): List<String> {
    val java = clientDir.resolve(archFolder).toAbsolutePath().toString()
    val cd = clientDir.toAbsolutePath().toString()
    val mem = memMb.toString()
    val dlogin = Base64.getEncoder().encodeToString(login.toByteArray(Charsets.UTF_16))
    return args.map { arg ->
        arg.replace("<java>", java)
            .replace("<cd>", cd)
            .replace("<usr>", login)
            .replace("<sess>", session)
            .replace("<mem>", mem)
            .replace("/", "\\")
            .replace("<dlogin>", dlogin)
    }
}
