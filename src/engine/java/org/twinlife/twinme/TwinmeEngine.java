/*
 *  Copyright (c) 2019 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinme.TwinmeContext.Consumer;
import org.twinlife.twinme.models.EngineCard;
import org.twinlife.twinme.models.EngineCard.ChannelInvitation;
import org.twinlife.twinme.models.EngineCard.GroupProvisioning;
import org.twinlife.twinme.models.EngineProvisioningDescriptor;
import org.twinlife.twinme.models.GroupProvisioningDescriptor;
import org.twinlife.twinme.models.InvitationProvisioningDescriptor;
import org.twinlife.twinme.models.Space;
import org.twinlife.twinme.models.SpaceCard;
import org.twinlife.twinme.models.SpaceCardProvisioningDescriptor;

import java.util.Map;
import java.util.UUID;

public interface TwinmeEngine {

    @NonNull
    TwinmeApplication getApplication();

    @Nullable
    TwinmeContext getTwinmeContext();

    void createEngineCard(long requestId, @NonNull EngineProvisioningDescriptor groups,
                          @NonNull Consumer<EngineCard> consumer);

    void createGroupProvisionning(long requestId, @NonNull GroupProvisioningDescriptor group,
                                  @NonNull Consumer<GroupProvisioning> consumer);

    void createInvitationProvisionning(long requestId, @NonNull UUID groupTwincodeId,
                                       @NonNull GroupProvisioningDescriptor group,
                                       @NonNull InvitationProvisioningDescriptor invitation,
                                       @NonNull Consumer<ChannelInvitation> consumer);

    void createSpaceCardProvisionning(long requestId, @NonNull EngineCard engineCard,
                                      @NonNull Map<GroupProvisioningDescriptor, UUID> groupTwincodes,
                                      @NonNull SpaceCardProvisioningDescriptor spaceCardProvisioningDescriptor,
                                      @NonNull Consumer<SpaceCard> consumer);

    void createGroup(long requestId, @NonNull Space space, @NonNull GroupProvisioning groupProvisioning);

    void getEngineCard(long requestId, @NonNull UUID cardId, TwinmeContext.Consumer<EngineCard> consumer);

    void setupEngine(long requestId, @NonNull EngineCard engineCard, @NonNull Space space);
}
