package com.tap.zoot.runtime.diagnostics

import com.tap.crashreporting.CrashReportingConfig
import com.tap.zoot.runtime.config.ZootBuildConfig
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@BindingContainer
@ContributesTo(AppScope::class)
object CrashReportingBindings {
    @Provides
    fun crashReportingConfig(): CrashReportingConfig =
        CrashReportingConfig(
            applicationName = ZootBuildConfig.APPLICATION_NAME,
            githubRepositoryUrl = ZootBuildConfig.GITHUB_REPOSITORY_URL,
        )
}
