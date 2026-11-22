/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        google()
        gradlePluginPortal()
        maven { url = uri("https://api.xposed.info/") }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        google()
        maven { url = uri("https://api.xposed.info/") }
    }
}

rootProject.name = "ColorOS-Live-Lyrics-Provider-NeriPlayer"

include(":provider-core")
include(":provider-runtime-diagnostics")
include(":provider-hook-api102")
include(":provider-settings-api102")
include(":provider-module-status")
include(":provider-scope-request")
include(":reflection-core")
include(":parser-lrc")
include(":parser-ttml")
include(":player-neriplayer")
