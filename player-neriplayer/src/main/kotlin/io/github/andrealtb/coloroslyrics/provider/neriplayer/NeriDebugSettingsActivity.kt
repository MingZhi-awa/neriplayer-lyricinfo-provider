/*
 * Copyright 2026 猫羽bot
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.neriplayer

import io.github.andrealtb.coloroslyrics.provider.core.config.ProviderId
import io.github.andrealtb.coloroslyrics.provider.settings102.ProviderDebugSettingsActivity

class NeriDebugSettingsActivity : ProviderDebugSettingsActivity() {
    override val providerId: ProviderId = ProviderId.NERIPLAYER
    override val providerDisplayName: String = "音理音理歌词 Provider"
    override val targetPackageDescription: String = "目标播放器：moe.ouom.neriplayer"
}
