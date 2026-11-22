/*
 * Copyright 2026 猫羽bot
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.neriplayer

import io.github.andrealtb.coloroslyrics.provider.core.config.ProviderId
import io.github.andrealtb.coloroslyrics.provider.hook102.ProviderHookInstaller
import io.github.andrealtb.coloroslyrics.provider.hook102.ProviderModuleEntry
import io.github.andrealtb.coloroslyrics.provider.hook102.ProviderProcessPolicy

/** Single libxposed API 102 entry for NeriPlayer (音理音理). */
class NeriModuleEntry : ProviderModuleEntry() {

    override val providerId: ProviderId = ProviderId.NERIPLAYER

    override val diagnosticsComponent: String = "provider/neriplayer"

    override val processPolicy: ProviderProcessPolicy = NeriServiceProcessPolicy

    override fun createHookInstaller(): ProviderHookInstaller =
        ProviderHookInstaller { context -> NeriPlayerHooker(context).onHook() }
}
