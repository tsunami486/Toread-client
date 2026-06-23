package com.example.toread.logging

object ToreadClientLog {
    fun info(area: String, event: String, vararg fields: Pair<String, Any?>) {
        write("INFO", area, event, fields)
    }

    fun warn(area: String, event: String, vararg fields: Pair<String, Any?>) {
        write("WARN", area, event, fields)
    }

    private fun write(
        level: String,
        area: String,
        event: String,
        fields: Array<out Pair<String, Any?>>,
    ) {
        val parts = mutableListOf(
            "level=${level.toLogValue()}",
            "area=${area.toLogValue()}",
            "event=${event.toLogValue()}",
        )
        fields.forEach { (key, value) ->
            parts += "${key.toLogKey()}=${value.toLogValue()}"
        }
        println("[Toread] ${parts.joinToString(" ")}")
    }

    private fun String.toLogKey(): String =
        filter { it.isLetterOrDigit() || it == '_' || it == '-' }.ifBlank { "field" }

    private fun Any?.toLogValue(): String {
        if (this == null) return "null"
        val text = toString()
        if (text.isEmpty()) return "\"\""
        val needsQuotes = text.any { it.isWhitespace() || it == '"' || it == '=' }
        if (!needsQuotes) return text
        return buildString {
            append('"')
            text.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }
    }
}
