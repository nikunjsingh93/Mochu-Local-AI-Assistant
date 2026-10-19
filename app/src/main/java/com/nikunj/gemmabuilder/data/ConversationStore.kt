package com.nikunj.gemmabuilder

import android.content.Context
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

fun newBlankConversation(kind: String = CHAT_KIND_STUDIO): ChatConversation = ChatConversation(
    id = UUID.randomUUID().toString(),
    title = "New chat",
    updatedAt = System.currentTimeMillis(),
    messages = emptyList(),
    kind = kind
)

fun chatsRoot(context: Context): File = File(context.filesDir, "chats")
fun projectsRoot(context: Context): File = File(context.filesDir, "projects")

fun loadConversations(context: Context): List<ChatConversation> {
    val root = chatsRoot(context)
    root.mkdirs()
    return root.listFiles { file -> file.isFile && file.name.endsWith(".chat") }
        ?.mapNotNull { file -> runCatching { readConversationFile(file) }.getOrNull() }
        ?.sortedByDescending { it.updatedAt }
        ?: emptyList()
}

fun readConversationFile(file: File): ChatConversation {
    var title = "New chat"
    var summary = ""
    var summarizedCount = 0
    var kind = CHAT_KIND_STUDIO
    var updatedAt = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
    val messages = mutableListOf<ChatMessage>()
    file.readLines().forEach { line ->
        when {
            line.startsWith("title\t") -> title = decodeText(line.substringAfter("title\t"))
            line.startsWith("updated\t") -> updatedAt = line.substringAfter("updated\t").toLongOrNull() ?: updatedAt
            line.startsWith("summary\t") -> summary = decodeText(line.substringAfter("summary\t"))
            line.startsWith("kind\t") -> kind = line.substringAfter("kind\t").ifBlank { CHAT_KIND_STUDIO }
            line.startsWith("summarized\t") -> summarizedCount = line.substringAfter("summarized\t").toIntOrNull() ?: 0
            line.startsWith("msg	") -> {
                val parts = line.split("	", limit = 7)
                when {
                    parts.size >= 5 -> {
                        val attachments = decodeText(parts[4])
                            .split('|')
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                        val statsInline = parts.getOrNull(5)?.let(::decodeText)?.takeIf { it.isNotBlank() }
                        messages.add(
                            ChatMessage(
                                role = parts[1],
                                text = decodeText(parts[3]),
                                timestamp = parts[2].toLongOrNull() ?: updatedAt,
                                attachments = attachments,
                                statsInline = statsInline,
                                activity = parts.getOrNull(6)?.let { decodeActivity(decodeText(it)) }.orEmpty()
                            )
                        )
                    }
                    parts.size == 4 -> messages.add(ChatMessage(parts[1], decodeText(parts[3]), parts[2].toLongOrNull() ?: updatedAt))
                    parts.size == 3 -> messages.add(ChatMessage(parts[1], decodeText(parts[2]), updatedAt))
                }
            }
        }
    }
    return ChatConversation(
        id = file.nameWithoutExtension,
        title = title,
        updatedAt = updatedAt,
        messages = messages,
        summary = summary,
        summarizedCount = summarizedCount.coerceIn(0, messages.size),
        kind = kind
    )
}

fun saveConversationFile(context: Context, conversation: ChatConversation) {
    val root = chatsRoot(context)
    root.mkdirs()
    val file = File(root, "${conversation.id}.chat")
    val text = buildString {
        appendLine("title\t${encodeText(conversation.title)}")
        appendLine("updated\t${conversation.updatedAt}")
        appendLine("kind\t${conversation.kind}")
        if (conversation.summary.isNotBlank()) {
            appendLine("summary\t${encodeText(conversation.summary)}")
            appendLine("summarized\t${conversation.summarizedCount}")
        }
        conversation.messages.forEach { msg ->
            val attachmentsEncoded = encodeText(msg.attachments.joinToString("|"))
            val statsEncoded = encodeText(msg.statsInline.orEmpty())
            val activityEncoded = encodeText(encodeActivity(msg.activity))
            appendLine("msg\t${msg.role}\t${msg.timestamp}\t${encodeText(msg.text)}\t$attachmentsEncoded\t$statsEncoded\t$activityEncoded")
        }
    }
    file.writeText(text)
}

fun encodeText(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
fun decodeText(value: String): String = URLDecoder.decode(value, Charsets.UTF_8.name())
fun formatChatTimestamp(timestamp: Long): String {
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val now = Calendar.getInstance()
    val sameDay = then.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) "h:mm a" else if (then.get(Calendar.YEAR) == now.get(Calendar.YEAR)) "MMM d" else "MMM d, yyyy"
    return SimpleDateFormat(pattern, Locale.US).format(Date(timestamp))
}


// Activity steps are stored as kind / text / url fields joined by unit separators, one step per
// record separator. These characters never appear in step text.
private const val FIELD_SEP = '\u001F'
private const val STEP_SEP = '\u001E'

fun encodeActivity(steps: List<ActivityStep>): String =
    steps.joinToString(STEP_SEP.toString()) { listOf(it.kind, it.text, it.url.orEmpty()).joinToString(FIELD_SEP.toString()) }

fun decodeActivity(raw: String): List<ActivityStep> =
    raw.split(STEP_SEP).filter { it.isNotBlank() }.mapNotNull { record ->
        val fields = record.split(FIELD_SEP)
        if (fields.size < 2) null else ActivityStep(fields[0], fields[1], fields.getOrNull(2)?.ifBlank { null })
    }
