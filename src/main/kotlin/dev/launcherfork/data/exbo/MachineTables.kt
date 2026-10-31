package dev.launcherfork.data.exbo

/** SMBIOS Type 17 and logical-processor records. Pure: the Windows calls live in [WindowsHardware]. */
internal object MachineTables {
    const val DMI_MEMORY_DEVICE = 17
    const val DMI_END_OF_TABLE = 127
    const val CACHE_UNIFIED = 0

    fun memDeviceSpeedMts(rec: ByteArray): Int? {
        if (rec.size < 0x0E) return null
        if (u16(rec, 0x0C) == 0) return null
        if (rec.size >= 0x22) {
            val configured = u16(rec, 0x20)
            if (configured != 0 && configured != 0xFFFF) return configured
            if (rec.size >= 0x5C && configured == 0xFFFF) {
                val ext = u32(rec, 0x58)
                if (ext != 0L) return ext.toInt()
            }
        }
        if (rec.size >= 0x17) {
            val speed = u16(rec, 0x15)
            if (speed != 0 && speed != 0xFFFF) return speed
            if (rec.size >= 0x58 && speed == 0xFFFF) {
                val ext = u32(rec, 0x54)
                if (ext != 0L) return ext.toInt()
            }
        }
        return null
    }

    /** Highest configured speed in a `GetSystemFirmwareTable` RSMB buffer. 0 when the table is unreadable. */
    fun smbiosBestMemSpeed(raw: ByteArray): Int {
        if (raw.size < 8) return 0
        val tableLen = u32(raw, 4)
        if (tableLen == 0L || 8 + tableLen > raw.size) return 0
        val end = 8 + tableLen.toInt()
        var best = 0
        var off = 8
        while (off + 4 <= end) {
            val typ = raw[off].toInt() and 0xFF
            val length = raw[off + 1].toInt() and 0xFF
            if (length < 4 || off + length > end) break
            if (typ == DMI_MEMORY_DEVICE) {
                val speed = memDeviceSpeedMts(raw.copyOfRange(off, off + length))
                if (speed != null && speed > best) best = speed
            }
            off += length
            while (off + 1 < end && !(raw[off].toInt() == 0 && raw[off + 1].toInt() == 0)) off++
            off += 2
            if (typ == DMI_END_OF_TABLE) break
        }
        return best
    }

    /** One record per physical core in a `RelationProcessorCore` buffer. */
    fun countProcessorCores(buf: ByteArray): Int {
        var cores = 0
        var off = 0
        while (off + 8 <= buf.size) {
            val size = u32(buf, off + 4)
            if (size == 0L || off + size > buf.size) break
            cores++
            off += size.toInt()
        }
        return cores
    }

    /**
     * Largest unified L3 in a `RelationCache` buffer, in MB.
     * The maximum is per record, not the sum, so a dual-CCD CPU keeps one CCD's cache.
     */
    fun maxUnifiedL3Mb(buf: ByteArray): Int {
        var maxBytes = 0L
        var off = 0
        while (off + 20 <= buf.size) {
            val size = u32(buf, off + 4)
            if (size == 0L || off + size > buf.size) break
            val level = buf[off + 8].toInt() and 0xFF
            val cacheSize = u32(buf, off + 12)
            val cacheType = u32(buf, off + 16)
            if (level == 3 && cacheType == CACHE_UNIFIED.toLong() && cacheSize > maxBytes) maxBytes = cacheSize
            off += size.toInt()
        }
        return (maxBytes shr 20).toInt()
    }

    fun isGenericCpu(name: String): Boolean {
        val text = name.trim()
        return text.startsWith("Intel64 Family") ||
            text.startsWith("AMD64 Family") ||
            text.startsWith("x86 Family") ||
            text.equals("unknown", ignoreCase = true)
    }

    fun cleanHardwareName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return ""
        return trimmed.replace(Regex(" {2,}"), " ")
    }

    fun gpuUsable(name: String): Boolean {
        val text = name.trim().lowercase()
        return text.isNotEmpty() &&
            !text.contains("microsoft basic render") &&
            !text.contains("microsoft basic display")
    }

    private fun u16(buf: ByteArray, at: Int): Int =
        (buf[at].toInt() and 0xFF) or ((buf[at + 1].toInt() and 0xFF) shl 8)

    private fun u32(buf: ByteArray, at: Int): Long =
        (buf[at].toLong() and 0xFF) or
            ((buf[at + 1].toLong() and 0xFF) shl 8) or
            ((buf[at + 2].toLong() and 0xFF) shl 16) or
            ((buf[at + 3].toLong() and 0xFF) shl 24)
}
