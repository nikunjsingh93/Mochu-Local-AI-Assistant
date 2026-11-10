package com.nikunj.gemmabuilder

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// OpenAI-style function calling for the local API server. Clients send "tools"; the model answers with
// <tool_call>{"name": ..., "arguments": {...}}</tool_call> blocks, which go back as "tool_calls".
// The tools are described in the system prompt and the reply is read here, rather than through each
// runtime's own template, so it works the same with every engine (LiteRT, GGUF, home server, combined devices).

/** A function an API client offers the model. */
data class ApiTool(val name: String, val description: String, val parameters: JSONObject?)

/** A function call the model asked for; [arguments] is a JSON string, as OpenAI sends it. */
data class ApiToolCall(val id: String, val name: String, val arguments: String)

/** A reply split into its text and the function calls in it. */
data class ToolReply(val content: String, val calls: List<ApiToolCall>)

sealed interface ToolChoice {
    data object Auto : ToolChoice
    data object None : ToolChoice
    data object Required : ToolChoice
    data class Named(val name: String) : ToolChoice
}

private const val CALL_OPEN = "<tool_call>"
private const val CALL_CLOSE = "</tool_call>"
// Gemma 4's own call format, which it sometimes writes instead of the one asked for:
// <|tool_call>call:get_weather{city:<|"|>Paris<|"|>}<tool_call|>
private const val GEMMA_OPEN = "<|tool_call>"
private const val GEMMA_QUOTE = "<|\"|>"
// LFM2's own format, with Python-style arguments: <|tool_call_start|>[get_weather(city="Paris")]<|tool_call_end|>
private const val LFM_OPEN = "<|tool_call_start|>"
private val LFM_CALL = Regex("""<\|tool_call_start\|>(.*?)(?:<\|tool_call_end\|>|$)""", RegexOption.DOT_MATCHES_ALL)
private val GEMMA_CALL = Regex("""<\|tool_call>\s*call:([\w.\-]+)\s*(\{.*?\})?\s*(?:<tool_call\|>|$)""", RegexOption.DOT_MATCHES_ALL)

/** Reads "tools" (or the older "functions") from a chat completion request. */
fun apiToolsFrom(request: JSONObject): List<ApiTool> {
    val list = request.optJSONArray("tools") ?: request.optJSONArray("functions") ?: return emptyList()
    return (0 until list.length()).mapNotNull { i ->
        val entry = list.optJSONObject(i) ?: return@mapNotNull null
        if (entry.has("type") && entry.optString("type") != "function") return@mapNotNull null
        val function = entry.optJSONObject("function") ?: entry
        val name = function.optString("name").trim()
        if (name.isEmpty()) null else ApiTool(name, function.optString("description").trim(), function.optJSONObject("parameters"))
    }
}

/** Reads "tool_choice" (or the older "function_call"); anything unknown means the model decides. */
fun toolChoiceFrom(request: JSONObject): ToolChoice = when (val choice = request.opt("tool_choice") ?: request.opt("function_call")) {
    is String -> when (choice) {
        "none" -> ToolChoice.None
        "required", "any" -> ToolChoice.Required
        else -> ToolChoice.Auto
    }
    is JSONObject -> (choice.optJSONObject("function")?.optString("name") ?: choice.optString("name"))
        .takeIf { it.isNotBlank() }?.let { ToolChoice.Named(it) } ?: ToolChoice.Auto
    else -> ToolChoice.Auto
}

/** The tools the model is shown: none when the client turned them off, only the one it named if it named one. */
fun toolsOffered(tools: List<ApiTool>, choice: ToolChoice): List<ApiTool> = when (choice) {
    ToolChoice.None -> emptyList()
    is ToolChoice.Named -> tools.filter { it.name == choice.name }.ifEmpty { tools }
    else -> tools
}

/** The part of the system prompt that lists the tools and how to call them. */
fun toolsSystemPrompt(tools: List<ApiTool>, choice: ToolChoice): String = buildString {
    append("# Tools\n\nYou can call these functions:\n")
    tools.forEach { tool ->
        val spec = JSONObject().put("name", tool.name)
        if (tool.description.isNotEmpty()) spec.put("description", tool.description)
        spec.put("parameters", tool.parameters ?: JSONObject().put("type", "object").put("properties", JSONObject()))
        append(spec.toString()).append('\n')
    }
    append("\nTo call a function, reply with only this, and nothing after it:\n")
    append("$CALL_OPEN\n{\"name\": \"function_name\", \"arguments\": {\"argument_name\": \"value\"}}\n$CALL_CLOSE\n")
    append("To call several functions, write one block for each. Use the exact function and argument names above. ")
    append("Don't make up results: the function's result comes back in the next message.\n")
    when (choice) {
        ToolChoice.Required -> append("You must call at least one function now.")
        is ToolChoice.Named -> append("You must call ${choice.name} now.")
        else -> append("If no function is needed, answer normally without a $CALL_OPEN block.")
    }
}

