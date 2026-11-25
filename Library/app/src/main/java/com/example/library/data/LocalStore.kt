package com.example.library.data

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("family_library", Context.MODE_PRIVATE)
    private val cacheFile = File(context.filesDir, "library_cache.json")

    var userName: String?
        get() = prefs.getString("user_name", null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString("user_name", value).apply()

    var lastLocation: String
        get() = prefs.getString("last_location", "44") ?: "44"
        set(value) = prefs.edit().putString("last_location", value).apply()

    fun loadLastOpened(): Map<String, Long> {
        val raw = prefs.getString("last_opened", null) ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { o.optLong(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun saveLastOpened(map: Map<String, Long>) {
        prefs.edit().putString("last_opened", JSONObject(map as Map<*, *>).toString()).apply()
    }

    fun loadCache(): LibrarySnapshot? {
        if (!cacheFile.exists()) return null
        return try {
            val o = JSONObject(cacheFile.readText())
            val cols = o.optJSONArray("extraColumns")
            LibrarySnapshot(
                books = Book.listFromJson(o.optJSONArray("books")),
                extraColumns = if (cols == null) emptyList() else (0 until cols.length()).map { cols.optString(it) },
                sheetUrl = o.optString("sheetUrl", "").ifBlank { null },
                version = o.optString("version", "0"),
            )
        } catch (e: Exception) {
            null
        }
    }

    fun saveCache(snapshot: LibrarySnapshot) {
        try {
            val books = JSONArray()
            snapshot.books.filterNot { it.isLocal }.forEach { books.put(it.toJson()) }
            val o = JSONObject()
                .put("books", books)
                .put("extraColumns", JSONArray(snapshot.extraColumns))
                .put("sheetUrl", snapshot.sheetUrl ?: "")
                .put("version", snapshot.version)
            val tmp = File(cacheFile.path + ".tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(cacheFile)
        } catch (_: Exception) {
        }
    }
}
