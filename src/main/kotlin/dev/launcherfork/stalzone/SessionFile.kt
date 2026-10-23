package dev.launcherfork.stalzone

import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Reads and writes the Regedit `sessions.json` array. */
object SessionFile {
    fun load(path: Path): List<ExboSession> {
        if (!Files.exists(path)) return emptyList()
        val text = Files.readString(path).trim()
        if (text.isEmpty()) return emptyList()
        val parsed = MiniJson.parse(text) as? List<*> ?: error("sessions.json is not a list")
        return parsed.map { item ->
            val obj = item as Map<*, *>
            ExboSession(
                id = (obj["id"] as Number).toLong(),
                name = obj["name"] as String,
                createdAt = obj["created_at"] as String,
                data = keyFrom(obj["data"] as Map<*, *>),
            )
        }
    }

    fun save(path: Path, sessions: List<ExboSession>) {
        val parent = path.parent
        if (parent != null) Files.createDirectories(parent)
        val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.writeString(tmp, MiniJson.write(sessions.map { toMap(it) }))
        moveReplacing(tmp, path)
    }

    private fun moveReplacing(from: Path, to: Path) {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: IOException) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun toMap(session: ExboSession): Map<String, Any?> = linkedMapOf(
        "id" to session.id,
        "name" to session.name,
        "created_at" to session.createdAt,
        "data" to keyMap(session.data),
    )

    private fun keyMap(key: ExboKey): Map<String, Any?> = linkedMapOf(
        "name" to key.name,
        "path" to key.path,
        "values" to key.values.map {
            linkedMapOf("name" to it.name, "value" to it.value, "value_type" to it.valueType)
        },
        "subkeys" to key.subkeys.map { keyMap(it) },
    )

    private fun keyFrom(obj: Map<*, *>): ExboKey = ExboKey(
        name = obj["name"] as? String ?: "",
        path = obj["path"] as? String ?: ExboSessions.KEY_PATH,
        values = (obj["values"] as? List<*>).orEmpty().map { raw ->
            val v = raw as Map<*, *>
            ExboValue(
                name = v["name"] as String,
                value = v["value"]?.toString() ?: "",
                valueType = v["value_type"] as? String ?: "REG_SZ",
            )
        },
        subkeys = (obj["subkeys"] as? List<*>).orEmpty().map { keyFrom(it as Map<*, *>) },
    )
}
