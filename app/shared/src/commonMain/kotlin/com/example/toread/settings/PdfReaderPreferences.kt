package com.example.toread.settings

data class AndroidPdfReaderPreferences(
    val landscapeDoublePageEnabled: Boolean = true,
    val volumeKeysEnabled: Boolean = false,
    val orientationMode: PdfOrientationMode = PdfOrientationMode.System,
    val tapToTurnPagesEnabled: Boolean = true,
    val reverseTapToTurnPages: Boolean = false,
    val pageAnimationEnabled: Boolean = true,
    val doubleTapZoomEnabled: Boolean = true,
    val longPressZoomEnabled: Boolean = true,
    val showPageNumber: Boolean = true,
    val showStatusInfo: Boolean = true,
    val autoPageTurningIntervalSeconds: Int = 5,
)

data class PdfBookPreferences(
    val rightToLeft: Boolean = false,
    val coverAlone: Boolean = true,
    val readerMode: PdfReaderMode = PdfReaderMode.PagedHorizontal,
)

enum class PdfReaderMode(val storedValue: String) {
    PagedHorizontal("paged_horizontal"),
    PagedVertical("paged_vertical"),
    ContinuousVertical("continuous_vertical"),
    ;

    companion object {
        fun fromStoredValue(value: String?): PdfReaderMode? =
            entries.firstOrNull { it.storedValue == value }
    }
}

enum class PdfOrientationMode(val storedValue: String) {
    System("system"),
    Portrait("portrait"),
    Landscape("landscape"),
    ;

    companion object {
        fun fromStoredValue(value: String?): PdfOrientationMode? =
            entries.firstOrNull { it.storedValue == value }
    }
}
