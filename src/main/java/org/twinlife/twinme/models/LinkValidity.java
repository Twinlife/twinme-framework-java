/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

public enum LinkValidity {
    // The conference link is always valid.
    PERMANENT,

    // The conference link is intended to be used only once.
    SINGLE_USE,

    // The conference link is periodic.
    PERIODIC
}