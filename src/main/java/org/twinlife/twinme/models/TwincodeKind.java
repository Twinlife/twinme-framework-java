/*
 *  Copyright (c) 2021 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

public enum TwincodeKind {
    // The twincode is an invitation
    INVITATION,

    // The twincode is a simple contact relation.
    CONTACT,

    // The twincode is a group twincode.
    GROUP,

    // The twincode is a group member.
    GROUP_MEMBER,

    // The twincode is a twinroom with specific capabilities.
    TWINROOM,

    // The twincode is used for account migration.
    ACCOUNT_MIGRATION,

    // The twincode describes a managed space.
    SPACE,
    // The twincode describes a call receiver.
    CALL_RECEIVER;

    /**
     * Serializable value. e.g. CALL_RECEIVER => call_receiver
     */
    public final String value = name().toLowerCase().replace('_','-');

    static TwincodeKind getByValue(String value){
        for(TwincodeKind kind: TwincodeKind.values()){
            if(kind.value.equals(value)){
                return kind;
            }
        }

        return null;
    }
}