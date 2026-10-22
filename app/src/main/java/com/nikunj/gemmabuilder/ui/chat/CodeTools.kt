package com.nikunj.gemmabuilder

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Code blocks in replies: colors for keywords, strings, comments, numbers and HTML tags, and a
// button that saves the block (or a table, as CSV) to the phone's Downloads folder.

/** The colors a highlighted block uses, taken from the app theme so they suit light and dark. */
data class CodeColors(val keyword: Color, val string: Color, val comment: Color, val number: Color, val tag: Color, val attribute: Color)

private val keywords = setOf(
    // Shared by most languages.
    "if", "else", "for", "while", "do", "return", "break", "continue", "switch", "case", "default", "try", "catch",
    "finally", "throw", "throws", "new", "this", "class", "interface", "enum", "extends", "implements", "import",
    "export", "from", "package", "public", "private", "protected", "static", "final", "const", "let", "var", "void",
    "true", "false", "null", "undefined", "typeof", "instanceof", "async", "await", "yield", "function", "type",
    "struct", "in", "of", "as", "is", "with", "super",
    // Python.
    "def", "elif", "except", "raise", "pass", "lambda", "None", "True", "False", "and", "or", "not", "global",
    "nonlocal", "assert", "del", "self",
    // Kotlin, Swift, Go, Rust.
    "fun", "val", "when", "object", "override", "data", "func", "go", "defer", "chan", "fn", "impl", "mut", "pub",
    "use", "mod", "match", "guard", "where",
    // Common types.
    "int", "float", "double", "long", "char", "bool", "boolean", "string", "String", "Int"
)

private fun commentStyle(language: String) = when (language.lowercase(Locale.US)) {
    "python", "py", "bash", "sh", "shell", "zsh", "yaml", "yml", "ruby", "rb", "toml", "r", "dockerfile", "makefile" -> "hash"
    "html", "xml", "svg", "vue" -> "markup"
    "sql" -> "dash"
    else -> "slash"
}

/** [code] with colors for its tokens, for the given fence [language] ("html", "python", "kotlin"...). */
fun highlightCode(code: String, language: String, colors: CodeColors): AnnotatedString {
    val style = commentStyle(language)
    val markup = style == "markup"
    val comment = when (style) {
        "hash" -> """#[^\n]*"""
        "markup" -> """<!--[\s\S]*?-->|/\*[\s\S]*?\*/"""
        "dash" -> """--[^\n]*|/\*[\s\S]*?\*/"""
        else -> """//[^\n]*|/\*[\s\S]*?\*/"""
    }
    // One pass over the code: the first alternative that matches at a spot wins.
    val token = Regex(
        "(?<comment>$comment)" +
            """|(?<string>"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'|`(?:\\.|[^`\\])*`)""" +
            (if (markup) """|(?<tag></?!?[A-Za-z][\w:-]*|/?>)|(?<attr>\b[\w:-]+(?=\s*=))""" else "") +
            """|(?<number>\b\d+(?:\.\d+)?\b)""" +
            """|(?<word>\b[A-Za-z_]\w*\b)"""
    )
    return buildAnnotatedString {
        var at = 0
        for (m in token.findAll(code)) {
            append(code, at, m.range.first)
            val text = m.value
            val span = when {
                m.groups["comment"] != null -> SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic)
                m.groups["string"] != null -> SpanStyle(color = colors.string)
                markup && m.groups["tag"] != null -> SpanStyle(color = colors.tag)
                markup && m.groups["attr"] != null -> SpanStyle(color = colors.attribute)
                m.groups["number"] != null -> SpanStyle(color = colors.number)
                // In HTML text, words are content; keywords count only in code languages.
                !markup && text in keywords -> SpanStyle(color = colors.keyword)
                else -> null
            }
            if (span == null) append(text) else withStyle(span) { append(text) }
            at = m.range.last + 1
        }
        append(code, at, code.length)
    }
}

/** File extension and MIME type for a fence language. */
fun codeFileType(language: String): Pair<String, String> = when (language.lowercase(Locale.US).trim()) {
    "html", "htm" -> "html" to "text/html"
    "css" -> "css" to "text/css"
    "js", "javascript", "jsx" -> (if (language.equals("jsx", true)) "jsx" else "js") to "text/javascript"
    "ts", "typescript" -> "ts" to "text/plain"
    "tsx" -> "tsx" to "text/plain"
    "py", "python" -> "py" to "text/x-python"
    "kt", "kotlin" -> "kt" to "text/plain"
    "java" -> "java" to "text/x-java"
    "json" -> "json" to "application/json"
    "xml" -> "xml" to "text/xml"
    "svg" -> "svg" to "image/svg+xml"
    "sql" -> "sql" to "text/plain"
    "sh", "bash", "shell", "zsh" -> "sh" to "text/x-sh"
    "c" -> "c" to "text/x-c"
    "cpp", "c++" -> "cpp" to "text/x-c++"
    "cs", "csharp" -> "cs" to "text/plain"
    "go" -> "go" to "text/plain"
    "rs", "rust" -> "rs" to "text/plain"
    "swift" -> "swift" to "text/plain"
    "rb", "ruby" -> "rb" to "text/plain"
    "php" -> "php" to "text/plain"
    "yaml", "yml" -> "yaml" to "text/plain"
    "md", "markdown" -> "md" to "text/markdown"
    "csv" -> "csv" to "text/csv"
    else -> "txt" to "text/plain"
}

/** A file name for a saved block: the page title for HTML, otherwise "mochu-<type>-<time>". */
fun codeFileName(code: String, language: String): String {
    val (ext, _) = codeFileType(language)
    val title = if (ext == "html") Regex("""<title>\s*([^<]{1,60}?)\s*</title>""", RegexOption.IGNORE_CASE).find(code)?.groupValues?.get(1) else null
    val base = title?.replace(Regex("""[^\w\- ]"""), "")?.trim()?.replace(' ', '-')?.takeIf { it.isNotBlank() }
        ?: ("mochu-" + (if (ext == "txt") "text" else ext) + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")))
    return "$base.$ext"
}

/** Rows as CSV, quoting cells that need it. */
fun tableToCsv(rows: List<List<String>>): String = rows.joinToString("\n") { row ->
    row.joinToString(",") { cell ->
        val plain = cell.replace("**", "").replace("`", "")
        if (plain.any { it == ',' || it == '"' || it == '\n' }) "\"" + plain.replace("\"", "\"\"") + "\"" else plain
    }
}

/** Saves [text] to Downloads as [name]; returns false if Android refused. */
fun saveToDownloads(context: Context, name: String, mime: String, text: String): Boolean = runCatching {
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, name)
        put(MediaStore.Downloads.MIME_TYPE, mime)
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
    resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: return false
    values.clear()
    values.put(MediaStore.Downloads.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
    true
}.getOrDefault(false)

/** Download button for a code block or table. */
@Composable
fun DownloadButton(name: String, mime: String, text: () -> String, contentDescription: String) {
    val context = LocalContext.current
    IconButton(onClick = {
        val saved = saveToDownloads(context, name, mime, text())
        Toast.makeText(context, if (saved) "Saved to Downloads as $name" else "Couldn't save the file", Toast.LENGTH_SHORT).show()
    }) {
        Icon(Icons.Rounded.Download, contentDescription = contentDescription)
    }
}
