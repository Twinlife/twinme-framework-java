/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

/**
 * Twincode invocations used on the CallReceiver for the conference support.
 */
public class ConferenceProtocol {

    // The date when the conference event occurred.
    public static final String PARAM_DATE = "date";

    // The date when the conference was started (for the conference::stop event).
    public static final String PARAM_START = "startDate";

    public static final String ACTION_JOIN = "conference::join";
    public static final String ACTION_START = "conference::start";
    public static final String ACTION_STOP = "conference::stop";
}
