package com.kapi.notes.pdf

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** How many characters fit on one imported page at [IMPORTED_TEXT_FONT_SIZE_PX] inside the
 * default page's text margins - a rough estimate (not a real text-layout pass), generous enough
 * that imported pages read comfortably rather than packed edge-to-edge. Tuned for the project's
 * default page size (1588x2246px, see NotesRepositoryImpl.DEFAULT_PAGE_*). */
private const val CHARS_PER_PAGE = 1400
const val IMPORTED_TEXT_FONT_SIZE_PX = 36f
const val IMPORTED_TEXT_MARGIN_PX = 80f

/** Extracts plain text from a `.txt` or `.docx` file picked via the system document picker (which
 * already reaches Google Drive, Downloads, and any other Storage Access Framework provider - no
 * Drive API/OAuth scope needed for this, see DashboardScreen's OpenDocument() launcher) so it can
 * be imported as a note's content instead of only PDFs/images. */
@Singleton
class DocumentTextImporter @Inject constructor() {

    fun extractPlainText(context: Context, uri: Uri, isDocx: Boolean): Result<String> =
        runCatching {
            if (isDocx) extractDocxText(context, uri) else extractPlainTextFile(context, uri)
        }

    private fun extractPlainTextFile(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Could not read file")

    /** A .docx is a zip archive; its text lives in word/document.xml as a sequence of <w:p>
     * (paragraph) elements, each containing one or more <w:r><w:t>text</w:t></w:r> runs. This
     * walks that XML with a plain XmlPullParser (no extra dependency needed) rather than parsing
     * full OOXML semantics - formatting/styles/images/tables are dropped, only the readable text
     * and paragraph breaks are kept, which is what actually fits in a handwriting-notes page
     * anyway. */
    private fun extractDocxText(context: Context, uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri) ?: error("Could not read file")
        val documentXmlBytes = stream.use { input ->
            ZipInputStream(input).use { zip ->
                generateSequence { zip.nextEntry }
                    .firstOrNull { it.name == "word/document.xml" }
                    ?.let { zip.readBytes() }
            }
        } ?: error(".docx missing word/document.xml - not a valid Word document")

        val parser: XmlPullParser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(documentXmlBytes.inputStream(), "UTF-8")
        }

        val sb = StringBuilder()
        var inTextRun = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "t" -> inTextRun = true
                        // <w:tab/> and <w:br/> are self-closing - no matching text content, so
                        // handle them on START_TAG directly instead of waiting for an END_TAG.
                        "tab" -> sb.append('\t')
                        "br" -> sb.append('\n')
                    }
                }
                XmlPullParser.TEXT -> if (inTextRun) sb.append(parser.text)
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "t" -> inTextRun = false
                        "p" -> sb.append('\n') // paragraph boundary
                    }
                }
            }
            event = parser.next()
        }
        return sb.toString().trim()
    }
}

/** Splits [text] into page-sized chunks, breaking on a blank line or whitespace rather than
 * mid-word wherever possible, so each returned string is what one page's single text box holds. */
fun paginateImportedText(text: String, charsPerPage: Int = CHARS_PER_PAGE): List<String> {
    if (text.isEmpty()) return listOf("")
    val pages = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        val hardEnd = (start + charsPerPage).coerceAtMost(text.length)
        if (hardEnd == text.length) {
            pages += text.substring(start, hardEnd)
            break
        }
        // Prefer breaking at the last paragraph break within the budget, else the last space,
        // else just hard-cut - never leaves an empty page from a zero-length break point.
        val paragraphBreak = text.lastIndexOf("\n\n", hardEnd)
        val spaceBreak = text.lastIndexOf(' ', hardEnd)
        val breakAt = when {
            paragraphBreak > start -> paragraphBreak + 2
            spaceBreak > start -> spaceBreak + 1
            else -> hardEnd
        }
        pages += text.substring(start, breakAt)
        start = breakAt
    }
    return pages
}
