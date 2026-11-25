package com.example.library

object Config {
    const val SHEET_URL = "https://script.google.com/macros/s/AKfycbz1R3VX8QN-xTtTWHdYn-obWL7PVtfn7oArRIDXJ099kT6AdMan1WuuBoWp9LUp11yT/exec"
    const val SHEET_TOKEN = "sIiNCldrubvUZr72Mc-7au_-UYrTsNgv"

    val isConfigured: Boolean
        get() = SHEET_URL.startsWith("https://")

    const val POLL_INTERVAL_MS = 15_000L

    const val FULL_REFRESH_MS = 5 * 60_000L
}
