package dev.launcherfork.data.exbo

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

/**
 * RAM topology, L3, SMBIOS speed, large-page privilege, and CPU / GPU names.
 * Any failed call becomes a zero or a blank name. [HostSystemInfo] then keeps the safe JVM numbers.
 * x64 Windows only. Other systems get an empty [Hardware].
 */
internal object WindowsHardware {
    data class Hardware(
        val cores: Int = 0,
        val l3Mb: Int = 0,
        val memSpeedMts: Int = 0,
        val largePages: Boolean = false,
        val cpuName: String = "",
        val gpuName: String = "",
    )

    fun probe(): Hardware {
        if (!windows64()) return Hardware()
        val native = Native.tryLoad() ?: return Hardware()
        return Hardware(
            cores = call { native.physicalCores() },
            l3Mb = call { native.l3Mb() },
            memSpeedMts = call { native.memSpeedMts() },
            largePages = call { native.largePages() } == 1,
            cpuName = text { native.cpuName() },
            gpuName = text { native.gpuName() },
        )
    }

    private fun windows64(): Boolean {
        val os = System.getProperty("os.name").orEmpty()
        val arch = System.getProperty("os.arch").orEmpty()
        return os.contains("Windows", ignoreCase = true) && (arch == "amd64" || arch == "x86_64")
    }

    private fun call(block: () -> Int): Int = try {
        block().coerceAtLeast(0)
    } catch (_: Throwable) {
        0
    }

    private fun text(block: () -> String): String = try {
        MachineTables.cleanHardwareName(block())
    } catch (_: Throwable) {
        ""
    }

    private class Native private constructor(
        private val kernel: SymbolLookup,
        private val advapi: SymbolLookup,
    ) {
        private val linker = Linker.nativeLinker()

        fun physicalCores(): Int = MachineTables.countProcessorCores(processorInfo(0))

        fun l3Mb(): Int = MachineTables.maxUnifiedL3Mb(processorInfo(2))

        fun memSpeedMts(): Int = MachineTables.smbiosBestMemSpeed(firmwareTable(0x52534D42))

        fun largePages(): Int {
            val minimum = down(kernel, "GetLargePageMinimum", FunctionDescriptor.of(ValueLayout.JAVA_LONG))
            if (invokeLong(minimum) == 0L) return 0
            return if (lockMemoryEnabled()) 1 else 0
        }

        fun cpuName(): String {
            val base = "HARDWARE\\DESCRIPTION\\System\\CentralProcessor"
            for (sub in subkeys(base)) {
                val name = regString("$base\\$sub", "ProcessorNameString")
                if (!name.isNullOrBlank() && !MachineTables.isGenericCpu(name)) return name
            }
            val env = System.getenv("PROCESSOR_IDENTIFIER").orEmpty()
            if (env.isNotBlank() && !MachineTables.isGenericCpu(env)) return env
            return ""
        }

        fun gpuName(): String {
            val base = "SYSTEM\\CurrentControlSet\\Control\\Class\\{4d36e968-e325-11ce-bfc1-08002be10318}"
            val names = subkeys(base)
                .filter { it.length == 4 && it.all { ch -> ch.isDigit() || ch.lowercaseChar() in 'a'..'f' } }
                .mapNotNull { sub ->
                    val desc = regString("$base\\$sub", "DriverDesc") ?: return@mapNotNull null
                    if (!MachineTables.gpuUsable(desc)) return@mapNotNull null
                    sub to desc
                }
                .sortedBy { it.first.toIntOrNull(16) ?: 0 }
            if (names.isNotEmpty()) return names.last().second
            return ""
        }

        private fun processorInfo(relation: Int): ByteArray = Arena.ofConfined().use { arena ->
            val query = down(
                kernel,
                "GetLogicalProcessorInformationEx",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
            )
            val needed = arena.allocate(ValueLayout.JAVA_INT)
            invokeInt(query, relation, MemorySegment.NULL, needed)
            val size = needed.get(ValueLayout.JAVA_INT, 0)
            if (size <= 0) return ByteArray(0)
            val buf = arena.allocate(size.toLong(), 8)
            if (invokeInt(query, relation, buf, needed) == 0) return ByteArray(0)
            val got = needed.get(ValueLayout.JAVA_INT, 0).coerceIn(0, size)
            buf.asSlice(0, got.toLong()).toArray(ValueLayout.JAVA_BYTE)
        }

        private fun firmwareTable(provider: Int): ByteArray = Arena.ofConfined().use { arena ->
            val query = down(
                kernel,
                "GetSystemFirmwareTable",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                ),
            )
            val size = invokeInt(query, provider, 0, MemorySegment.NULL, 0)
            if (size <= 0) return ByteArray(0)
            val buf = arena.allocate(size.toLong(), 8)
            val got = invokeInt(query, provider, 0, buf, size)
            if (got <= 0) return ByteArray(0)
            buf.asSlice(0, got.toLong().coerceAtMost(size.toLong())).toArray(ValueLayout.JAVA_BYTE)
        }

