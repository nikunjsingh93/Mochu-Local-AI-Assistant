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

fun newBlankConversation(): ChatConversation = ChatConversation(
    id = UUID.randomUUID().toString(),
    title = "New chat",
    updatedAt = System.currentTimeMillis(),
    messages = emptyList()
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
    var updatedAt = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
    val messages = mutableListOf<ChatMessage>()
    file.readLines().forEach { line ->
        when {
            line.startsWith("title\t") -> title = decodeText(line.substringAfter("title\t"))
            line.startsWith("updated\t") -> updatedAt = line.substringAfter("updated\t").toLongOrNull() ?: updatedAt
            line.startsWith("summary\t") -> summary = decodeText(line.substringAfter("summary\t"))
            line.startsWith("summarized\t") -> summarizedCount = line.substringAfter("summarized\t").toIntOrNull() ?: 0
            line.startsWith("msg	") -> {
                val parts = line.split("	", limit = 6)
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
                                statsInline = statsInline
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
        summarizedCount = summarizedCount.coerceIn(0, messages.size)
    )
}

fun saveConversationFile(context: Context, conversation: ChatConversation) {
    val root = chatsRoot(context)
    root.mkdirs()
    val file = File(root, "${conversation.id}.chat")
    val text = buildString {
        appendLine("title\t${encodeText(conversation.title)}")
        appendLine("updated\t${conversation.updatedAt}")
        if (conversation.summary.isNotBlank()) {
            appendLine("summary\t${encodeText(conversation.summary)}")
            appendLine("summarized\t${conversation.summarizedCount}")
        }
        conversation.messages.forEach { msg ->
            val attachmentsEncoded = encodeText(msg.attachments.joinToString("|"))
            val statsEncoded = encodeText(msg.statsInline.orEmpty())
            appendLine("msg\t${msg.role}\t${msg.timestamp}\t${encodeText(msg.text)}\t$attachmentsEncoded\t$statsEncoded")
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

