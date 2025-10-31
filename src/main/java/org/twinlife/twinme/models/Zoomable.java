/*
 *  Copyright (c) 2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

public enum Zoomable {
    // Control of the camera by the peer is never allowed (we must not ask).
    NEVER,

    // Control of the camera is allowed after a request and confirmation process.
    ASK,

    // Control of the camera is always allowed (no need to request).
    ALLOW
}