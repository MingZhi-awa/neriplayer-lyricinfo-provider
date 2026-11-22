/*
 * Copyright 2026 猫羽bot
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.neriplayer

import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.DiagnosticEvent
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.StructuredDiagnostics

/**
 * Every structured event of this Provider goes through here, so the component string and the
 * event vocabulary stay identical across areas. Lyrics, stable media IDs, cookie and URL query are
 * never logged: only counts, generation numbers and hashed identities.
 */
internal object NeriDiagnostics {

    const val COMPONENT = "provider/neriplayer"

    fun info(
        area: String,
        event: String,
        generation: Long? = null,
        trackHash: String? = null,
        payloadChars: Int? = null,
        parcelBytes: Int? = null,
        reason: String? = null,
        message: String? = null
    ) = StructuredDiagnostics.logInfo(
        DiagnosticEvent(
            component = COMPONENT,
            area = area,
            event = event,
            generation = generation,
            trackHash = trackHash,
            payloadChars = payloadChars,
            parcelBytes = parcelBytes,
            reason = reason,
            message = message
        )
    )

    fun debug(
        area: String,
        event: String,
        generation: Long? = null,
        reason: String? = null,
        message: String? = null
    ) = StructuredDiagnostics.logDebug(
        DiagnosticEvent(
            component = COMPONENT,
            area = area,
            event = event,
            generation = generation,
            reason = reason,
            message = message
        )
    )

    fun warning(area: String, event: String, reason: String? = null) =
        StructuredDiagnostics.logWarning(
            DiagnosticEvent(component = COMPONENT, area = area, event = event, reason = reason)
        )

    fun failure(area: String, event: String, error: Throwable) = StructuredDiagnostics.logError(
        DiagnosticEvent(
            component = COMPONENT,
            area = area,
            event = event,
            reason = error.javaClass.simpleName,
            message = error.message?.take(500)
        ),
        throwable = error
    )
}
