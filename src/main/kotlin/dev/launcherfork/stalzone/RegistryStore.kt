package dev.launcherfork.stalzone

import java.nio.charset.Charset

/** HKCU string values. The Windows store shells out to `reg.exe` with a direct argument list. */
interface RegistryStore {
    fun readValues(path: String): Map<String, String>
    fun writeValues(path: String, values: Map<String, String>)
    fun deleteValue(path: String, name: String)
}

class MemoryRegistry : RegistryStore {
    val keys = mutableMapOf<String, MutableMap<String, String>>()

    override fun readValues(path: String): Map<String, String> = keys[path].orEmpty().toMap()

    override fun writeValues(path: String, values: Map<String, String>) {
        keys.getOrPut(path) { linkedMapOf() }.putAll(values)
    }

    override fun deleteValue(path: String, name: String) {
        keys[path]?.remove(name)
    }
}

class WindowsRegistry : RegistryStore {
    override fun readValues(path: String): Map<String, String> {
        val proc = ProcessBuilder("reg", "query", "HKCU\\$path")
            .redirectErrorStream(true)
            .start()
        val text = proc.inputStream.readBytes().toString(Charset.defaultCharset())
        val code = proc.waitFor()
        if (code != 0) return emptyMap()
        val out = linkedMapOf<String, String>()
        for (line in text.lineSequence()) {
            val parts = line.trim().split(Regex("\\s{2,}"))
            if (parts.size >= 3 && parts[1].startsWith("REG_")) out[parts[0]] = parts.drop(2).joinToString("  ")
        }
        return out
    }

    override fun writeValues(path: String, values: Map<String, String>) {
        for ((name, value) in values) {
            val proc = ProcessBuilder(
                "reg", "add", "HKCU\\$path", "/v", name, "/t", "REG_SZ", "/d", value, "/f",
            ).redirectErrorStream(true).start()
            val text = proc.inputStream.readBytes().toString(Charset.defaultCharset())
            val code = proc.waitFor()
            if (code != 0) error("reg add $path\\$name failed ($code): $text")
        }
    }

    override fun deleteValue(path: String, name: String) {
        val proc = ProcessBuilder("reg", "delete", "HKCU\\$path", "/v", name, "/f")
            .redirectErrorStream(true)
            .start()
        proc.inputStream.readBytes()
        proc.waitFor()
    }
}
