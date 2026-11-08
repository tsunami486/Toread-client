package com.example.toread.lan

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The pairing code and the address parser are the two pieces a user can get wrong, so they are pinned
 * here: everything else in the LAN path needs a socket or a database.
 */
class LanProtocolTest {
    @Test
    fun pairingCodeUsesTheUnambiguousAlphabetAndExpectedLength() {
        repeat(64) { seed ->
            val code = LanPairingCode.generate(Random(seed))
            assertEquals(LanPairingCode.Length, code.length)
            assertFalse(code.any { it in "01O2IZ" }, "code '$code' contains an ambiguous character")
            assertTrue(LanPairingCode.isValid(code))
        }
    }

    @Test
    fun pairingCodeIgnoresCaseAndSeparatorsButRejectsShortInput() {
        val expected = "AB3DEF"
        assertTrue(LanPairingCode.matches(expected, "ab3def"))
        assertTrue(LanPairingCode.matches(expected, "AB-3D EF"))
        assertTrue(LanPairingCode.matches(expected, " ab3:def "))
        assertFalse(LanPairingCode.matches(expected, "AB3DE"))
        assertFalse(LanPairingCode.matches(expected, "AB3DEFF"))
        assertFalse(LanPairingCode.matches(expected, ""))
        // A five-character code is not a code, so the field refuses it instead of accepting a cut-off one.
        assertFalse(LanPairingCode.isValid("AB3DE"))
        assertEquals("AB3DEF", LanPairingCode.normalize("ab-3d ef"))
    }

    @Test
    fun peerAddressAcceptsWhatAPersonTypesAndRejectsNonsense() {
        assertEquals("http://192.168.1.7:47901", LanPeerAddress.parse("192.168.1.7:47901", "AB3DEF")?.baseUrl)
        assertEquals("http://192.168.1.7", LanPeerAddress.parse("192.168.1.7", "AB3DEF")?.baseUrl)
        assertEquals("https://peer.local:443", LanPeerAddress.parse("https://peer.local:443/", "AB3DEF")?.baseUrl)
        assertEquals("AB3DEF", LanPeerAddress.parse("192.168.1.7:47901", "ab-3d ef")?.code)

        assertNull(LanPeerAddress.parse("", "AB3DEF"))
        assertNull(LanPeerAddress.parse("192.168.1.7:47901", "SHORT"))
        assertNull(LanPeerAddress.parse("192.168.1.7:47901/sub", "AB3DEF"))
        assertNull(LanPeerAddress.parse("192.168 1.7", "AB3DEF"))
    }

    @Test
    fun protocolPathsStayUnderTheVersionedPrefix() {
        assertEquals("/lan/v1/info", LanPaths.Info)
        assertEquals("/lan/v1/books", LanPaths.Books)
        assertEquals("/lan/v1/books/abc123/file", LanPaths.bookFile("abc123"))
    }

    @Test
    fun receivedFileNameStaysASupportedFileTheImporterWillAccept() {
        assertEquals("三体.epub", importFileName("三体", "EPUB"))
        assertEquals("book.pdf", importFileName("", "pdf"))
        // Path separators and control characters must never survive into a downloaded file name.
        val sanitized = importFileName("a/b\\c:d*e?f\"g<h>i|j", "txt")
        assertTrue(sanitized.endsWith(".txt"))
        assertFalse(sanitized.any { it in "\\/:*?\"<>|" })
        // An unknown format falls back to a supported extension instead of producing an unimportable file.
        assertTrue(importFileName("x", "mobi").endsWith(".epub"))
    }

    @Test
    fun missingBooksIsDecidedByHashOnly() {
        val peer = listOf(
            LanBookSummary(bookHash = "hash-a", title = "A", format = "epub"),
            LanBookSummary(bookHash = "hash-b", title = "B", format = "pdf"),
        )
        val missing = missingBooks(peer, localHashes = setOf("hash-b"))
        assertEquals(listOf("hash-a"), missing.map { it.bookHash })
        assertTrue(missingBooks(peer, setOf("hash-a", "hash-b")).isEmpty())
        assertNotNull(missingBooks(peer, emptySet()).firstOrNull())
    }
}