/**
 * Finds the function calls in a finished reply. Calls in <tool_call> blocks are read first; a reply that is
 * only a JSON call (some models skip the tags) also counts. Calls to functions that weren't offered are
 * left in the text, so a made-up name never reaches the client as a call.
 */
fun parseToolCalls(reply: String, tools: List<ApiTool>, newId: () -> String = ::newToolCallId): ToolReply {
    val names = tools.map { it.name }.toSet()
    val answer = withLfmCallsAsJson(withGemmaCallsAsJson(reply))
    val calls = mutableListOf<Pair<String, String>>()
    val content = StringBuilder()
    var at = 0
    while (true) {
        val open = answer.indexOf(CALL_OPEN, at)
        if (open < 0) {
            content.append(answer.substring(at))
            break
        }
        content.append(answer.substring(at, open))
        val bodyStart = open + CALL_OPEN.length
        val close = answer.indexOf(CALL_CLOSE, bodyStart)
        val end = if (close >= 0) close + CALL_CLOSE.length else answer.length
        val body = answer.substring(bodyStart, if (close >= 0) close else answer.length)
        val found = callsInJson(body, names).ifEmpty { xmlCalls(body, names) }
        if (found.isEmpty()) content.append(answer.substring(open, end)) else calls += found
        at = end
    }
    if (calls.isEmpty()) {
        val bare = callsInJson(answer, names).ifEmpty { looseCalls(answer, names) }
        if (bare.isNotEmpty()) return ToolReply("", bare.map { (name, args) -> ApiToolCall(newId(), name, args) })
    }
    return ToolReply(content.toString().trim(), calls.map { (name, args) -> ApiToolCall(newId(), name, args) })
}

/**
 * How much of a streaming answer can go out as text. Text stops where a <tool_call> block begins, and an
 * answer that starts like a bare JSON call is held until the reply is finished.
 */
fun streamableLength(answer: String): Int {
    val openers = listOf(CALL_OPEN, GEMMA_OPEN, LFM_OPEN, "<name>")
    val open = openers.map { answer.indexOf(it) }.filter { it >= 0 }.minOrNull()
    if (open != null) return answer.substring(0, open).trimEnd().length
    val start = answer.trimStart()
    if (start.startsWith("{") || start.startsWith("[") || start.startsWith("```")) return 0
    // Hold a tag that is only partly streamed, like "<tool_" or "<|tool".
    val hold = openers.maxOf { tag ->
        (minOf(tag.length - 1, answer.length) downTo 1).firstOrNull { answer.endsWith(tag.take(it)) } ?: 0
    }
    return answer.length - hold
}

/** Rewrites Gemma 4's own call blocks as <tool_call> JSON blocks, so one parser reads both. */
internal fun withGemmaCallsAsJson(text: String): String {
    if (!text.contains(GEMMA_OPEN)) return text
    return GEMMA_CALL.replace(text) { match ->
        val args = match.groupValues[2].ifEmpty { "{}" }
        val json = runCatching { JSONObject(gemmaArgsToJson(args)) }.getOrNull() ?: return@replace match.value
        "$CALL_OPEN${JSONObject().put("name", match.groupValues[1]).put("arguments", json)}$CALL_CLOSE"
    }
}

/** Rewrites LFM2's Python-style call blocks as <tool_call> JSON blocks. */
internal fun withLfmCallsAsJson(text: String): String {
    if (!text.contains(LFM_OPEN)) return text
    return LFM_CALL.replace(text) { match ->
        val calls = runCatching { pythonCalls(match.groupValues[1].trim()) }.getOrNull()
        if (calls.isNullOrEmpty()) return@replace match.value
        calls.joinToString("") { (name, args) -> "$CALL_OPEN${JSONObject().put("name", name).put("arguments", args)}$CALL_CLOSE" }
    }
}

