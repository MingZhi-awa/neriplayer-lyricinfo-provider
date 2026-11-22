/*
 * Copyright 2026 猫羽bot
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.neriplayer

import io.github.andrealtb.coloroslyrics.provider.hook102.ProviderProcessPolicy

/**
 * NeriPlayer publishes its playback MediaSession from the main process through
 * `moe.ouom.neriplayer.core.player.service.AudioPlayerService`; the manifest declares no
 * `android:process` on that service. Auxiliary processes (`:netease_login`, `:bili_web_login`,
 * `:youtube_web_login`, Shizuku user service) never own the notification MediaSession, so they
 * detach before any business hook.
 */
object NeriProcessPolicy {
    fun shouldHook(packageName: String, processName: String): Boolean {
        if (packageName !in NeriPlayerConstants.HOST_PACKAGES) return false
        return processName == packageName
    }

    fun describe(packageName: String, processName: String): String =
        "neriplayer-main-process package=$packageName process=$processName"
}

internal object NeriServiceProcessPolicy : ProviderProcessPolicy {

    override val packages: Set<String> = NeriPlayerConstants.HOST_PACKAGES

    override fun accepts(packageName: String, processName: String): Boolean =
        NeriProcessPolicy.shouldHook(packageName, processName)

    override fun describe(packageName: String, processName: String): String =
        NeriProcessPolicy.describe(packageName, processName)
}
