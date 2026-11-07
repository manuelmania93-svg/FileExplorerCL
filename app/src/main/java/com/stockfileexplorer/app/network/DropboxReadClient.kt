package com.stockfileexplorer.app.network

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStream

/** Actual read-only Dropbox endpoints. Caller owns encrypted token storage and authorization. */
internal class DropboxReadClient(private val accessToken: String) {
    data class Entry(val id: String, val name: String, val path: String, val folder: Boolean, val size: Long)
    data class Page(val entries: List<Entry>, val cursor: String, val hasMore: Boolean)
    private fun connection(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = false
        requestMethod = "POST"; setRequestProperty("Authorization", "Bearer $accessToken")
    }
    fun list(path: String = "", cursor: String? = null): Page {
        val endpoint = if (cursor == null) "list_folder" else "list_folder/continue"
        val payload = if (cursor == null) JSONObject().put("path", path).put("recursive", false).put("limit", 200) else JSONObject().put("cursor", cursor)
        val connection = connection("https://api.dropboxapi.com/2/files/$endpoint")
        try {
            connection.setRequestProperty("Content-Type", "application/json"); connection.doOutput = true
            connection.outputStream.use { it.write(payload.toString().toByteArray()) }
            check(connection.responseCode == 200) { "Dropbox list failed (HTTP ${connection.responseCode})" }
            return parsePage(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }
    fun download(id: String, destination: OutputStream): Long {
        require(id.isNotBlank())
        val connection = connection("https://content.dropboxapi.com/2/files/download")
        try {
            connection.setRequestProperty("Dropbox-API-Arg", JSONObject().put("path", id).toString())
            check(connection.responseCode == 200) { "Dropbox download failed (HTTP ${connection.responseCode})" }
            return connection.inputStream.use { it.copyTo(destination) }
        } finally { connection.disconnect() }
    }
    companion object {
        fun parsePage(json: String): Page {
            val value = JSONObject(json); val entries = value.getJSONArray("entries")
            return Page((0 until entries.length()).map { i ->
                val entry = entries.getJSONObject(i)
                Entry(entry.getString("id"), entry.getString("name"), entry.optString("path_lower"), entry.getString(".tag") == "folder", entry.optLong("size"))
            }, value.getString("cursor"), value.getBoolean("has_more"))
        }
    }
}
