package com.example.toread.auth

/**
 * A small, device-local history of auth events, kept in the settings table.
 *
 * Weak-network incidents are impossible to reconstruct after the fact: Android prints the structured
 * client log to logcat (which needs a cable and a reproduction), and the packaged desktop build has no
 * console at all. Fifty compact lines are enough to tell "we kept losing responses and retried" apart
 * from "the server rejected the token", and they carry no secrets — never a token, only its outcome.
 *
 * The read/write lambdas keep this class free of storage details, so the app root owns the settings
 * accessors and the ring stays trivially testable.
 */
internal class AuthDiagnostics(
    private val read: () -> List<String>,
    private val write: (List<String>) -> Unit,
    private val nowIsoString: () -> String,
) {
    fun record(
        event: String,
        reason: String? = null,
        cause: String? = null,
        extra: Array<out Pair<String, Any?>> = emptyArray(),
    ) {
        val entry = buildString {
            append("ts=").append(nowIsoString().toLogToken())
            append(" event=").append(event.toLogToken())
            reason?.let { append(" reason=").append(it.toLogToken()) }
            cause?.let { append(" cause=").append(it.toLogToken()) }
            extra.forEach { (key, value) ->
                append(' ').append(key.toLogToken()).append('=').append(value.toLogToken())
            }
        }
        val entries = (read() + entry).takeLast(MaxEntries)
        runCatching { write(entries) }
    }

    fun entries(): List<String> = read()

    private fun Any?.toLogToken(): String {
        val text = this?.toString().orEmpty()
        return text.filter { it.isLetterOrDigit() || it in "-_.:@+" }.ifBlank { "-" }
    }

    private companion object {
        /** Enough to cover an evening of a flapping network without growing the settings row. */
        const val MaxEntries = 50
    }
}
