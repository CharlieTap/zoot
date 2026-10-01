package com.tap.zoot.settings

interface SettingsStorage {
    fun read(key: String): String?

    fun write(values: Map<String, String>)
}