        private fun lockMemoryEnabled(): Boolean = Arena.ofConfined().use { arena ->
            val open = down(
                advapi,
                "OpenProcessToken",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
            )
            val current = down(kernel, "GetCurrentProcess", FunctionDescriptor.of(ValueLayout.JAVA_LONG))
            val close = down(kernel, "CloseHandle", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG))
            val tokenPtr = arena.allocate(ValueLayout.JAVA_LONG)
            if (invokeInt(open, invokeLong(current), 0x0008, tokenPtr) == 0) return false
            val token = tokenPtr.get(ValueLayout.JAVA_LONG, 0)
            try {
                val lookup = down(
                    advapi,
                    "LookupPrivilegeValueW",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
                )
                val luid = arena.allocate(8, 4)
                if (invokeInt(lookup, MemorySegment.NULL, wide(arena, "SeLockMemoryPrivilege"), luid) == 0) return false
                val set = arena.allocate(20, 4)
                set.set(ValueLayout.JAVA_INT, 0, 1)
                set.set(ValueLayout.JAVA_INT, 4, 0)
                set.asSlice(8, 8).copyFrom(luid)
                set.set(ValueLayout.JAVA_INT, 16, 0x00000002)
                val check = down(
                    advapi,
                    "PrivilegeCheck",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
                )
                val result = arena.allocate(ValueLayout.JAVA_INT)
                val ok = invokeInt(check, token, set, result)
                ok != 0 && result.get(ValueLayout.JAVA_INT, 0) != 0
            } finally {
                invokeInt(close, token)
            }
        }

        private fun subkeys(path: String): List<String> {
            for (sam in intArrayOf(KEY_READ or KEY_WOW64_64, KEY_READ, KEY_READ or KEY_WOW64_32)) {
                val names = subkeys(path, sam)
                if (names.isNotEmpty()) return names
            }
            return emptyList()
        }

        private fun subkeys(path: String, sam: Int): List<String> = Arena.ofConfined().use { arena ->
            val key = openKey(arena, path, sam) ?: return emptyList()
            val enumerate = down(
                advapi,
                "RegEnumKeyExW",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
            )
            val close = down(advapi, "RegCloseKey", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG))
            try {
                val names = ArrayList<String>()
                var index = 0
                while (index < 256) {
                    val name = arena.allocate(512, 2)
                    val chars = arena.allocate(ValueLayout.JAVA_INT)
                    chars.set(ValueLayout.JAVA_INT, 0, 255)
                    val status = invokeInt(
                        enumerate,
                        key,
                        index,
                        name,
                        chars,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                    )
                    if (status != 0) break
                    val n = chars.get(ValueLayout.JAVA_INT, 0).coerceIn(0, 255)
                    names += decodeWide(name, n)
                    index++
                }
                names.filter { it.isNotEmpty() }
            } finally {
                invokeInt(close, key)
            }
        }

        private fun regString(path: String, value: String): String? {
            for (sam in intArrayOf(KEY_READ or KEY_WOW64_64, KEY_READ, KEY_READ or KEY_WOW64_32)) {
                val text = regString(path, value, sam)
                if (!text.isNullOrBlank()) return text
            }
            return null
        }

        private fun regString(path: String, value: String, sam: Int): String? = Arena.ofConfined().use { arena ->
            val key = openKey(arena, path, sam) ?: return null
            val query = down(
                advapi,
                "RegQueryValueExW",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
            )
            val close = down(advapi, "RegCloseKey", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG))
            try {
                val name = wide(arena, value)
                val size = arena.allocate(ValueLayout.JAVA_INT)
                if (invokeInt(query, key, name, MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL, size) != 0) {
                    return null
                }
                val bytes = size.get(ValueLayout.JAVA_INT, 0)
                if (bytes <= 0) return null
                val data = arena.allocate(bytes.toLong() + 2, 2)
                size.set(ValueLayout.JAVA_INT, 0, bytes)
                if (invokeInt(query, key, name, MemorySegment.NULL, MemorySegment.NULL, data, size) != 0) return null
                val chars = (size.get(ValueLayout.JAVA_INT, 0) / 2).coerceAtLeast(0)
                decodeWide(data, chars).trim().ifEmpty { null }
            } finally {
                invokeInt(close, key)
            }
        }

        private fun openKey(arena: Arena, path: String, sam: Int): Long? {
            val open = down(
                advapi,
                "RegOpenKeyExW",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                ),
            )
            val out = arena.allocate(ValueLayout.JAVA_LONG)
            val status = invokeInt(open, HKLM, wide(arena, path), 0, sam, out)
            if (status != 0) return null
            return out.get(ValueLayout.JAVA_LONG, 0)
        }

        private fun down(lib: SymbolLookup, name: String, desc: FunctionDescriptor): MethodHandle =
            linker.downcallHandle(lib.find(name).orElseThrow { UnsatisfiedLinkError(name) }, desc)

        private fun invokeInt(handle: MethodHandle, vararg args: Any?): Int {
            val value = handle.invokeWithArguments(*args)
            return (value as? Number)?.toInt() ?: 0
        }

        private fun invokeLong(handle: MethodHandle, vararg args: Any?): Long {
            val value = handle.invokeWithArguments(*args)
            return (value as? Number)?.toLong() ?: 0L
        }

        private fun wide(arena: Arena, text: String): MemorySegment {
            val bytes = (text + "\u0000").toByteArray(Charsets.UTF_16LE)
            val seg = arena.allocate(bytes.size.toLong(), 2)
            seg.copyFrom(MemorySegment.ofArray(bytes))
            return seg
        }

        private fun decodeWide(seg: MemorySegment, chars: Int): String {
            if (chars <= 0) return ""
            val bytes = seg.asSlice(0, chars.toLong() * 2).toArray(ValueLayout.JAVA_BYTE)
            val text = String(bytes, Charsets.UTF_16LE)
            val end = text.indexOf('\u0000')
            return if (end >= 0) text.substring(0, end) else text
        }

        companion object {
            private const val HKLM = -2147483646L
            private const val KEY_READ = 0x20019
            private const val KEY_WOW64_64 = 0x0100
            private const val KEY_WOW64_32 = 0x0200

            fun tryLoad(): Native? = try {
                val hold = Arena.global()
                Native(SymbolLookup.libraryLookup("Kernel32", hold), SymbolLookup.libraryLookup("Advapi32", hold))
            } catch (_: Throwable) {
                null
            }
        }
    }
}
