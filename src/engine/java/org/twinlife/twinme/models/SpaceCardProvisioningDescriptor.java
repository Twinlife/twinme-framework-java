/*
 *  Copyright (c) 2019 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

import android.graphics.Bitmap;

import java.util.Map;

/**
 * SpaceCard provisionning
 */
public class SpaceCardProvisioningDescriptor {

    public String name;

    public String description;

    public Bitmap avatar;

    public String permissions;

    public Map<GroupProvisioningDescriptor, InvitationProvisioningDescriptor> invitations;
}
