package com.example.toread.platform

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun aDroppedFileUriResolvesIntoTheRealFilePath() {
        // The desktop drop hands over `file:` URIs, spaces and non-ASCII are percent-encoded in them.
        val directory = Files.createTempDirectory("toread drop ")
        val book = directory.resolve("法医秦明系列 (全8册).txt")
        Files.writeString(book, "拖拽导入验证")
        try {
            val file = platformFileFromPath(book.toUri().toString())

            assertEquals("法医秦明系列 (全8册).txt", file.displayName)
            assertEquals(book.toAbsolutePath().toString(), file.path)
            assertEquals(Files.size(book), file.sizeBytes)
            assertTrue(file.mimeType?.startsWith("text/") == true, "mime=${file.mimeType}")
        } finally {
            Files.deleteIfExists(book)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun bothFileUriSpellingsResolveToTheSamePath() {
        val book = Files.createTempFile("toread-uri", ".epub")
        try {
            // Java writes `file:///C:/...`; the drop in the bug report delivered `file:/E:/...`.
            val singleSlash = "file:/" + book.toAbsolutePath().toString().replace('\\', '/')
            val tripleSlash = book.toUri().toString()

            assertEquals(
                platformFileFromPath(singleSlash).path,
                platformFileFromPath(tripleSlash).path,
            )
            assertEquals(book.fileName.toString(), platformFileFromPath(singleSlash).displayName)
        } finally {
            Files.deleteIfExists(book)
        }
    }

    @Test
    fun aFileUriWithRawSpacesOrBackslashesStillResolves() {
        val directory = Files.createTempDirectory("toread raw ")
        val book = directory.resolve("raw name.epub")
        Files.writeString(book, "x")
        try {
            val driveStyle = "file:${book.toAbsolutePath()}"
            val unencoded = "file:/${book.toAbsolutePath().toString().replace('\\', '/')}"

            assertEquals(book.fileName.toString(), platformFileFromPath(driveStyle).displayName)
            assertEquals(book.fileName.toString(), platformFileFromPath(unencoded).displayName)
        } finally {
            Files.deleteIfExists(book)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun theMixedEncodingADesktopDropSendsResolves() {
        // Verbatim shape of the drop that failed: `file:/`, raw Chinese and full-width parens, %20 for
        // spaces, %5B/%5D for brackets.
        val directory = Files.createTempDirectory("toread-mixed")
        val name = "法医秦明系列（全8册） (法医秦明 [法医秦明]).txt"
        val book = directory.resolve(name)
        Files.writeString(book, "x")
        try {
            val encodedName = name
                .replace(" ", "%20")
                .replace("[", "%5B")
                .replace("]", "%5D")
            val raw = "file:/" + directory.toAbsolutePath().toString().replace('\\', '/') +
                "/" + encodedName

            val file = platformFileFromPath(raw)

            assertEquals(name, file.displayName)
            assertEquals(book.toAbsolutePath().toString(), file.path)
            assertEquals(Files.size(book), file.sizeBytes)
        } finally {
            Files.deleteIfExists(book)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun anUnresolvablePathIsLeftAloneInsteadOfThrowing() {
        assertNull(resolveLocalFilePath("   "))
        assertNull(resolveLocalFilePath("file://server/share/%zz.epub"))
        assertEquals(
            "not a path.epub",
            platformFileFromPath("not a path.epub").displayName,
        )
    }
}
