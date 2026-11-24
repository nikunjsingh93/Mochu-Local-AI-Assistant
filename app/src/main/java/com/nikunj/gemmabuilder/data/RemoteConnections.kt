package com.nikunj.gemmabuilder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

data class RemoteConnection(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val contextTokens: Int = 0,
    val lastModel: String? = null
)

fun remoteConnectionAddress(input: String): String? {
    if (input.isBlank()) return null
    if ("://" in input && !input.trimStart().startsWith("https://") && !input.trimStart().startsWith("http://")) return null
    val base = normalizeBaseUrl(input)
    val uri = runCatching { URI(base) }.getOrNull() ?: return null
    return base.takeIf {
        uri.host != null && uri.userInfo == null && uri.query == null &&
            uri.fragment == null && uri.port in -1..65535
    }
}

internal fun encodeRemoteConnections(connections: List<RemoteConnection>): String =
    JSONArray().apply {
        connections.forEach { connection ->
            put(JSONObject().apply {
                put("id", connection.id)
                put("name", connection.name)
                put("baseUrl", connection.baseUrl)
                put("apiKey", connection.apiKey)
                put("contextTokens", connection.contextTokens)
                put("lastModel", connection.lastModel)
            })
        }
    }.toString()

internal fun decodeRemoteConnections(json: String?, legacy: RemoteModelConfig?): List<RemoteConnection> {
    if (json == null) {
        return listOfNotNull(legacy?.let {
            RemoteConnection("legacy", "My server", it.baseUrl, it.apiKey, it.contextTokens, it.model)
        })
    }
    val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val id = item.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
        val base = remoteConnectionAddress(item.optString("baseUrl")) ?: return@mapNotNull null
        RemoteConnection(
            id, item.optString("name"), base, item.optString("apiKey"),
            item.optInt("contextTokens"), item.optString("lastModel").takeIf(String::isNotBlank)
        )
    }
}

fun savedRemoteConnections(context: Context): List<RemoteConnection> {
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val json = prefs.getString("remoteConnections", null)
    val connections = decodeRemoteConnections(json, savedRemoteModelConfig(context))
    if (json == null) saveRemoteConnections(context, connections)
    return connections
}

fun saveRemoteConnections(context: Context, connections: List<RemoteConnection>) {
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
        .putString("remoteConnections", encodeRemoteConnections(connections)).apply()
}
