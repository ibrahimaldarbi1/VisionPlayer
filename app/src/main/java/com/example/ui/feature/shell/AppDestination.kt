package com.example.ui.feature.shell

enum class AppDestination {
    HOME,
    LIVE,
    MOVIES,
    SERIES,
    EPG,
    SEARCH,
    SETTINGS;

    companion object {
        fun fromKey(key: String?): AppDestination? {
            return entries.find { it.name == key }
        }
    }
}
