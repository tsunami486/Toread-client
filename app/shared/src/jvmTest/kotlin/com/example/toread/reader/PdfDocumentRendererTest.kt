package com.example.toread.reader

import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfDocumentRendererTest {
    @Test
    fun opensRendersAndClosesLocalTwoPagePdf() = runBlocking {
        val pdfPath = Files.createTempFile("toread-pdf-renderer-", ".pdf")
        val renderer = PdfDocumentRenderer()
        try {
            PDDocument().use { document ->
                document.addPage(PDPage(PDRectangle(400f, 600f)))
                document.addPage(PDPage(PDRectangle(600f, 400f)))
                document.save(pdfPath.toFile())
            }

            val info = renderer.open(pdfPath.toString())

            assertEquals(2, info.pageCount)
            val portrait = renderer.renderPage(
                pageIndex = 0,
                targetWidthPx = 320,
                targetHeightPx = 320,
            )
            val landscape = renderer.renderPage(
                pageIndex = 1,
                targetWidthPx = 320,
                targetHeightPx = 320,
            )
            assertTrue(portrait.width in 1..320)
            assertTrue(portrait.height in 1..320)
            assertTrue(portrait.height > portrait.width)
            assertTrue(landscape.width in 1..320)
            assertTrue(landscape.height in 1..320)
            assertTrue(landscape.width > landscape.height)
        } finally {
            renderer.close()
            renderer.close()
            Files.deleteIfExists(pdfPath)
        }
    }
}
