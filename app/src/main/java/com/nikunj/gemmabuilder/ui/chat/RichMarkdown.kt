package com.nikunj.gemmabuilder

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

// Markdown for assistant replies: headings, lists, quotes, rules, code blocks, and inline styles.

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class ListItem(val marker: String, val text: String, val indent: Int) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class Code(val language: String, val code: String) : MdBlock()
    data class Table(val rows: List<List<String>>) : MdBlock()
    data object Rule : MdBlock()
}

private val headingRegex = Regex("""^(#{1,4})\s+(.*)$""")
private val bulletRegex = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val orderedRegex = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")

private fun parseBlocks(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()
    fun flush() {
        if (paragraph.isNotBlank()) blocks += MdBlock.Paragraph(paragraph.toString().trim())
        paragraph.clear()
    }
    val lines = text.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") -> {
                flush()
                val language = trimmed.removePrefix("```").trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    code.appendLine(lines[i]); i++
                }
                blocks += MdBlock.Code(language, code.toString().trimEnd())
            }
            trimmed.startsWith("|") -> {
                flush()
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    val row = lines[i].trim().trim('|').split("|").map { it.trim() }
                    // Skip the |---|:---:| separator row.
                    if (!row.all { cell -> cell.isNotEmpty() && cell.all { it == '-' || it == ':' } }) rows += row
                    i++
                }
                i--
                if (rows.isNotEmpty()) blocks += MdBlock.Table(rows)
            }
            trimmed.isEmpty() -> flush()
            trimmed == "---" || trimmed == "***" -> { flush(); blocks += MdBlock.Rule }
            headingRegex.matches(trimmed) -> {
                flush()
                val m = headingRegex.find(trimmed)!!
                blocks += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2])
            }
            bulletRegex.matches(line) -> {
                flush()
                val m = bulletRegex.find(line)!!
                blocks += MdBlock.ListItem("•", m.groupValues[2], m.groupValues[1].length / 2)
            }
            orderedRegex.matches(line) -> {
                flush()
                val m = orderedRegex.find(line)!!
                blocks += MdBlock.ListItem("${m.groupValues[2]}.", m.groupValues[3], m.groupValues[1].length / 2)
            }
            trimmed.startsWith(">") -> { flush(); blocks += MdBlock.Quote(trimmed.removePrefix(">").trim()) }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(trimmed)
            }
        }
        i++
    }
    flush()
    return blocks
}

/** Inline **bold**, *italic*, `code`, and [links](https://…). */
private fun inlineMarkdown(text: String, codeBackground: Color, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val rest = text.substring(i)
        when {
            rest.startsWith("**") && text.indexOf("**", i + 2) > i + 2 -> {
                val end = text.indexOf("**", i + 2)
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text.substring(i + 2, end)) }
                i = end + 2
            }
            (rest.startsWith("*") || rest.startsWith("_")) && rest.length > 2 && !rest[1].isWhitespace() &&
                text.indexOf(rest[0], i + 1) > i + 1 -> {
                val end = text.indexOf(rest[0], i + 1)
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                i = end + 1
            }
            rest.startsWith("`") && text.indexOf('`', i + 1) > i + 1 -> {
                val end = text.indexOf('`', i + 1)
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                    append(" ${text.substring(i + 1, end)} ")
                }
                i = end + 1
            }
            rest.startsWith("[") -> {
                val close = text.indexOf("](", i)
                val endUrl = if (close > 0) text.indexOf(')', close) else -1
                val url = if (endUrl > 0) text.substring(close + 2, endUrl) else ""
                if (close > i && endUrl > close && (url.startsWith("https://") || url.startsWith("http://"))) {
                    withLink(
                        LinkAnnotation.Url(
                            url,
                            TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                        )
                    ) { append(text.substring(i + 1, close)) }
                    i = endUrl + 1
                } else {
                    append(text[i]); i++
                }
            }
            else -> { append(text[i]); i++ }
        }
    }
}

@Composable
fun RichMarkdown(text: String, fontScale: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    val blocks = remember(text) { parseBlocks(text) }
    val body = MaterialTheme.typography.bodyLarge.let { it.copy(fontSize = it.fontSize * fontScale, lineHeight = it.lineHeight * fontScale) }
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val linkColor = MaterialTheme.colorScheme.primary
    SelectionContainer(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            blocks.forEach { block ->
                when (block) {
                    is MdBlock.Heading -> Text(
                        inlineMarkdown(block.text, codeBackground, linkColor),
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineSmall
                            2 -> MaterialTheme.typography.titleLarge
                            else -> MaterialTheme.typography.titleMedium
                        }.let { it.copy(fontSize = it.fontSize * fontScale) },
                        color = color
                    )
                    is MdBlock.Paragraph -> Text(inlineMarkdown(block.text, codeBackground, linkColor), style = body, color = color)
                    is MdBlock.ListItem -> Row(Modifier.padding(start = (block.indent * 16).dp)) {
                        Text(block.marker, style = body, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp))
                        Text(inlineMarkdown(block.text, codeBackground, linkColor), style = body, color = color)
                    }
                    is MdBlock.Quote -> {
                        val bar = MaterialTheme.colorScheme.outline
                        Text(
                            inlineMarkdown(block.text, codeBackground, linkColor),
                            style = body.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .drawBehind { drawRect(bar, size = Size(3.dp.toPx(), size.height)) }
                                .padding(start = 14.dp)
                        )
                    }
                    is MdBlock.Code -> CodeBlock(block.language, block.code, body)
                    is MdBlock.Table -> MarkdownTable(block.rows, body, codeBackground, linkColor)
                    MdBlock.Rule -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(language: String, code: String, body: TextStyle) {
    val clipboard = LocalClipboardManager.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    language.ifBlank { "code" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy code", modifier = Modifier.size(18.dp))
                }
            }
            Text(
                code,
                style = body.copy(fontFamily = FontFamily.Monospace, fontSize = body.fontSize * 0.86f),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
            )
        }
    }
}

@Composable
private fun MarkdownTable(rows: List<List<String>>, body: TextStyle, codeBackground: Color, linkColor: Color) {
    val columns = rows.maxOf { it.size }
    // Width per column from its longest cell, within sensible bounds.
    val widths = (0 until columns).map { c -> (rows.maxOf { it.getOrNull(c)?.length ?: 0 }.coerceIn(4, 28) * 8 + 24).dp }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            rows.forEachIndexed { r, row ->
                Row {
                    (0 until columns).forEach { c ->
                        Text(
                            inlineMarkdown(row.getOrNull(c).orEmpty(), codeBackground, linkColor),
                            style = body.copy(fontSize = body.fontSize * 0.88f, fontWeight = if (r == 0) FontWeight.SemiBold else null),
                            modifier = Modifier
                                .width(widths[c])
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
                if (r < rows.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.width(widths.fold(0.dp) { total, w -> total + w })
                    )
                }
            }
        }
    }
}
