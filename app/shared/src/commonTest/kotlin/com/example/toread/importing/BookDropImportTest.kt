package com.example.toread.importing

import com.example.toread.platform.PlatformFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookDropImportTest {
    @Test
    fun droppedFilesKeepOnlySupportedBooksInDropOrder() {
        val dropped = listOf(
            file("C:\\books\\三体.epub"),
            file("/home/reader/manual.PDF"),
            file("D:\\notes\\readme.TXT"),
            file("C:\\books\\cover.png"),
            file("C:\\books\\archive.zip"),
            file("C:\\books\\no-extension"),
        )

        assertEquals(
            listOf("三体.epub", "manual.PDF", "readme.TXT"),
            dropped.droppedBookFiles().map { it.displayName },
        )
    }

    @Test
    fun aDroppedFileWithoutAPathStillUsesItsName() {
        val dropped = listOf(
            PlatformFile(displayName = "没有路径.epub"),
            PlatformFile(displayName = "没有路径.png"),
        )

        assertEquals(listOf("没有路径.epub"), dropped.droppedBookFiles().map { it.displayName })
    }

    @Test
    fun theDropMessageReportsEveryOutcome() {
        val message = droppedImportMessage(
            results = listOf(
                BookImportResult.Imported("hash", "三体", "C:\\stored\\original.epub"),
                BookImportResult.Duplicate("hash2", "活着"),
                BookImportResult.Failed("broken.epub", "文件可能已损坏"),
            ),
            skippedCount = 2,
        )

        assertTrue(message.contains("导入成功：1"), message)
        assertTrue(message.contains("已在书架中：1"), message)
        assertTrue(message.contains("失败：1"), message)
        assertTrue(message.contains("已忽略 2"), message)
    }

    @Test
    fun aDropThatImportedNothingReportsTheReasonInsteadOfOnlyACount() {
        val message = droppedImportMessage(
            results = listOf(
                BookImportResult.Failed("a.epub", "文件 MIME 类型不匹配：image/png"),
                BookImportResult.Failed("b.epub", "文件 MIME 类型不匹配：image/png"),
            ),
        )

        assertTrue(message.contains("拖入的 2 个文件导入失败"), message)
        assertTrue(message.contains("MIME"), message)
    }

    @Test
    fun anEmptyDropFallsBackToTheGenericImportFailure() {
        assertEquals(
            com.example.toread.ui.text.ToreadStrings.ImportFailedGeneric,
            droppedImportMessage(results = emptyList()),
        )
    }

    @Test
    fun theImporterAndTheDropTargetAgreeOnTheSupportedExtensions() {
        assertTrue("book.epub".isSupportedBookFileName())
        assertTrue("book.PDF".isSupportedBookFileName())
        assertTrue("book.Txt".isSupportedBookFileName())
        assertTrue(!"book.mobi".isSupportedBookFileName())
        assertTrue(!"book".isSupportedBookFileName())
        assertTrue(!"".isSupportedBookFileName())
        assertEquals(setOf("epub", "pdf", "txt"), supportedBookExtensions)
    }

    private fun file(path: String): PlatformFile = PlatformFile(
        displayName = path.substringAfterLast('\\').substringAfterLast('/'),
        path = path,
    )
}
