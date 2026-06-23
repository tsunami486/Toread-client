package com.example.toread.progress

fun isIsoTimestampAfter(candidate: String, baseline: String): Boolean =
    candidate.normalizedIsoInstantKey() > baseline.normalizedIsoInstantKey()

private fun String.normalizedIsoInstantKey(): String {
    val trimmed = trim()
    val instant = when {
        trimmed.endsWith("Z") -> trimmed.removeSuffix("Z")
        trimmed.endsWith("+00:00") -> trimmed.removeSuffix("+00:00")
        else -> return trimmed
    }
    val dotIndex = instant.indexOf('.')
    if (dotIndex < 0) {
        return "$instant.000000000Z"
    }

    val head = instant.substring(0, dotIndex)
    val fraction = instant
        .substring(dotIndex + 1)
        .takeWhile { it.isDigit() }
        .take(9)
        .padEnd(9, '0')
    return "$head.${fraction}Z"
}
