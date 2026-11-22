/*
 * Copyright 2026 猫羽bot
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.neriplayer

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NeriProcessPolicyTest {

    @Test
    fun `main process of both host variants is accepted`() {
        assertTrue(NeriProcessPolicy.shouldHook("moe.ouom.neriplayer", "moe.ouom.neriplayer"))
        assertTrue(NeriProcessPolicy.shouldHook("moe.ouom.neriplayer.debug", "moe.ouom.neriplayer.debug"))
    }

    @Test
    fun `auxiliary processes are rejected`() {
        val host = "moe.ouom.neriplayer"
        assertFalse(NeriProcessPolicy.shouldHook(host, "$host:netease_login"))
        assertFalse(NeriProcessPolicy.shouldHook(host, "$host:bili_web_login"))
        assertFalse(NeriProcessPolicy.shouldHook(host, "$host:youtube_web_login"))
        assertFalse(NeriProcessPolicy.shouldHook(host, "$host:shizuku"))
    }

    @Test
    fun `other packages are rejected`() {
        assertFalse(NeriProcessPolicy.shouldHook("com.salt.music", "com.salt.music"))
        assertFalse(NeriProcessPolicy.shouldHook("moe.ouom.neriplayer.benchmark", "moe.ouom.neriplayer.benchmark"))
    }
}