/** Reads `[name(key="value", n=3), other()]` into (name, arguments) pairs. */
private fun pythonCalls(text: String): List<Pair<String, JSONObject>> {
    val src = text.removePrefix("[").removeSuffix("]").trim()
    val calls = mutableListOf<Pair<String, JSONObject>>()
    var i = 0
    while (i < src.length) {
        while (i < src.length && (src[i] == ',' || src[i].isWhitespace())) i++
        if (i >= src.length) break
        val paren = src.indexOf('(', i)
        if (paren < 0) break
        val name = src.substring(i, paren).trim()
        val out = StringBuilder("{")
        i = paren + 1
        var depth = 0
        var expectKey = true
        while (i < src.length) {
            val c = src[i]
            when {
                c == '"' || c == '\'' -> {
                    val value = StringBuilder()
                    i++
                    while (i < src.length && src[i] != c) {
                        if (src[i] == '\\' && i + 1 < src.length) i++
                        value.append(src[i]); i++
                    }
                    out.append(JSONObject.quote(value.toString()))
                    expectKey = false
                }
                depth == 0 && c == ')' -> { i++; break }
                // Nested lists, tuples and dicts; dict keys are quoted strings already, so no bare keys inside.
                c == '(' || c == '[' || c == '{' -> { depth++; out.append(if (c == '(') '[' else c); expectKey = false }
                c == ')' || c == ']' || c == '}' -> { depth--; out.append(if (c == ')') ']' else c) }
                c == ',' -> { out.append(','); expectKey = depth == 0 }
                c == '=' -> out.append(':')
                c.isLetter() || c == '_' -> {
                    var end = i
                    while (end < src.length && (src[end].isLetterOrDigit() || src[end] == '_' || src[end] == '.')) end++
                    val word = src.substring(i, end)
                    out.append(
                        when {
                            expectKey && depth == 0 -> JSONObject.quote(word)
                            word == "True" || word == "true" -> "true"
                            word == "False" || word == "false" -> "false"
                            word == "None" || word == "null" -> "null"
                            else -> JSONObject.quote(word)
                        }
                    )
                    expectKey = false
                    i = end
                    continue
                }
                else -> out.append(c)
            }
            i++
        }
        calls += name to JSONObject(out.append('}').toString())
    }
    return calls
}

/** Gemma writes keys bare and strings between <|"|> marks: {city:<|"|>Paris<|"|>,days:3}. */
private fun gemmaArgsToJson(args: String): String {
    val out = StringBuilder()
    // Open brackets, so a comma inside a list isn't read as the start of a key.
    val open = ArrayDeque<Char>()
    var i = 0
    var expectKey = false
    while (i < args.length) {
        val c = args[i]
        when {
            args.startsWith(GEMMA_QUOTE, i) -> {
                val end = args.indexOf(GEMMA_QUOTE, i + GEMMA_QUOTE.length).let { if (it < 0) args.length else it }
                out.append(JSONObject.quote(args.substring(i + GEMMA_QUOTE.length, end)))
                i = end + GEMMA_QUOTE.length
                expectKey = false
                continue
            }
            c == '{' || c == '[' -> { open.addLast(c); out.append(c); expectKey = c == '{' }
            c == '}' || c == ']' -> { open.removeLastOrNull(); out.append(c); expectKey = false }
            c == ',' -> { out.append(c); expectKey = open.lastOrNull() == '{' }
            expectKey && (c.isLetter() || c == '_') -> {
                val colon = args.indexOf(':', i)
                if (colon < 0) { out.append(args.substring(i)); break }
                out.append(JSONObject.quote(args.substring(i, colon).trim())).append(':')
                i = colon + 1
                expectKey = false
                continue
            }
            else -> { out.append(c); if (!c.isWhitespace()) expectKey = false }
        }
        i++
    }
    return out.toString()
}

/** Writes an assistant message's calls the way the model is asked to write them, for the conversation prompt. */
fun toolCallsAsText(toolCalls: JSONArray): String = (0 until toolCalls.length()).mapNotNull { i ->
    val function = toolCalls.optJSONObject(i)?.optJSONObject("function") ?: return@mapNotNull null
    val args = when (val raw = function.opt("arguments")) {
        is JSONObject -> raw
        is String -> runCatching { JSONObject(raw) }.getOrElse { raw }
        else -> JSONObject()
    }
    "$CALL_OPEN\n${JSONObject().put("name", function.optString("name")).put("arguments", args)}\n$CALL_CLOSE"
}.joinToString("\n")

/** The OpenAI "tool_calls" array for [calls]; streamed chunks also carry each call's index. */
fun toolCallsJson(calls: List<ApiToolCall>, withIndex: Boolean = false): JSONArray = JSONArray().apply {
    calls.forEachIndexed { i, call ->
        val item = JSONObject()
        if (withIndex) item.put("index", i)
        item.put("id", call.id).put("type", "function")
            .put("function", JSONObject().put("name", call.name).put("arguments", call.arguments))
        put(item)
    }
}

private fun newToolCallId(): String = "call_" + UUID.randomUUID().toString().replace("-", "").take(24)

private val XML_FUNCTION = Regex("""<function=([\w.\-]+)>(.*?)(?:</function>|$)""", RegexOption.DOT_MATCHES_ALL)
private val XML_PARAMETER = Regex("""<parameter=([\w.\-]+)>(.*?)(?:</parameter>|$)""", RegexOption.DOT_MATCHES_ALL)

/**
 * The XML call style inside <tool_call> that LFM3 writes (as Qwen3-Coder does):
 * <function=get_weather><parameter=city>Paris</parameter></function>. Values that read as JSON
 * (numbers, true/false, lists, objects) keep their type; anything else is a string.
 */
