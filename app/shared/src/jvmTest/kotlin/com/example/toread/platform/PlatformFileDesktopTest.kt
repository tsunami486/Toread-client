package com.example.toread.platform

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformFileDesktopTest {
    @Test
    fun aDroppedPathBecomesTheSameFileDescriptionThePickerBuilds() {
        val temp = Files.createTempFile("toread-drop", ".epub")
        try {
            val file = platformFileFromPath(temp.toString())

            assertEquals(temp.fileName.toString(), file.displayName)
            assertEquals(temp.toAbsolutePath().toString(), file.path)
            assertEquals(Files.size(temp), file.sizeBytes)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    @Test
    fun aPathThatNoLongerExistsStillCarriesItsName() {
        val file = platformFileFromPath("C:\\not\\there\\gone.epub")

        assertEquals("gone.epub", file.displayName)
        assertEquals(null, file.sizeBytes)
    }
}
