/*
 *  Copyright (c) 2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Fabrice Trescartes (Fabrice.Trescartes@twin.life)
 */

package org.twinlife.twinme.calls;

import androidx.annotation.Nullable;

/**
 * Normal incoming flows:
 *
 * INCOMING_CALL -> ACCEPTED_INCOMING_CALL -> IN_CALL -> TERMINATED
 * INCOMING_VIDEO_CALL -> ACCEPTED_INCOMING_VIDEO_CALL -> IN_VIDEO_CALL -> TERMINATED
 * INCOMING_VIDEO_BELL -> IN_VIDEO_BELL -> ACCEPTED_INCOMING_VIDEO_CALL -> IN_VIDEO_CALL -> TERMINATED
 *
 * Outgoing flows:
 *
 * OUTGOING_CALL -> ACCEPTED_OUTGOING_CALL -> IN_CALL -> TERMINATED
 * OUTGOING_VIDEO_CALL -> ACCEPTED_OUTGOING_VIDEO_CALL -> IN_VIDEO_CALL -> TERMINATED
 * OUTGOING_VIDEO_BELL -> IN_VIDEO_BELL -> IN_VIDEO_CALL -> TERMINATED
 *
 */
public enum CallStatus {
    INCOMING_CALL,
    INCOMING_VIDEO_CALL,
    INCOMING_VIDEO_BELL,     // Incoming video bell and we are receiving the video track.
    ACCEPTED_INCOMING_CALL,
    ACCEPTED_INCOMING_VIDEO_CALL,
    OUTGOING_CALL,
    OUTGOING_VIDEO_CALL,
    OUTGOING_VIDEO_BELL,
    ACCEPTED_OUTGOING_CALL,
    ACCEPTED_OUTGOING_VIDEO_CALL,
    IN_VIDEO_BELL,           // Outgoing video bell and we have setup the local video track.
    IN_CALL,
    IN_VIDEO_CALL,
    PEER_ON_HOLD,           // The peer has put the call on hold
    ON_HOLD,                // We have put the call on hold
    FALLBACK,
    TERMINATED;

    CallStatus toActive() {

        switch (this) {
            case INCOMING_CALL:
            case ACCEPTED_INCOMING_CALL:
            case OUTGOING_CALL:
            case ACCEPTED_OUTGOING_CALL:
                return IN_CALL;

            case INCOMING_VIDEO_BELL:
            case INCOMING_VIDEO_CALL:
            case OUTGOING_VIDEO_BELL:
            case OUTGOING_VIDEO_CALL:
            case IN_VIDEO_BELL:
            case ACCEPTED_INCOMING_VIDEO_CALL:
            case ACCEPTED_OUTGOING_VIDEO_CALL:
                return IN_VIDEO_CALL;

            default:
                return this;
        }
    }

    CallStatus toAccepted() {

        switch (this) {
            case INCOMING_CALL:
            case ACCEPTED_INCOMING_CALL:
                return ACCEPTED_INCOMING_CALL;

            case OUTGOING_CALL:
            case ACCEPTED_OUTGOING_CALL:
                return ACCEPTED_OUTGOING_CALL;

            case INCOMING_VIDEO_BELL:
            case INCOMING_VIDEO_CALL:
            case ACCEPTED_INCOMING_VIDEO_CALL:
                return ACCEPTED_INCOMING_VIDEO_CALL;

            case OUTGOING_VIDEO_BELL:
            case OUTGOING_VIDEO_CALL:
            case ACCEPTED_OUTGOING_VIDEO_CALL:
                return ACCEPTED_OUTGOING_VIDEO_CALL;

            default:
                return this;
        }
    }

    CallStatus toVideo() {

        switch (this) {
            case INCOMING_CALL:
            case INCOMING_VIDEO_CALL:
                return INCOMING_VIDEO_CALL;

            case ACCEPTED_INCOMING_CALL:
            case ACCEPTED_INCOMING_VIDEO_CALL:
                return ACCEPTED_INCOMING_VIDEO_CALL;

            case OUTGOING_CALL:
            case OUTGOING_VIDEO_CALL:
                return OUTGOING_VIDEO_CALL;

            case INCOMING_VIDEO_BELL:
                return INCOMING_VIDEO_BELL;

            case OUTGOING_VIDEO_BELL:
                return OUTGOING_VIDEO_BELL;

            case IN_VIDEO_BELL:
                return IN_VIDEO_BELL;

            case IN_CALL:
            case IN_VIDEO_CALL:
                return IN_VIDEO_CALL;

            default:
                return this;
        }
    }

    public static boolean isIncoming(@Nullable CallStatus mode) {

        return mode == INCOMING_CALL || mode == INCOMING_VIDEO_CALL || mode == INCOMING_VIDEO_BELL;
    }

    public static boolean isOutgoing(@Nullable CallStatus mode) {

        return mode == OUTGOING_CALL || mode == OUTGOING_VIDEO_CALL || mode == OUTGOING_VIDEO_BELL;
    }

    public static boolean isActive(@Nullable CallStatus mode) {

        return mode == IN_CALL || mode == IN_VIDEO_CALL;
    }

    public static boolean isAccepted(@Nullable CallStatus mode) {

        return mode == ACCEPTED_INCOMING_CALL || mode == ACCEPTED_INCOMING_VIDEO_CALL || mode == ACCEPTED_OUTGOING_CALL || mode == ACCEPTED_OUTGOING_VIDEO_CALL;
    }

    public static boolean isTerminated(@Nullable CallStatus mode) {

        return mode == TERMINATED;
    }

    public boolean isVideo() {

        switch (this) {
            case INCOMING_VIDEO_CALL:
            case INCOMING_VIDEO_BELL:
            case OUTGOING_VIDEO_BELL:
            case OUTGOING_VIDEO_CALL:
            case ACCEPTED_OUTGOING_VIDEO_CALL:
            case ACCEPTED_INCOMING_VIDEO_CALL:
            case IN_VIDEO_BELL:
            case IN_VIDEO_CALL:
                return true;

            default:
                return false;
        }
    }

    public boolean isOnHold() {
        return this == ON_HOLD || this == PEER_ON_HOLD;
    }
}
