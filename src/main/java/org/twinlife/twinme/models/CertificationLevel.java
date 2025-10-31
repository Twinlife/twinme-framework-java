/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.twinme.models;

public enum CertificationLevel {
    LEVEL_0, // Relation does not use public and private keys
    LEVEL_1, // Relation uses public and private keys, no side is trusted
    LEVEL_2, // Relation uses public/private keys, the peer trust our public key and identity twincode.
    LEVEL_3, // Relation uses public/private keys, we trust the peer public key and identity twincode.
    LEVEL_4  // Relation uses public/private keys, both side trust each other's public key and twincode.
}
