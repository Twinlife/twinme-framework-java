/*
 *  Copyright (c) 2019-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme;

import android.annotation.SuppressLint;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.Conversation;
import org.twinlife.twinlife.ConversationService.GroupConversation;
import org.twinlife.twinlife.ConversationService.Descriptor;
import org.twinlife.twinlife.ConversationService.UpdateType;
import org.twinlife.twinlife.TwincodeFactoryService;
import org.twinlife.twinme.executors.CreateEngineCardExecutor;
import org.twinlife.twinme.executors.CreateGroupEngineExecutor;
import org.twinlife.twinme.executors.CreateGroupProvisioningExecutor;
import org.twinlife.twinme.executors.CreateInvitationExecutor;
import org.twinlife.twinme.executors.CreateInvitationProvisioningExecutor;
import org.twinlife.twinme.executors.CreateSpaceCardExecutor;
import org.twinlife.twinme.executors.GetEngineCardExecutor;
import org.twinlife.twinme.executors.SetupEngineExecutor;
import org.twinlife.twinme.models.EngineCard;
import org.twinlife.twinme.models.EngineCard.ChannelInvitation;
import org.twinlife.twinme.models.EngineCard.GroupProvisioning;
import org.twinlife.twinme.models.EngineProvisioningDescriptor;
import org.twinlife.twinme.models.Group;
import org.twinlife.twinme.models.GroupProvisioningDescriptor;
import org.twinlife.twinme.models.InvitationProvisioningDescriptor;
import org.twinlife.twinme.models.Space;
import org.twinlife.twinme.TwinmeContext.Consumer;
import org.twinlife.twinme.models.SpaceCard;
import org.twinlife.twinme.models.SpaceCardProvisioningDescriptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

public class TwinmeEngineImpl extends TwinmeContext.DefaultObserver implements TwinmeEngine {
    private static final String LOG_TAG = "TwinmeEngineImpl";
    private static final boolean DEBUG = false;

    private class TwinmeContextObserver extends TwinmeContext.DefaultObserver {

        @Override
        public void onError(long requestId, ErrorCode errorCode, String errorParameter) {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContextObserver.onError: requestId=" + requestId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
            }

            Integer operationId;
            synchronized (mRequestIds) {
                operationId = mRequestIds.remove(requestId);
            }
            if (operationId != null) {
                TwinmeEngineImpl.this.onError(operationId, errorCode, errorParameter);
            }
        }

    }

    private class ConversationServiceObserver extends ConversationService.DefaultServiceObserver {

        @Override
        public void onPopDescriptor(long requestId, @NonNull Conversation conversation, @NonNull Descriptor descriptor) {
            if (DEBUG) {
                Log.d(LOG_TAG, "ConversationServiceObserver.onPopObject: requestId=" + requestId + " conversation=" + conversation + " descriptor=" + descriptor);
            }

            TwinmeEngineImpl.this.onPopDescriptor(conversation, descriptor);
        }

        @Override
        public void onUpdateDescriptor(long requestId, @NonNull Conversation conversation, @NonNull Descriptor descriptor, UpdateType updateType) {
            if (DEBUG) {
                Log.d(LOG_TAG, "ConversationServiceObserver.onUpdateDescriptor: requestId=" + requestId + " conversation=" + conversation + " descriptor=" + descriptor +
                        " updateType=" + updateType);
            }

            TwinmeEngineImpl.this.onUpdateDescriptor(conversation, descriptor, updateType);
        }

        @Override
        public void onCreateGroupConversation(long requestId, @NonNull GroupConversation conversation) {
            if (DEBUG) {
                Log.d(LOG_TAG, "ConversationServiceObserver.onCreateGroupConversation: requestId=" + requestId + " conversation=" + conversation);
            }

            TwinmeEngineImpl.this.onCreateGroupConversation(conversation);
        }

        @Override
        public void onDeleteGroupConversation(long requestId, @NonNull UUID conversationId, @NonNull UUID groupId) {
            if (DEBUG) {
                Log.d(LOG_TAG, "ConversationServiceObserver.onDeleteGroupConversation: requestId=" + requestId + " conversation=" + conversationId + " groupId=" + groupId);
            }

            TwinmeEngineImpl.this.onDeleteGroupConversation(conversationId);
        }

        @Override
        public void onLeaveGroup(long requestId, @NonNull GroupConversation conversation, @NonNull UUID memberId) {
            if (DEBUG) {
                Log.d(LOG_TAG, "ConversationServiceObserver.onLeaveGroup: conversation=" + conversation + " memberId=" + memberId);
            }

            TwinmeEngineImpl.this.onLeaveGroup(memberId);
        }

    }

    private final TwinmeApplication mTwinmeApplication;
    private final TwinmeContext mTwinmeContext;
    private final HashMap<UUID, UUID> mForwardConversations = new HashMap<>();

    @SuppressLint("UseSparseArrays")
    private final HashMap<Long, Integer> mRequestIds = new HashMap<>();

    private final TwinmeContextObserver mTwinmeContextObserver;
    private final ConversationServiceObserver mConversationServiceObserver;
    private final Executor mTwinlifeExecutor;

    public TwinmeEngineImpl(@NonNull TwinmeApplication twinmeApplication,
                            @NonNull TwinmeContext twinmeContext,
                            @NonNull Executor executor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "TwinmeEngineImpl: twinmeApplication=" + twinmeApplication + " twinmeContext=" + twinmeContext);
        }

        mTwinmeApplication = twinmeApplication;
        mTwinmeContext = twinmeContext;
        mTwinlifeExecutor = executor;

        mTwinmeContextObserver = new TwinmeContextObserver();
        mConversationServiceObserver = new ConversationServiceObserver();
    }

    @NonNull
    @Override
    public TwinmeApplication getApplication() {

        return mTwinmeApplication;
    }

    @Nullable
    @Override
    public TwinmeContext getTwinmeContext() {

        return mTwinmeContext;
    }

    @Override
    public void getEngineCard(long requestId, @NonNull UUID cardId, TwinmeContext.Consumer<EngineCard> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getEngineCard: requestId=" + requestId + " cardId=" + cardId);
        }

        GetEngineCardExecutor getEngineCardExecutor = new GetEngineCardExecutor((TwinmeContextImpl)mTwinmeContext, requestId, cardId, consumer);
        mTwinlifeExecutor.execute(getEngineCardExecutor::start);
    }

    @Override
    public void setupEngine(long requestId, @NonNull EngineCard engineCard, @NonNull Space space) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setupEngine: requestId=" + requestId + " engineCard=" + engineCard);
        }

        SetupEngineExecutor setupEngineExecutor = new SetupEngineExecutor(this, requestId, engineCard, space);
        mTwinlifeExecutor.execute(setupEngineExecutor::start);
    }

    public void createGroup(long requestId, @NonNull Space space, @NonNull GroupProvisioning groupProvisioning) {

        CreateGroupEngineExecutor createGroupExecutor = new CreateGroupEngineExecutor((TwinmeContextImpl)mTwinmeContext, requestId, space, groupProvisioning);
        mTwinlifeExecutor.execute(createGroupExecutor::start);
    }

    public void createInvitation(long requestId, @NonNull Space space, @NonNull Group group, long permissions,
                                 @NonNull UUID invitationTwincodeInboundId, @NonNull UUID invitationTwincodeOutboundId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createInvitation: requestId=" + requestId + " invitationTwincodeInboundId=" + invitationTwincodeInboundId
            + " invitationTwincodeOutboundId=" + invitationTwincodeOutboundId + " group=" + group + " permissions=" + permissions);
        }

        CreateInvitationExecutor createInvitationExecutor = new CreateInvitationExecutor((TwinmeContextImpl)mTwinmeContext, requestId,
                space, group, permissions, invitationTwincodeInboundId, invitationTwincodeOutboundId);
        mTwinlifeExecutor.execute(createInvitationExecutor::start);
    }

    public void createTwincode(long requestId,
                               @NonNull List<BaseService.AttributeNameValue> twincodeFactoryAttributes,
                               @Nullable List<BaseService.AttributeNameValue> twincodeInboundAttributes,
                               @Nullable List<BaseService.AttributeNameValue> twincodeOutboundAttributes,
                               @Nullable List<BaseService.AttributeNameValue> twincodeSwitchAttributes,
                               @NonNull org.twinlife.twinlife.Consumer<TwincodeFactoryService.TwincodeFactory> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createTwincode: requestId=" + requestId + " twincodeFactoryAttributes=" + twincodeFactoryAttributes +
                    " twincodeInboundAttributes=" + twincodeInboundAttributes + " twincodeOutboundAttributes=" + twincodeOutboundAttributes +
                    " twincodeSwitchAttributes=" + twincodeSwitchAttributes);
        }

        mTwinmeContext.getTwincodeFactoryService().createTwincode(requestId,
                twincodeFactoryAttributes, twincodeInboundAttributes,
                twincodeOutboundAttributes, twincodeSwitchAttributes, complete);
    }

    public void createGroupProvisionning(long requestId, @NonNull GroupProvisioningDescriptor group,
                                         @NonNull Consumer<GroupProvisioning> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createGroupProvisionning: requestId=" + requestId + " group=" + group);
        }

        CreateGroupProvisioningExecutor createGroupProvisioningExecutor
                = new CreateGroupProvisioningExecutor((TwinmeContextImpl) mTwinmeContext, requestId, this,
                group, consumer);

        mTwinlifeExecutor.execute(createGroupProvisioningExecutor::start);
    }

    @Override
    public void createInvitationProvisionning(long requestId, @NonNull UUID groupTwincodeId,
                                              @NonNull GroupProvisioningDescriptor group,
                                              @NonNull InvitationProvisioningDescriptor invitation,
                                              @NonNull Consumer<ChannelInvitation> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createInvitationProvisionning: requestId=" + requestId + " groupTwincodeOutId=" + groupTwincodeId +
                    " group=" + group);
        }

        CreateInvitationProvisioningExecutor createGroupProvisioningExecutor
                = new CreateInvitationProvisioningExecutor((TwinmeContextImpl) mTwinmeContext, requestId, this,
                groupTwincodeId, group, invitation, consumer);

        mTwinlifeExecutor.execute(createGroupProvisioningExecutor::start);
    }

    @Override
    public void createEngineCard(long requestId, @NonNull EngineProvisioningDescriptor engineDescriptor,
                                 @NonNull Consumer<EngineCard> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createEngineCard: requestId=" + requestId + " engineDescriptor=" + engineDescriptor +
                    " consumer=" + consumer);
        }

        CreateEngineCardExecutor createEngineCardExecutor
                = new CreateEngineCardExecutor((TwinmeContextImpl)mTwinmeContext, requestId, this, engineDescriptor, consumer);

        mTwinlifeExecutor.execute(createEngineCardExecutor::start);
    }

    @Override
    public void createSpaceCardProvisionning(long requestId, @NonNull EngineCard engineCard,
                                             @NonNull Map<GroupProvisioningDescriptor, UUID> groupTwincodes,
                                             @NonNull SpaceCardProvisioningDescriptor spaceCardProvisioningDescriptor,
                                             @NonNull Consumer<SpaceCard> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createEngineCard: requestId=" + requestId + " engineCard=" + engineCard +
                    " consumer=" + consumer);
        }

        CreateSpaceCardExecutor createSpaceCardExecutor
                = new CreateSpaceCardExecutor((TwinmeContextImpl)mTwinmeContext, requestId, this,
                engineCard, groupTwincodes, spaceCardProvisioningDescriptor, consumer);

        mTwinlifeExecutor.execute(createSpaceCardExecutor::start);
    }

    public void addForward(@NonNull UUID contactId, @NonNull UUID targetConversationId) {

        Log.i(LOG_TAG, "Forward " + contactId + " on " + targetConversationId);
        mForwardConversations.put(contactId, targetConversationId);
    }

    //
    // Protected Methods
    //

    @Override
    public final void onTwinlifeReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeReady");
        }

        super.onTwinlifeReady();

        mTwinmeContext.setObserver(mTwinmeContextObserver);
        mTwinmeContext.getConversationService().addServiceObserver(mConversationServiceObserver);
    }

    @Override
    public final void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        super.onTwinlifeOnline();
    }

    private void removeConversation(@NonNull UUID conversationId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "removeConversation: twincodeOutboundId=" + conversationId);
        }

        synchronized (mForwardConversations) {
            mForwardConversations.remove(conversationId);
        }
    }

    private void onLeaveGroup(@NonNull UUID memberId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onLeaveGroup: memberId=" + memberId);
        }

        // Remove the member's twincode from our local database.
    }

    private void onCreateGroupConversation(@NonNull GroupConversation conversation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateGroupConversation: conversation=" + conversation);
        }

    }

    private void onDeleteGroupConversation(@NonNull UUID conversationId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteGroupConversation: conversationId=" + conversationId);
        }

        removeConversation(conversationId);
    }

    private void onPopDescriptor(@NonNull Conversation conversation, @NonNull Descriptor descriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onPopDescriptor: conversation=" + conversation + " descriptor=" + descriptor);
        }

        if (descriptor.getType() != Descriptor.Type.OBJECT_DESCRIPTOR) {

            return;
        }
        UUID forwardConversationId = mForwardConversations.get(conversation.getContactId());
        if (forwardConversationId != null) {
            long requestId = BaseService.DEFAULT_REQUEST_ID;

            mTwinmeContext.getConversationService().forwardDescriptor(requestId, forwardConversationId, descriptor.getDescriptorId());
        }
    }

    private void onUpdateDescriptor(@NonNull Conversation conversation, @NonNull Descriptor descriptor, UpdateType updateType) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateDescriptor: conversation=" + conversation + " descriptor=" + descriptor + " updateType=" + updateType);
        }

        if (updateType != UpdateType.CONTENT) {

            return;
        }

        UUID forwardConversationId = mForwardConversations.get(conversation.getId());
        if (forwardConversationId != null) {
            long requestId = BaseService.DEFAULT_REQUEST_ID;

            mTwinmeContext.getConversationService().forwardDescriptor(requestId, forwardConversationId, descriptor.getDescriptorId());
        }
    }

    private void onError(int operationId, ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: operationId=" + operationId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

    }
}
