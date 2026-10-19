package com.nikunj.gemmabuilder

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay

// Markdown for assistant replies: headings, lists, quotes, rules, code blocks, and inline styles.

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class ListItem(val marker: String, val text: String, val indent: Int) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class Code(val language: String, val code: String) : MdBlock()
    data class Table(val rows: List<List<String>>) : MdBlock()
    /** Text under a label like "Option 1:" (a drafted note, message, caption...), shown as a copyable card. */
    data class Note(val label: MdBlock?, val body: List<MdBlock>) : MdBlock()
    data object Rule : MdBlock()
}

/** Labels that introduce something the user will want to copy: "Option 2", "**Draft 1:**", "Version A". */
private val noteLabelRegex = Regex("""(?i)^(option|draft|version|variation|alternative|choice|template|example|message|note|email|caption|reply)\b.{0,40}$""")

private fun labelText(block: MdBlock): String? = when (block) {
    is MdBlock.Heading -> block.text
    // A short paragraph that is all bold, e.g. "**Option 1: Formal**" or "**Option 1:** (formal)".
    is MdBlock.Paragraph -> block.text.takeIf { it.startsWith("**") && it.length <= 60 }
    else -> null
}?.replace("*", "")?.trim()?.trimEnd(':')?.trim()

private fun isNoteLabel(block: MdBlock): Boolean = labelText(block)?.let { noteLabelRegex.matches(it) } == true

/** Groups the paragraphs, list items and quotes after each note label into one [MdBlock.Note]. */
private fun groupNotes(blocks: List<MdBlock>): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    var i = 0
    while (i < blocks.size) {
        val block = blocks[i]
        // Text framed by two --- rules is a drafted note too ("Here's a note:" --- Hi Nick, … --- ).
        if (block == MdBlock.Rule) {
            val close = (i + 1 until blocks.size).firstOrNull { blocks[it] == MdBlock.Rule }
            val inside = close?.let { blocks.subList(i + 1, it) }
            if (inside != null && inside.isNotEmpty() &&
                inside.all { it is MdBlock.Paragraph || it is MdBlock.ListItem || it is MdBlock.Quote }
            ) {
                out += MdBlock.Note(null, inside.toList())
                i = close + 1
                continue
            }
        }
        if (isNoteLabel(block)) {
            var j = i + 1
            // Stop at the next label, or at a bold lead-in like "**Tips:**" that starts another part.
            while (j < blocks.size && labelText(blocks[j]) == null &&
                (blocks[j] is MdBlock.Paragraph || blocks[j] is MdBlock.ListItem || blocks[j] is MdBlock.Quote)
            ) j++
            if (j > i + 1) {
                out += MdBlock.Note(block, blocks.subList(i + 1, j).toList())
                i = j
                continue
            }
        }
        out += block
        i++
    }
    return out
}

/** Text for the clipboard: Markdown markers removed, link text kept. */
private fun plainText(markdown: String): String = markdown
    .replace(Regex("""\[([^\]]+)]\((https?://[^)]+)\)"""), "$1")
    .replace("**", "")
    .replace(Regex("""(?<![\w*])[*_](\S(?:.*?\S)?)[*_](?![\w*])"""), "$1")
    .replace("`", "")

private fun blockPlainText(block: MdBlock): String = when (block) {
    is MdBlock.Paragraph -> plainText(block.text)
    is MdBlock.Quote -> plainText(block.text)
    is MdBlock.ListItem -> "  ".repeat(block.indent) + (if (block.marker == "•") "-" else block.marker) + " " + plainText(block.text)
    is MdBlock.Heading -> plainText(block.text)
    is MdBlock.Code -> block.code
    else -> ""
}

private fun notePlainText(blocks: List<MdBlock>): String = buildString {
    blocks.forEachIndexed { index, block ->
        if (index > 0) append(if (block is MdBlock.ListItem && blocks[index - 1] is MdBlock.ListItem) "\n" else "\n\n")
        append(blockPlainText(block))
    }
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
            // Consecutive "> " lines form one quote, keeping paragraph breaks.
            trimmed.startsWith(">") -> {
                flush()
                val quote = StringBuilder()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    val content = lines[i].trim().removePrefix(">").trim()
                    when {
                        content.isEmpty() -> if (quote.isNotEmpty() && !quote.endsWith("\n\n")) quote.append("\n\n")
                        quote.isEmpty() || quote.endsWith("\n\n") -> quote.append(content)
                        else -> quote.append(' ').append(content)
                    }
                    i++
                }
                i--
                if (quote.isNotBlank()) blocks += MdBlock.Quote(quote.toString().trim())
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(trimmed)
            }
        }
        i++
    }
    flush()
    return groupNotes(blocks)
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
            blocks.forEach { block -> MarkdownBlock(block, body, fontScale, color, codeBackground, linkColor) }
        }
    }
}

@Composable
private fun MarkdownBlock(
    block: MdBlock,
    body: TextStyle,
    fontScale: Float,
    color: Color,
    codeBackground: Color,
    linkColor: Color,
    inCard: Boolean = false
) {
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
                    // Inside a note card a quote is just the note's text.
                    is MdBlock.Quote -> if (inCard) {
                        Text(inlineMarkdown(block.text, codeBackground, linkColor), style = body, color = color)
                    } else if (block.text.length >= 80 || block.text.contains("\n")) {
                        // A longer quote is usually drafted text (a note, message, caption): make it copyable.
                        CopyCard(block.text) {
                            Text(inlineMarkdown(block.text, codeBackground, linkColor), style = body, color = color)
                        }
                    } else {
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
                    is MdBlock.Note -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        block.label?.let { MarkdownBlock(it, body, fontScale, color, codeBackground, linkColor) }
                        CopyCard(notePlainText(block.body)) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                block.body.forEach { MarkdownBlock(it, body, fontScale, color, codeBackground, linkColor, inCard = true) }
                            }
                        }
                    }
                    is MdBlock.Code -> CodeBlock(block.language, block.code, body)
                    is MdBlock.Table -> MarkdownTable(block.rows, body, codeBackground, linkColor)
                    MdBlock.Rule -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
}

/** A card with a copy button in its top-right corner; [copyText] may contain Markdown. */
@Composable
private fun CopyCard(copyText: String, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box {
            Box(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 48.dp)) { content() }
            CopyButton(plainText(copyText), contentDescription = "Copy", modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

/** Copy icon that briefly turns into a check mark after copying. */
@Composable
private fun CopyButton(text: String, contentDescription: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_500)
            copied = false
        }
    }
    IconButton(onClick = { clipboard.setText(AnnotatedString(text)); copied = true }, modifier = modifier) {
        Icon(
            if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
            contentDescription = if (copied) "Copied" else contentDescription,
            tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun CodeBlock(language: String, code: String, body: TextStyle) {
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
                CopyButton(code, contentDescription = "Copy code")
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
