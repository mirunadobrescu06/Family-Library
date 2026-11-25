# Family Library

An Android app that lets my family (4 phones) browse and manage our shared book collection,
which is spread over two houses ("44" and "71"). The collection lives in a Google Sheet, so it
can still be viewed, edited and exported as Excel, and the app keeps every phone in sync with it.

## Features
- Name-only sign-in (no passwords; it's a family app)
- Search across title, author, year, notes and custom columns (diacritic-insensitive: `calinescu` finds *Călinescu*)
- Filter by location and by who added a book; 10 sort orders, with a "recent" default (recently opened or added first)
- Add, modify, duplicate and delete books; optimistic UI with rollback if the save fails
- Import `.xlsx` / `.csv` files: columns matched by name (English or Romanian), unknown columns added to the sheet,
  duplicates skipped, rows with a known ID update existing books (Excel round-trip)
- Live sync: polls a cheap change stamp every 15 s and reloads only when something changed (from any phone or the sheet itself)
- Offline cache for instant start-up

## Architecture
```
Jetpack Compose UI ── LibraryViewModel (StateFlow) ── SheetApi (HttpURLConnection + JSON)
                                    │                         │
                               LocalStore                Google Apps Script Web App (Code.gs)
                     (name, last-opened, cache)               │
                                                         Google Sheet (master copy)
```
- **Android:** Kotlin, Jetpack Compose (Material 3), coroutines, no third-party libraries.
  The `.xlsx` reader is a small zip + SAX parser written from scratch instead of Apache POI, which would add many megabytes to the app.
- **Backend:** Google Apps Script on top of the Sheet: a JSON API (list/add/update/delete/import),
  script lock for concurrent writes, `onEdit`/`onChange` triggers that stamp manual edits so the phones notice them.

See [SETUP.md](SETUP.md) to run it.
