package dev.launcherfork.stalzone

/** Small JSON codec for the session file and nothing else. */
internal object MiniJson {
    fun parse(text: String): Any? = Parser(text).parseValue()

    fun write(value: Any?): String = buildString { writeInto(this, value, 0) }

    private fun writeInto(out: StringBuilder, value: Any?, indent: Int) {
        when (value) {
            null -> out.append("null")
            is String -> out.append('"').append(escape(value)).append('"')
            is Boolean -> out.append(if (value) "true" else "false")
            is Number -> out.append(value.toString())
            is Map<*, *> -> {
                out.append("{\n")
                val entries = value.entries.toList()
                entries.forEachIndexed { i, (k, v) ->
                    pad(out, indent + 1)
                    out.append('"').append(escape(k.toString())).append("\": ")
                    writeInto(out, v, indent + 1)
                    if (i != entries.lastIndex) out.append(',')
                    out.append('\n')
                }
                pad(out, indent)
                out.append('}')
            }
            is List<*> -> {
                if (value.isEmpty()) {
                    out.append("[]")
                    return
                }
                out.append("[\n")
                value.forEachIndexed { i, v ->
                    pad(out, indent + 1)
                    writeInto(out, v, indent + 1)
                    if (i != value.lastIndex) out.append(',')
                    out.append('\n')
                }
                pad(out, indent)
                out.append(']')
            }
            else -> error("unsupported ${value.javaClass}")
        }
    }

    private fun pad(out: StringBuilder, n: Int) { repeat(n) { out.append("  ") } }

    private fun escape(s: String): String = buildString {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun parseValue(): Any? {
            skip()
            val v = when (val c = peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) parseNumber() else error("bad json at $i")
            }
            skip()
            return v
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val map = linkedMapOf<String, Any?>()
            skip()
            if (peek() == '}') { i++; return map }
            while (true) {
                skip()
                val key = parseString()
                skip()
                expect(':')
                map[key] = parseValue()
                skip()
                when (peek()) {
                    ',' -> i++
                    '}' -> { i++; return map }
                    else -> error("bad object at $i")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val list = mutableListOf<Any?>()
            skip()
            if (peek() == ']') { i++; return list }
            while (true) {
                list += parseValue()
                skip()
                when (peek()) {
                    ',' -> i++
                    ']' -> { i++; return list }
                    else -> error("bad array at $i")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val out = StringBuilder()
            while (i < s.length) {
                val c = s[i++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> out.append(when (val e = s[i++]) {
                        '"', '\\', '/' -> e
                        'b' -> '\b'
                        'f' -> '\u000C'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        'u' -> s.substring(i, i + 4).also { i += 4 }.toInt(16).toChar()
                        else -> error("bad escape")
                    })
                    else -> out.append(c)
                }
            }
            error("unterminated string")
        }

        private fun parseNumber(): Number {
            val start = i
            if (peek() == '-') i++
            while (i < s.length && s[i].isDigit()) i++
            var real = false
            if (i < s.length && s[i] == '.') {
                real = true
                i++
                while (i < s.length && s[i].isDigit()) i++
            }
            val raw = s.substring(start, i)
            return if (real) raw.toDouble() else raw.toLong()
        }

        private fun literal(word: String, value: Any?): Any? {
            require(s.startsWith(word, i))
            i += word.length
            return value
        }

        private fun expect(c: Char) { skip(); require(s[i] == c) { "expected $c at $i" }; i++ }
        private fun peek(): Char { skip(); return s[i] }
        private fun skip() { while (i < s.length && s[i].isWhitespace()) i++ }
    }
}
