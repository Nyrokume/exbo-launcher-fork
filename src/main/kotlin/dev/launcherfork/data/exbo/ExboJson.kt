package dev.launcherfork.data.exbo

/** The bit of JSON the EXBO launcher actually returns: objects, arrays, strings, numbers, bools, null. */
internal sealed interface Json {
    data class Obj(val fields: Map<String, Json>) : Json
    data class Arr(val items: List<Json>) : Json
    data class Str(val text: String) : Json
    data class Num(val text: String) : Json
    data class Bool(val value: Boolean) : Json
    data object Null : Json
}

internal fun parseJson(raw: String): Json {
    val parser = JsonParser(raw)
    val value = parser.value()
    parser.skip()
    require(parser.done) { "trailing json" }
    return value
}

internal fun Json.field(name: String): Json? = (this as? Json.Obj)?.fields?.get(name)

internal fun Json.text(name: String): String? = when (val value = field(name)) {
    is Json.Str -> value.text
    is Json.Num -> value.text
    else -> null
}

internal fun Json.int(name: String): Int? = text(name)?.toIntOrNull()

internal fun Json.double(name: String): Double? = text(name)?.toDoubleOrNull()

internal fun Json.bool(name: String): Boolean? = (field(name) as? Json.Bool)?.value

internal fun Json.long(name: String): Long? = text(name)?.toLongOrNull()

internal fun Json.strings(name: String): List<String> {
    val arr = field(name) as? Json.Arr ?: return emptyList()
    return arr.items.mapNotNull { (it as? Json.Str)?.text }
}

private class JsonParser(private val source: String) {
    private var index = 0

    val done: Boolean get() = index >= source.length

    fun skip() {
        while (index < source.length && source[index].isWhitespace()) index++
    }

    fun value(): Json {
        skip()
        require(index < source.length) { "empty json" }
        return when (source[index]) {
            '{' -> obj()
            '[' -> arr()
            '"' -> Json.Str(str())
            't' -> literal("true", Json.Bool(true))
            'f' -> literal("false", Json.Bool(false))
            'n' -> literal("null", Json.Null)
            else -> num()
        }
    }

    private fun obj(): Json.Obj {
        expect('{')
        val fields = linkedMapOf<String, Json>()
        skip()
        if (peek('}')) return Json.Obj(fields)
        while (true) {
            skip()
            val name = str()
            skip()
            expect(':')
            fields[name] = value()
            skip()
            when {
                peek(',') -> continue
                peek('}') -> return Json.Obj(fields)
                else -> error("expected , or }")
            }
        }
    }

    private fun arr(): Json.Arr {
        expect('[')
        val items = mutableListOf<Json>()
        skip()
        if (peek(']')) return Json.Arr(items)
        while (true) {
            items += value()
            skip()
            when {
                peek(',') -> continue
                peek(']') -> return Json.Arr(items)
                else -> error("expected , or ]")
            }
        }
    }

    private fun str(): String {
        expect('"')
        val out = StringBuilder()
        while (index < source.length) {
            val c = source[index++]
            when (c) {
                '"' -> return out.toString()
                '\\' -> out.append(escape())
                else -> out.append(c)
            }
        }
        error("unterminated string")
    }

    private fun escape(): Char {
        require(index < source.length) { "bad escape" }
        return when (val c = source[index++]) {
            '"', '\\', '/' -> c
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                require(index + 4 <= source.length) { "bad unicode escape" }
                val hex = source.substring(index, index + 4)
                index += 4
                hex.toInt(16).toChar()
            }
            else -> error("bad escape")
        }
    }

    private fun num(): Json.Num {
        val start = index
        if (source[index] == '-') index++
        require(index < source.length && source[index].isDigit()) { "bad number" }
        while (index < source.length && source[index].isDigit()) index++
        if (index < source.length && source[index] == '.') {
            index++
            while (index < source.length && source[index].isDigit()) index++
        }
        if (index < source.length && (source[index] == 'e' || source[index] == 'E')) {
            index++
            if (index < source.length && (source[index] == '+' || source[index] == '-')) index++
            while (index < source.length && source[index].isDigit()) index++
        }
        return Json.Num(source.substring(start, index))
    }

    private fun literal(word: String, value: Json): Json {
        require(source.startsWith(word, index)) { "expected $word" }
        index += word.length
        return value
    }

    private fun expect(c: Char) {
        skip()
        require(index < source.length && source[index] == c) { "expected $c" }
        index++
    }

    private fun peek(c: Char): Boolean {
        if (index < source.length && source[index] == c) {
            index++
            return true
        }
        return false
    }
}
