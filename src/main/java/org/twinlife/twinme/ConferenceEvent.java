/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme;

public enum ConferenceEvent {
    // A first participant is waiting for the conference to start
    FIRST_JOIN,

    // The conference has started (at least 2 participants joined).
    START,

    // The conference is finished (every participant left the conference).
    STOP
}