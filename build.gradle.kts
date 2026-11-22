/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
}

apply(from = rootProject.file("gradle/build-identity.gradle.kts"))

extra["compileSdkVersion"] = 37
extra["targetSdkVersion"] = 37

val v5ProviderModules = listOf(
    ":player-neriplayer"
)

val releaseSigningEnvironment = listOf(
    "RELEASE_STORE_FILE",
    "RELEASE_STORE_PASSWORD",
    "RELEASE_KEY_ALIAS",
    "RELEASE_KEY_PASSWORD"
)
val releaseArtifactTaskRequested = gradle.startParameter.taskNames.any { requestedTask ->
    requestedTask.substringAfterLast(':').lowercase() in setOf(
        "assemblev5matrixrelease",
        "assemblerelease",
        "bundlerelease",
        "packagerelease",
        "installrelease",
        "build"
    )
}
if (releaseArtifactTaskRequested) {
    val missingSigningEnvironment = releaseSigningEnvironment.filter { name ->
        System.getenv(name).isNullOrBlank()
    }
    check(missingSigningEnvironment.isEmpty()) {
        "Provider release signing is required; missing: ${missingSigningEnvironment.joinToString()}."
    }
}

tasks.register("assembleV5MatrixDebug") {
    group = "build"
    description = "Build the v5 Provider debug APK."
    dependsOn(v5ProviderModules.map { "$it:assembleDebug" })
}

tasks.register("assembleV5MatrixRelease") {
    group = "build"
    description = "Build the v5 Provider release APK."
    dependsOn(v5ProviderModules.map { "$it:assembleRelease" })
}

tasks.register("testV5Matrix") {
    group = "verification"
    description = "Run core, parser, and v5 Provider unit tests."
    dependsOn(v5ProviderModules.map { "$it:testDebugUnitTest" })
    dependsOn(
        ":provider-core:testDebugUnitTest",
        ":provider-runtime-diagnostics:testDebugUnitTest",
        ":provider-hook-api102:testDebugUnitTest",
        ":provider-settings-api102:testDebugUnitTest",
        ":provider-module-status:testDebugUnitTest",
        ":provider-scope-request:testDebugUnitTest",
        ":reflection-core:testDebugUnitTest",
        ":parser-lrc:test",
        ":parser-ttml:test"
    )
}
