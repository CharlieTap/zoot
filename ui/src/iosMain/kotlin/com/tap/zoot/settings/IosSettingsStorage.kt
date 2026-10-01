package com.tap.zoot.settings

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import platform.Foundation.NSUserDefaults

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosSettingsStorage : SettingsStorage {
    private val preferences = NSUserDefaults.standardUserDefaults

    override fun read(key: String): String? = preferences.stringForKey(key) ?: preferences.objectForKey(key)?.toString()

    override fun write(values: Map<String, String>) {
        values.forEach { (key, value) -> preferences.setObject(value, key) }
    }
}
