package com.example.library.data

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

enum class SortOption(val label: String) {
    RECENT("Recent (opened & added)"),
    LAST_ADDED("Last added"),
    FIRST_ADDED("First added"),
    LAST_MODIFIED("Last modified"),
    LAST_OPENED("Last opened by me"),
    TITLE("Title A–Z"),
    AUTHOR("Author A–Z"),
    YEAR_NEWEST("Year: newest first"),
    YEAR_OLDEST("Year: oldest first"),
    LOCATION("Location"),
}

val SYSTEM_NAMES = setOf("", "sheet", "someone")

object BookQuery {
    private val collator: Collator = Collator.getInstance(Locale.forLanguageTag("ro-RO")).apply { strength = Collator.PRIMARY }
    private val marks = Regex("\\p{Mn}+")

    fun fold(s: String): String =
        marks.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)

    fun matches(book: Book, query: String): Boolean {
        val words = fold(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return true
        val haystack = fold(
            listOf(book.title, book.author, book.year, book.location, book.comments)
                .plus(book.extra.values).joinToString(" ")
        )
        return words.all { haystack.contains(it) }
    }

    fun apply(
        books: List<Book>,
        query: String,
        location: String?,
        addedBy: String?,
        sort: SortOption,
        lastOpened: Map<String, Long>,
    ): List<Book> {
        val filtered = books.filter { b ->
            (location == null || b.location.trim() == location) &&
                (addedBy == null || b.addedBy.equals(addedBy, ignoreCase = true)) &&
                matches(b, query)
        }
        return sort(filtered, sort, lastOpened)
    }

    fun sort(books: List<Book>, sort: SortOption, lastOpened: Map<String, Long>): List<Book> {
        val byTitle = Comparator<Book> { a, b -> collator.compare(a.title, b.title) }
        return when (sort) {
            SortOption.RECENT -> books.sortedWith(
                compareByDescending<Book> { maxOf(lastOpened[it.id] ?: 0L, it.added) }.then(byTitle)
            )
            SortOption.LAST_ADDED -> books.sortedWith(compareByDescending<Book> { it.added }.then(byTitle))
            SortOption.FIRST_ADDED -> books.sortedWith(compareBy<Book> { it.added }.then(byTitle))
            SortOption.LAST_MODIFIED -> books.sortedWith(compareByDescending<Book> { it.modified }.then(byTitle))
            SortOption.LAST_OPENED -> books.sortedWith(compareByDescending<Book> { lastOpened[it.id] ?: 0L }.then(byTitle))
            SortOption.TITLE -> books.sortedWith(byTitle)
            SortOption.AUTHOR -> books.sortedWith(
                Comparator<Book> { a, b -> blankLast(a.author, b.author) ?: collator.compare(a.author, b.author) }.then(byTitle)
            )
            SortOption.YEAR_NEWEST -> books.sortedWith(
                compareBy<Book> { yearOf(it) == null }.thenByDescending { yearOf(it) ?: 0 }.then(byTitle)
            )
            SortOption.YEAR_OLDEST -> books.sortedWith(
                compareBy<Book> { yearOf(it) == null }.thenBy { yearOf(it) ?: 0 }.then(byTitle)
            )
            SortOption.LOCATION -> books.sortedWith(
                Comparator<Book> { a, b -> blankLast(a.location, b.location) ?: collator.compare(a.location, b.location) }.then(byTitle)
            )
        }
    }

    fun familyNames(books: List<Book>): List<String> =
        books.flatMap { listOf(it.addedBy, it.modifiedBy) }
            .map { it.trim() }
            .filter { it.lowercase(Locale.ROOT) !in SYSTEM_NAMES }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .sortedWith(collator)

    fun yearOf(b: Book): Int? = Regex("\\d{3,4}").find(b.year)?.value?.toIntOrNull()

    private fun blankLast(a: String, b: String): Int? = when {
        a.isBlank() && b.isNotBlank() -> 1
        b.isBlank() && a.isNotBlank() -> -1
        else -> null
    }
}
