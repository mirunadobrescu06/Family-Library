package com.example.library.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ApiException(message: String) : Exception(message)

data class LibrarySnapshot(
    val books: List<Book>,
    val extraColumns: List<String>,
    val sheetUrl: String?,
    val version: String,
)

data class ImportResult(val added: Int, val updated: Int, val skipped: Int)

class SheetApi(private val baseUrl: String, private val token: String) {
    suspend fun version(): String = get("version").optString("version", "0")

    suspend fun list(): LibrarySnapshot {
        val o = get("list")
        val cols = o.optJSONArray("extraColumns")
        return LibrarySnapshot(
            books = Book.listFromJson(o.optJSONArray("books")),
            extraColumns = if (cols == null) emptyList() else (0 until cols.length()).map { cols.optString(it) },
            sheetUrl = o.optString("sheetUrl", "").ifBlank { null },
            version = o.optString("version", "0"),
        )
    }

    suspend fun add(book: Book, user: String): String =
        post(JSONObject().put("action", "add").put("user", user).put("book", book.toEditableJson()))
            .getString("id")

    suspend fun update(book: Book, user: String) {
        post(JSONObject().put("action", "update").put("user", user).put("id", book.id).put("book", book.toEditableJson()))
    }

    suspend fun delete(id: String, user: String) {
        post(JSONObject().put("action", "delete").put("user", user).put("id", id))
    }

    suspend fun import(table: SheetTable, user: String): ImportResult {
        val rows = JSONArray()
        table.rows.forEach { r -> rows.put(JSONArray(r)) }
        val o = post(
            JSONObject().put("action", "import").put("user", user)
                .put("headers", JSONArray(table.headers)).put("rows", rows)
        )
        return ImportResult(o.optInt("added"), o.optInt("updated"), o.optInt("skipped"))
    }

    private suspend fun get(action: String): JSONObject = withContext(Dispatchers.IO) {
        val url = "$baseUrl?action=${enc(action)}&token=${enc(token)}"
        val conn = open(url)
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = true
        readJson(conn)
    }

    private suspend fun post(body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        body.put("token", token)
        val conn = open(baseUrl)
        val code = try {
            conn.requestMethod = "POST"
            conn.instanceFollowRedirects = false
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            conn.responseCode
        } catch (e: IOException) {
            conn.disconnect()
            throw ApiException("No connection. Check your internet and try again.")
        }
        if (code in 300..399) {
            val location = conn.getHeaderField("Location") ?: throw ApiException("Server redirect without location")
            conn.disconnect()
            val next = open(location)
            next.requestMethod = "GET"
            next.instanceFollowRedirects = true
            readJson(next)
        } else {
            readJson(conn)
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 45_000
            setRequestProperty("Accept", "application/json")
        }

    private fun readJson(conn: HttpURLConnection): JSONObject {
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw ApiException("The Google Sheet didn't answer (error $code).")
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw ApiException("The Google Sheet link isn't set up correctly. Check the Web App access is set to \"Anyone\".")
            }
            if (!json.optBoolean("ok", false)) {
                throw ApiException(json.optString("error", "Something went wrong"))
            }
            return json
        } catch (e: IOException) {
            throw ApiException("No connection. Check your internet and try again.")
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