private fun xmlCalls(body: String, names: Set<String>): List<Pair<String, String>> =
    XML_FUNCTION.findAll(body).mapNotNull { function ->
        val name = function.groupValues[1]
        if (name !in names) return@mapNotNull null
        val args = JSONObject()
        XML_PARAMETER.findAll(function.groupValues[2]).forEach { parameter ->
            val raw = parameter.groupValues[2].trim()
            val value: Any = when {
                raw.startsWith("{") -> runCatching { JSONObject(raw) }.getOrDefault(raw)
                raw.startsWith("[") -> runCatching { JSONArray(raw) }.getOrDefault(raw)
                raw == "true" || raw == "false" -> raw.toBoolean()
                raw == "null" -> JSONObject.NULL
                raw.matches(Regex("""-?\d+""")) -> raw.toLongOrNull() ?: raw
                raw.matches(Regex("""-?\d+\.\d+([eE][-+]?\d+)?""")) -> raw.toDouble()
                else -> raw
            }
            args.put(parameter.groupValues[1], value)
        }
        name to args.toString()
    }.toList()

private val NAME_TAG = Regex("""<name>\s*([\w.\-]+)\s*</name>""")

/**
 * Small models (LFM2.5 1.2B on the S26) sometimes improvise: `<name>get_weather</name>` then a JSON call,
 * or the arguments under "properties". A reply that is nothing but such pieces counts as calls;
 * any other text around them means it's an answer, so it's left alone.
 */
private fun looseCalls(answer: String, names: Set<String>): List<Pair<String, String>> {
    val tagName = NAME_TAG.find(answer)?.groupValues?.get(1)?.takeIf { it in names }
    var rest = NAME_TAG.replace(answer, " ")
    val calls = mutableListOf<Pair<String, String>>()
    val spans = jsonObjectSpans(rest)
    // Text before the call means it's an answer. Text after a call is usually a made-up result
    // ("The weather in Paris is 22°C."), which the model was told not to write, so it's dropped.
    val firstCall = spans.firstOrNull()?.let { rest.indexOf(it) } ?: rest.length
    if (rest.substring(0, firstCall).replace("```json", "").replace("```", "").isNotBlank()) return emptyList()
    val lastCall = spans.lastOrNull()?.let { rest.lastIndexOf(it) + it.length } ?: rest.length
    rest = rest.substring(0, lastCall)
    for (span in spans) {
        val obj = runCatching { JSONObject(span) }.getOrNull() ?: return emptyList()
        val call = obj.optJSONObject("function") ?: obj
        val name = call.optString("name").trim().takeIf { it in names } ?: tagName ?: return emptyList()
        val args = call.optJSONObject("arguments") ?: call.optJSONObject("parameters") ?: call.optJSONObject("properties")
            ?: call.optString("arguments").takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: if (call.has("name")) JSONObject() else call
        calls += name to args.toString()
        rest = rest.replace(span, " ")
    }
    if (calls.isEmpty() && tagName != null) calls += tagName to "{}"
    // Stray closing brackets and made-up closing tags ("}}", "</argument>") are noise, not an answer.
    val leftover = rest.replace("```json", "").replace("```", "").replace(Regex("""</\w+>"""), "").replace(Regex("""[}\]\s]"""), "")
    return if (leftover.isEmpty()) calls else emptyList()
}

/** The top-level {...} objects in [text], skipping braces inside JSON strings. */
private fun jsonObjectSpans(text: String): List<String> {
    val spans = mutableListOf<String>()
    var depth = 0
    var start = -1
    var inString = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            inString && c == '\\' -> i++
            c == '"' && depth > 0 -> inString = !inString
            !inString && c == '{' -> { if (depth == 0) start = i; depth++ }
            !inString && c == '}' && depth > 0 -> { depth--; if (depth == 0) spans += text.substring(start, i + 1) }
        }
        i++
    }
    return spans
}

/** Reads calls from JSON text: one call, a list of calls, or OpenAI's {"function": {...}} shape. */
private fun callsInJson(text: String, names: Set<String>): List<Pair<String, String>> {
    val json = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    val items = when {
        json.startsWith("{") -> runCatching { listOf(JSONObject(json)) }.getOrNull()
        json.startsWith("[") -> runCatching { JSONArray(json).let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } }.getOrNull()
        else -> null
    } ?: return emptyList()
    return items.mapNotNull { item ->
        val call = item.optJSONObject("function") ?: item
        val name = call.optString("name").trim()
        if (name !in names) return@mapNotNull null
        val args = when (val raw = call.opt("arguments") ?: call.opt("parameters")) {
            is JSONObject -> raw.toString()
            is String -> runCatching { JSONObject(raw).toString() }.getOrDefault("{}")
            else -> "{}"
        }
        name to args
    }
}
