package org.solsticesw.vivlia.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class LocalEpubParserTest {

    @Test
    fun parsesMetadataCoverAndSpineInReadingOrder() {
        val epub = createEpub(
            """
                <package>
                  <metadata>
                    <dc:title>Book Title</dc:title>
                    <dc:creator>Author Name</dc:creator>
                    <dc:description>Book description</dc:description>
                    <meta name="cover" content="cover"/>
                  </metadata>
                  <manifest>
                    <item id="cover" href="images/cover.png" media-type="image/png"/>
                    <item id="chapter-2" href="text/chapter-2.xhtml" media-type="application/xhtml+xml"/>
                    <item id="chapter-1" href="text/chapter-1.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="chapter-2"/>
                    <itemref idref="chapter-1"/>
                  </spine>
                </package>
            """.trimIndent()
        )

        try {
            val book = LocalEpubParser.parse(epub)
            assertEquals("Book Title", book.title)
            assertEquals(listOf("Author Name"), book.authors)
            assertEquals("Book description", book.description)
            assertEquals("OEBPS/images/cover.png", book.coverEntry)
            assertEquals(
                listOf("OEBPS/text/chapter-2.xhtml", "OEBPS/text/chapter-1.xhtml"),
                book.chapters.map { it.entryName }
            )
        } finally {
            epub.delete()
        }
    }

    @Test
    fun rejectsSpineHrefThatEscapesThePackageDirectory() {
        val epub = createEpub(
            """
                <package>
                  <manifest>
                    <item id="chapter" href="../../outside.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="chapter"/></spine>
                </package>
            """.trimIndent()
        )

        try {
            val failure = runCatching { LocalEpubParser.parse(epub) }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
        } finally {
            epub.delete()
        }
    }

    private fun createEpub(packageXml: String): File {
        val file = File.createTempFile("vivlia-test-", ".epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(
                """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>"""
                    .toByteArray()
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8"?><package xmlns:dc="http://purl.org/dc/elements/1.1/">$packageXml</package>"""
                    .toByteArray()
            )
            zip.closeEntry()
        }
        return file
    }
}
