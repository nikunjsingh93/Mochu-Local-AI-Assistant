package com.nikunj.gemmabuilder

/** Only treat a direct request to open the current page as an action. */
fun wantsHtmlPreview(request: String): Boolean {
    val text = request.trim().trimEnd('.', '!', '?').lowercase()
    if (!Regex("""\b(it|this|that|html|page|website|webpage|file|code|app|browser)\b""").containsMatchIn(text)) return false
    return Regex("""^(?:(?:please|hey mochu)[, ]+)?(?:(?:can|could|would|will) you )?(?:please )?(?:open|show|launch|run|preview)(?:\s+(?:it|this|that|(?:the|my|this|that)\s+(?:html|page|website|webpage|web page|file|code|app)))?(?:\s+(?:in|on|with)\s+(?:the\s+)?(?:browser|web browser))?(?:\s+(?:for me|please))?$""").matches(text)
}

fun isCompleteHtmlDocument(code: String): Boolean =
    Regex("(?is)<html(?:\\s|>)").containsMatchIn(code) && Regex("(?is)</html\\s*>").containsMatchIn(code)

/** Pick a complete HTML code fence; never interpret ordinary prose or incomplete streaming code. */
fun lastHtmlDocument(messages: List<String>): String? {
    val fence = Regex("(?is)```(?:html|htm)?[ \\t]*\\r?\\n(.*?)\\r?\\n```")
    for (message in messages.asReversed()) {
        val document = fence.findAll(message).map { it.groupValues[1].trim() }.lastOrNull { isCompleteHtmlDocument(it) }
        if (document != null) return document
    }
    return null
}
