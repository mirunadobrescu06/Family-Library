package com.example.library.data

import org.json.JSONArray
import org.json.JSONObject

data class Book(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val year: String = "",
    val location: String = "",
    val comments: String = "",
    val extra: Map<String, String> = emptyMap(),
    val added: Long = 0L,
    val modified: Long = 0L,
    val addedBy: String = "",
    val modifiedBy: String = "",
) {
    val isLocal: Boolean get() = id.startsWith(LOCAL_ID_PREFIX)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("author", author)
        put("year", year)
        put("location", location)
        put("comments", comments)
        put("extra", JSONObject(extra as Map<*, *>))
        put("added", added)
        put("modified", modified)
        put("addedBy", addedBy)
        put("modifiedBy", modifiedBy)
    }

    fun toEditableJson(): JSONObject = JSONObject().apply {
        put("title", title.trim())
        put("author", author.trim())
        put("year", year.trim())
        put("location", location.trim())
        put("comments", comments.trim())
        put("extra", JSONObject(extra.mapValues { it.value.trim() } as Map<*, *>))
    }

    companion object {
        const val LOCAL_ID_PREFIX = "local-"

        fun fromJson(o: JSONObject): Book {
            val extraObj = o.optJSONObject("extra")
            val extra = LinkedHashMap<String, String>()
            extraObj?.keys()?.forEach { k -> extra[k] = extraObj.optString(k, "") }
            return Book(
                id = o.optString("id", ""),
                title = o.optString("title", ""),
                author = o.optString("author", ""),
                year = o.optString("year", ""),
                location = o.optString("location", ""),
                comments = o.optString("comments", ""),
                extra = extra,
                added = o.optLong("added", 0L),
                modified = o.optLong("modified", 0L),
                addedBy = o.optString("addedBy", ""),
                modifiedBy = o.optString("modifiedBy", ""),
            )
        }

        fun listFromJson(a: JSONArray?): List<Book> =
            if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::fromJson) }
    }
}
