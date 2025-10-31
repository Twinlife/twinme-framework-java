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

import java.util.List;

/**
 * Engine card representation.
 */
public class EngineProvisioningDescriptor {

    public String spaceName;

    public Bitmap spaceAvatar;

    public List<GroupProvisioningDescriptor> groups;

    public List<SpaceCardProvisioningDescriptor> spaceCards;
}
