package com.tap.zoot.settings

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidSettingsStorage(
    application: Application,
) : SettingsStorage {
    private val preferences = application.getSharedPreferences("game_settings", 0)

    override fun read(key: String): String? = preferences.all[key]?.toString()

    override fun write(values: Map<String, String>) {
        preferences
            .edit()
            .also { editor -> values.forEach { (key, value) -> editor.putString(key, value) } }
            .apply()
    }
}
