package com.miniichat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miniichat.R

/**
 * Minimal Markdown renderer (block + inline). No external dependency.
 * Supports headings, paragraphs, fenced code, lists, quotes, hr, bold/italic/code/strike/link.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current
) {
    val blocks = remember(text) { parseBlocks(text) }
    val roundScreen = LocalConfiguration.current.isScreenRound
    val body = MaterialTheme.typography.bodyLarge.let {
        if (roundScreen) it.copy(fontSize = 19.sp, lineHeight = 26.sp) else it
    }
    Column(modifier = modifier) {
        blocks.forEachIndexed { idx, block ->
            if (idx > 0) Spacer(Modifier.height(blockSpacing(blocks[idx - 1], block).dp))
            when (block) {
                is Block.Heading -> {
                    val size = when (block.level) {
                        1 -> if (roundScreen) 25 else 28
                        2 -> if (roundScreen) 22 else 24
                        else -> if (roundScreen) 19 else 21
                    }
                    Text(
                        parseInline(block.text),
                        color = color,
                        style = body.copy(
                            fontSize = size.sp,
                            lineHeight = (size + 5).sp,
                            fontWeight = if (block.level <= 2) FontWeight.Bold else FontWeight.SemiBold
                        )
                    )
                }
                is Block.Paragraph -> {
                    Text(parseInline(block.text), color = color, style = body)
                }
                is Block.ListItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = (block.depth * 8).dp)
                    ) {
                        Text(
                            block.marker,
                            modifier = Modifier.sizeIn(minWidth = 15.dp).padding(end = 5.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = body
                        )
                        Text(parseInline(block.text), modifier = Modifier.weight(1f), color = color, style = body)
                    }
                }
                is Block.Quote -> {
                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            parseInline(block.text),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = body
                        )
                    }
                }
                is Block.Hr -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }
                is Block.CodeBlock -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(start = 10.dp, end = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                block.lang.ifBlank { stringResource(R.string.code_block) },
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            ReplyCopyButton(block.code, codeBlock = true)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                block.code,
                                color = color,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }
                is Block.MathBlock -> {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(readableMath(block.source, mathBlock = true), color = color,
                            style = body)
                    }
                }
                is Block.Table -> {
                    if (roundScreen) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            block.rows.forEach { cells ->
                                Column(modifier = Modifier.fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(10.dp))
                                    .padding(10.dp)) {
                                    cells.forEachIndexed { column, cell ->
                                        if (column > 0) Spacer(Modifier.height(6.dp))
                                        Text(parseInline(block.headers.getOrElse(column) { "" }),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelSmall)
                                        Text(parseInline(cell), color = color,
                                            style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    } else {
                    Column(modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(10.dp))
                        .horizontalScroll(rememberScrollState())) {
                        (listOf(block.headers) + block.rows).forEachIndexed { rowIndex, cells ->
                            Row(modifier = Modifier.height(IntrinsicSize.Max)) {
                                cells.forEach { cell ->
                                    Box(modifier = Modifier.width(132.dp).fillMaxHeight()
                                        .background(if (rowIndex == 0) MaterialTheme.colorScheme.surfaceVariant
                                            else MaterialTheme.colorScheme.surface)
                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                        .padding(horizontal = 8.dp, vertical = 7.dp)) {
                                        Text(parseInline(cell), color = color,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (rowIndex == 0) FontWeight.SemiBold
                                                    else FontWeight.Normal))
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

private sealed class Block {
    data class Heading(val level: Int, val text: String) : Block()
    data class Paragraph(val text: String) : Block()
    data class ListItem(val marker: String, val text: String, val depth: Int) : Block()
    data class Quote(val text: String) : Block()
    data class CodeBlock(val lang: String, val code: String) : Block()
    data class MathBlock(val source: String) : Block()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : Block()
    object Hr : Block()
}

private fun blockSpacing(previous: Block, current: Block): Int = when {
    current is Block.Heading -> if (current.level <= 2) 16 else 12
    previous is Block.Heading -> 7
    previous is Block.ListItem && current is Block.ListItem -> 4
    else -> 11
}

private val headingPattern = Regex("^(#{1,6})\\s+(.+)$")
private val listPattern = Regex("^([-*+]|\\d{1,9}[.)])\\s+(.+)$")

private fun parseBlocks(text: String): List<Block> {
    val result = mutableListOf<Block>()
    val lines = text.split("\n")
    var i = 0
    val paragraph = StringBuilder()
    var hardBreak = false

    fun flushParagraph() {
        if (paragraph.isNotBlank()) result.add(Block.Paragraph(paragraph.toString().trim()))
        paragraph.setLength(0)
        hardBreak = false
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()

        if (trimmed.startsWith("$$") || trimmed.startsWith("\\[")) {
            flushParagraph()
            val closing = if (trimmed.startsWith("$$")) "$$" else "\\]"
            val math = StringBuilder(trimmed.removePrefix(if (closing == "$$") "$$" else "\\["))
            while (!math.toString().contains(closing) && i + 1 < lines.size) {
                i++
                math.append('\n').append(lines[i])
            }
            result.add(Block.MathBlock(math.toString().substringBefore(closing)))
            i++
            continue
        }
        if (i + 1 < lines.size && line.contains('|') && isTableDivider(lines[i + 1])) {
            flushParagraph()
            val headers = tableCells(line)
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                rows.add(tableCells(lines[i]).let { cells ->
                    List(headers.size) { column -> cells.getOrElse(column) { "" } }
                })
                i++
            }
            result.add(Block.Table(headers, rows))
            continue
        }

        if (trimmed.startsWith("```")) {
            flushParagraph()
            val lang = trimmed.removePrefix("```").trim()
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                code.append(lines[i]).append('\n'); i++
            }
            result.add(Block.CodeBlock(lang, code.toString().trimEnd('\n')))
            if (i < lines.size) i++
            continue
        }
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            flushParagraph(); result.add(Block.Hr); i++; continue
        }
        val headingMatch = headingPattern.matchEntire(trimmed)
        if (headingMatch != null) {
            flushParagraph()
            result.add(Block.Heading(headingMatch.groupValues[1].length, headingMatch.groupValues[2]))
            i++; continue
        }
        if (trimmed.startsWith("> ") || trimmed == ">") {
            flushParagraph()
            val quote = mutableListOf<String>()
            while (i < lines.size) {
                val part = lines[i].trimStart()
                if (part != ">" && !part.startsWith("> ")) break
                quote.add(part.removePrefix(">").trimStart())
                i++
            }
            result.add(Block.Quote(quote.joinToString("\n")))
            continue
        }
        val listMatch = listPattern.matchEntire(trimmed)
        if (listMatch != null) {
            flushParagraph()
            val indentation = line.length - trimmed.length
            val marker = listMatch.groupValues[1]
            val content = StringBuilder(listMatch.groupValues[2])
            i++
            while (i < lines.size) {
                val continuation = lines[i].trimStart()
                if (continuation.isBlank() || lines[i].length - continuation.length <= indentation ||
                    listPattern.matches(continuation) || headingPattern.matches(continuation) ||
                    continuation.startsWith(">") || continuation.startsWith("```") ||
                    continuation.startsWith("\\[") || continuation.startsWith("$$")) break
                content.append(' ').append(continuation.trimEnd())
                i++
            }
            result.add(Block.ListItem(
                if (marker in listOf("-", "*", "+")) "•" else marker,
                content.toString(),
                (indentation / 2).coerceAtMost(3)
            ))
            continue
        }
        if (line.isBlank()) { flushParagraph(); i++; continue }
        if (paragraph.isNotEmpty()) paragraph.append(if (hardBreak) '\n' else ' ')
        paragraph.append(line.trim())
        hardBreak = line.trimEnd('\r').endsWith("  ")
        i++
    }
    flushParagraph()
    return result
}

private fun tableCells(line: String): List<String> =
    line.trim().trim('|').split('|').map { it.trim() }

private fun isTableDivider(line: String): Boolean =
    tableCells(line).size >= 2 && tableCells(line).all { it.matches(Regex(":?-{3,}:?")) }

private val inlineMathPattern = Regex(
    """\\\(.*?\\\)|\\\[.*?\\\]|\x24\x24.+?\x24\x24|\x24[^\x24\r\n]+\x24""",
    RegexOption.DOT_MATCHES_ALL
)
private val bareMathPattern = Regex(
    """(?:\\|/)(?:frac|dfrac|tfrac)\{[^{}]+\}\{[^{}]+\}|(?:\\|/)sqrt\{[^{}]+\}|\^(?:\{[+-]?\d+\}|\d)|_\{[+-]?\d+\}"""
)
private val texSymbolPattern = Regex("""\\([A-Za-z]+)""")

private fun parseInline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        if (text[i] == '`') {
            var start = i + 1
            while (start < text.length && text[start] == '`') start++
            val fence = text.substring(i, start)
            var end = text.indexOf(fence, start)
            while (end >= 0 && (text.getOrNull(end - 1) == '`' || text.getOrNull(end + fence.length) == '`')) {
                end = text.indexOf(fence, end + 1)
            }
            if (end >= start) {
                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22808080), fontSize = 14.sp))
                append(text.substring(start, end)); pop()
                i = end + fence.length; continue
            }
        }
        if (text[i] == '$' || text[i] == '\\' || text[i] == '/' || text[i] == '^' || text[i] == '_') {
            val math = inlineMathPattern.matchAt(text, i) ?: bareMathPattern.matchAt(text, i)
            if (math != null) {
                append(readableMath(math.value))
                i = math.range.last + 1
                continue
            }
            if (text[i] == '\\') {
                val command = texSymbolPattern.matchAt(text, i)
                val symbol = command?.let { readableMathSymbol(it.groupValues[1]) }
                if (command != null && symbol != null) {
                    append(symbol)
                    i = command.range.last + 1
                    continue
                }
            }
        }
        val boldMarker = when {
            text.startsWith("**", i) -> "**"
            text.startsWith("__", i) && text.getOrNull(i - 1)?.isLetterOrDigit() != true -> "__"
            else -> null
        }
        if (boldMarker != null) {
            val end = text.indexOf(boldMarker, i + 2)
            if (end > i + 2) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(parseInline(text.substring(i + 2, end))); pop()
                i = end + 2; continue
            }
        }
        if (text.startsWith("~~", i)) {
            val end = text.indexOf("~~", i + 2)
            if (end > i + 2) {
                pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                append(parseInline(text.substring(i + 2, end))); pop()
                i = end + 2; continue
            }
        }
        if ((text[i] == '*' && !text.startsWith("**", i)) ||
            (text[i] == '_' && text.getOrNull(i - 1)?.isLetterOrDigit() != true && !text.startsWith("__", i))) {
            val ch = text[i]
            val end = text.indexOf(ch, i + 1)
            if (end > i + 1 && (end + 1 >= text.length || text[end + 1] != ch)) {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(text.substring(i + 1, end)); pop()
                i = end + 1; continue
            }
        }
        if (text[i] == '[') {
            val close = text.indexOf(']', i + 1)
            if (close > i && close + 1 < text.length && text[close + 1] == '(') {
                val urlEnd = text.indexOf(')', close + 2)
                if (urlEnd > close + 1) {
                    val label = text.substring(i + 1, close)
                    pushStyle(SpanStyle(color = Color(0xFF7C5CFF), textDecoration = TextDecoration.Underline))
                    append(label); pop()
                    i = urlEnd + 1; continue
                }
            }
        }
        append(text[i]); i++
    }
}
