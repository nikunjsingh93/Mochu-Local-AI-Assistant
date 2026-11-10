package com.nikunj.gemmabuilder

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallingTest {
    private val weatherRequest = JSONObject(
        """{"messages": [], "tools": [{"type": "function", "function": {"name": "get_weather",
           "description": "Current weather for a city",
           "parameters": {"type": "object", "properties": {"city": {"type": "string"}}, "required": ["city"]}}}]}"""
    )
    private val tools = apiToolsFrom(weatherRequest)
    private var ids = 0
    private fun id() = "call_${++ids}"

    @Test
    fun readsToolsFromRequest() {
        assertEquals(1, tools.size)
        assertEquals("get_weather", tools[0].name)
        assertEquals("Current weather for a city", tools[0].description)
        assertTrue(tools[0].parameters!!.has("properties"))
    }

    @Test
    fun readsOlderFunctionsField() {
        val request = JSONObject("""{"functions": [{"name": "add", "parameters": {"type": "object"}}]}""")
        assertEquals(listOf("add"), apiToolsFrom(request).map { it.name })
    }

    @Test
    fun readsToolChoice() {
        assertEquals(ToolChoice.Auto, toolChoiceFrom(JSONObject("{}")))
        assertEquals(ToolChoice.None, toolChoiceFrom(JSONObject("""{"tool_choice": "none"}""")))
        assertEquals(ToolChoice.Required, toolChoiceFrom(JSONObject("""{"tool_choice": "required"}""")))
        assertEquals(ToolChoice.Named("get_weather"), toolChoiceFrom(JSONObject("""{"tool_choice": {"type": "function", "function": {"name": "get_weather"}}}""")))
    }

    @Test
    fun noneHidesTheTools() {
        assertTrue(toolsOffered(tools, ToolChoice.None).isEmpty())
        assertEquals(tools, toolsOffered(tools, ToolChoice.Auto))
    }

    @Test
    fun promptListsTheTools() {
        val prompt = toolsSystemPrompt(tools, ToolChoice.Auto)
        assertTrue(prompt.contains("\"name\":\"get_weather\""))
        assertTrue(prompt.contains("<tool_call>"))
        assertTrue(toolsSystemPrompt(tools, ToolChoice.Named("get_weather")).contains("You must call get_weather"))
    }

    @Test
    fun parsesTaggedCall() {
        val reply = parseToolCalls("<tool_call>\n{\"name\": \"get_weather\", \"arguments\": {\"city\": \"Paris\"}}\n</tool_call>", tools, ::id)
        assertEquals("", reply.content)
        assertEquals(1, reply.calls.size)
        assertEquals("call_1", reply.calls[0].id)
        assertEquals("get_weather", reply.calls[0].name)
        assertEquals("Paris", JSONObject(reply.calls[0].arguments).getString("city"))
    }

    @Test
    fun keepsTextAroundCallsAndReadsSeveral() {
        val reply = parseToolCalls(
            "Let me check both.\n<tool_call>{\"name\": \"get_weather\", \"arguments\": {\"city\": \"Paris\"}}</tool_call>\n" +
                "<tool_call>{\"name\": \"get_weather\", \"arguments\": {\"city\": \"Rome\"}}</tool_call>",
            tools, ::id
        )
        assertEquals("Let me check both.", reply.content)
        assertEquals(listOf("Paris", "Rome"), reply.calls.map { JSONObject(it.arguments).getString("city") })
    }

    @Test
    fun readsUnclosedTagAndStringArguments() {
        val reply = parseToolCalls("<tool_call>{\"name\": \"get_weather\", \"arguments\": \"{\\\"city\\\": \\\"Oslo\\\"}\"}", tools, ::id)
        assertEquals("Oslo", JSONObject(reply.calls.single().arguments).getString("city"))
    }

    @Test
    fun readsBareJsonCall() {
        val reply = parseToolCalls("```json\n{\"name\": \"get_weather\", \"parameters\": {\"city\": \"Lima\"}}\n```", tools, ::id)
        assertEquals("Lima", JSONObject(reply.calls.single().arguments).getString("city"))
    }

    @Test
    fun ignoresUnknownFunctionsAndPlainText() {
        val madeUp = parseToolCalls("<tool_call>{\"name\": \"send_email\", \"arguments\": {}}</tool_call>", tools, ::id)
        assertTrue(madeUp.calls.isEmpty())
        assertTrue(madeUp.content.contains("send_email"))
        val plain = parseToolCalls("It's sunny in Paris.", tools, ::id)
        assertEquals("It's sunny in Paris.", plain.content)
        assertTrue(plain.calls.isEmpty())
        // JSON that isn't a call stays as the answer.
        assertTrue(parseToolCalls("{\"city\": \"Paris\"}", tools, ::id).calls.isEmpty())
    }

    @Test
    fun readsGemmaNativeCalls() {
        // What Gemma 4 E4B actually wrote on the S26 Ultra.
        val reply = parseToolCalls("<|tool_call>call:get_weather{city:<|\"|>Paris<|\"|>}<tool_call|>", tools, ::id)
        assertEquals("", reply.content)
        assertEquals("Paris", JSONObject(reply.calls.single().arguments).getString("city"))
        val nested = parseToolCalls(
            "Sure.<|tool_call>call:get_weather{city:<|\"|>Rome, Italy<|\"|>,days:3,hours:[9,12],opts:{metric:true,tags:[<|\"|>a<|\"|>]}}<tool_call|>",
            tools, ::id
        )
        assertEquals("Sure.", nested.content)
        val args = JSONObject(nested.calls.single().arguments)
        assertEquals("Rome, Italy", args.getString("city"))
        assertEquals(3, args.getInt("days"))
        assertEquals(12, args.getJSONArray("hours").getInt(1))
        assertTrue(args.getJSONObject("opts").getBoolean("metric"))
        assertEquals("a", args.getJSONObject("opts").getJSONArray("tags").getString(0))
        // No arguments, and no closing tag.
        assertEquals("{}", parseToolCalls("<|tool_call>call:get_weather", tools, ::id).calls.single().arguments)
    }

    @Test
    fun readsSmallModelImprovisedCalls() {
        // What LFM2.5 1.2B actually wrote on the S26 Ultra.
        val withName = parseToolCalls("<name>get_weather</name>\n{\"name\": \"get_weather\", \"arguments\": {\"city\": \"Paris\", \"unit\": \"celsius\"}}", tools, ::id)
        assertEquals("Paris", JSONObject(withName.calls.single().arguments).getString("city"))
        val addEvent = apiToolsFrom(JSONObject("""{"tools": [{"type": "function", "function": {"name": "add_event", "parameters": {"type": "object"}}}]}"""))
        val properties = parseToolCalls("<name>add_event</name>\n{\"properties\": {\"title\": \"Dentist\", \"date\": \"2026-12-03\"}}", addEvent, ::id)
        assertEquals("add_event", properties.calls.single().name)
        assertEquals("Dentist", JSONObject(properties.calls.single().arguments).getString("title"))
        // Stray closing brackets and tags after the JSON, also seen on the S26.
        val extraBrace = parseToolCalls("<name>get_weather</name>\n{\"city\": \"Paris\", \"unit\": \"celsius\"}}", tools, ::id)
        assertEquals("Paris", JSONObject(extraBrace.calls.single().arguments).getString("city"))
        val extraTag = parseToolCalls("<name>get_weather</name>\n{\"arguments\": {\"city\": \"Berlin\"}}</argument>", tools, ::id)
        assertEquals("Berlin", JSONObject(extraTag.calls.single().arguments).getString("city"))
        // A made-up result after the call is dropped.
        val madeUp = parseToolCalls("<name>get_weather</name>\n{\"properties\": {\"city\": \"Paris\"}}</properties>\nThe current weather in Paris is 22°C.", tools, ::id)
        assertEquals("", madeUp.content)
        assertEquals("Paris", JSONObject(madeUp.calls.single().arguments).getString("city"))
        // Prose around the JSON means it's an answer, not a call.
        val prose = parseToolCalls("Here is an example: {\"name\": \"get_weather\", \"arguments\": {\"city\": \"Paris\"}} you can use.", tools, ::id)
        assertTrue(prose.calls.isEmpty())
    }

    @Test
    fun readsLfmNativeCalls() {
        // What LFM2.5 1.2B wrote on the S26 Ultra, in its trained format.
        val reply = parseToolCalls("<|tool_call_start|>[get_weather(city=\"Paris\", unit='celsius')]<|tool_call_end|>", tools, ::id)
        assertEquals("", reply.content)
        val args = JSONObject(reply.calls.single().arguments)
        assertEquals("Paris", args.getString("city"))
        assertEquals("celsius", args.getString("unit"))
        val two = parseToolCalls(
            "<|tool_call_start|>[get_weather(city=\"Rome, Italy\", days=3, metric=True, hours=[9, 12], extra={\"a\": None}), get_weather(city=\"Oslo\")]<|tool_call_end|>",
            tools, ::id
        )
        assertEquals(2, two.calls.size)
        val first = JSONObject(two.calls[0].arguments)
        assertEquals("Rome, Italy", first.getString("city"))
        assertEquals(3, first.getInt("days"))
        assertTrue(first.getBoolean("metric"))
        assertEquals(12, first.getJSONArray("hours").getInt(1))
        assertTrue(first.getJSONObject("extra").isNull("a"))
        assertEquals("Oslo", JSONObject(two.calls[1].arguments).getString("city"))
        assertEquals("Let me check that.".length, streamableLength("Let me check that.\n<|tool_call_start|>[get"))
    }

    @Test
    fun streamingHoldsBackGemmaCalls() {
        assertEquals(4, streamableLength("Okay<|tool_call>call:get"))
        assertEquals(5, streamableLength("Okay <|tool"))
    }

    @Test
    fun streamingHoldsBackCalls() {
        assertEquals(5, streamableLength("Hello"))
        assertEquals(6, streamableLength("Hello <tool_"))
        assertEquals(10, streamableLength("Let me see\n<tool_call>{\"name\""))
        assertEquals(0, streamableLength("{\"name\": \"get"))
        assertEquals(0, streamableLength("```json"))
    }

    @Test
    fun toolCallsJsonMatchesOpenAi() {
        val json = toolCallsJson(listOf(ApiToolCall("call_1", "get_weather", "{\"city\":\"Paris\"}")), withIndex = true).getJSONObject(0)
        assertEquals(0, json.getInt("index"))
        assertEquals("function", json.getString("type"))
        assertEquals("get_weather", json.getJSONObject("function").getString("name"))
        assertEquals("{\"city\":\"Paris\"}", json.getJSONObject("function").getString("arguments"))
        assertFalse(toolCallsJson(emptyList()).length() > 0)
    }

    @Test
    fun conversationIncludesCallsAndResults() {
        val messages = JSONArray(
            """[{"role": "user", "content": "Weather in Paris?"},
               {"role": "assistant", "content": null, "tool_calls": [{"id": "call_9", "type": "function",
                 "function": {"name": "get_weather", "arguments": "{\"city\": \"Paris\"}"}}]},
               {"role": "tool", "tool_call_id": "call_9", "content": "18°C and sunny"}]"""
        )
        val (_, message) = openAiMessagesToPrompt(messages)
        assertTrue(message.contains("User: Weather in Paris?"))
        assertTrue(message.contains("Assistant: <tool_call>"))
        assertTrue(message.contains("\"city\":\"Paris\""))
        assertTrue(message.contains("Result of get_weather: 18°C and sunny"))
        assertTrue(message.contains("Continue as the assistant"))
    }

    @Test
    fun plainConversationIsUnchanged() {
        val messages = JSONArray("""[{"role": "user", "content": "Hi"}, {"role": "assistant", "content": "Hello!"}, {"role": "user", "content": "How are you?"}]""")
        val (system, message) = openAiMessagesToPrompt(messages)
        assertEquals("You are a helpful assistant.", system)
        assertEquals("Conversation so far:\nUser: Hi\nAssistant: Hello!\n\nReply to the user's latest message:\nHow are you?", message)
    }
}
