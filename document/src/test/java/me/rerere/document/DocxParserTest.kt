package me.rerere.document

import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocxParserTest {
    @Test
    fun `parser finds document xml when docx entries have a folder prefix`() {
        val file = File.createTempFile("docx-parser-prefix", ".docx")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("prefix/word/document.xml"))
                zip.write(
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body>
                        <w:p><w:r><w:t>第一段内容</w:t></w:r></w:p>
                      </w:body>
                    </w:document>
                    """.trimIndent().toByteArray()
                )
                zip.closeEntry()
            }

            val parsed = DocxParser.parse(file)

            assertTrue(parsed, parsed.contains("第一段内容"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `parser throws instead of returning error text for invalid docx`() {
        val file = File.createTempFile("docx-parser-invalid", ".docx")
        try {
            file.writeText("this is not a zip package")

            try {
                DocxParser.parse(file)
                fail("Expected invalid DOCX to throw")
            } catch (error: IllegalArgumentException) {
                assertTrue(error.message.orEmpty().contains("DOCX"))
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `parser throws when document xml is missing`() {
        val file = File.createTempFile("docx-parser-empty", ".docx")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("[Content_Types].xml"))
                zip.write("""<?xml version="1.0" encoding="UTF-8"?><Types/>""".toByteArray())
                zip.closeEntry()
            }

            try {
                DocxParser.parse(file)
                fail("Expected DOCX without document xml to throw")
            } catch (error: IllegalArgumentException) {
                assertTrue(error.message.orEmpty().contains("正文"))
            }
        } finally {
            file.delete()
        }
    }
}
