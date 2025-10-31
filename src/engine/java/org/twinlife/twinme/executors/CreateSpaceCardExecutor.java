/*
 *  Copyright (c) 2019-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.executors;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinlife.TwincodeFactoryService.TwincodeFactory;
import org.twinlife.twinme.TwinmeContext;
import org.twinlife.twinme.TwinmeContext.Consumer;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.TwinmeEngineImpl;
import org.twinlife.twinme.models.EngineCard;
import org.twinlife.twinme.models.EngineCard.ChannelInvitation;
import org.twinlife.twinme.models.GroupProvisioningDescriptor;
import org.twinlife.twinme.models.InvitationProvisioningDescriptor;
import org.twinlife.twinme.models.PairProtocol;
import org.twinlife.twinme.models.SpaceCard;
import org.twinlife.twinme.models.SpaceCardProvisioningDescriptor;
import org.twinlife.twinme.util.TwinmeAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.1
//

public class CreateSpaceCardExecutor {
    private static final String LOG_TAG = "CreateSpaceCardExecutor";
    private static final boolean DEBUG = true;

    private static final int CREATE_SPACE_TWINCODE = 1;
    private static final int CREATE_SPACE_TWINCODE_DONE = 1 << 1;
    private static final int CREATE_INVITATION = 1 << 2;
    private static final int CREATE_INVITATION_DONE = 1 << 3;
    private static final int CREATE_OBJECT = 1 << 4;
    private static final int CREATE_OBJECT_DONE = 1 << 5;

    private class TwinmeContextObserver extends TwinmeContext.DefaultObserver {

        @Override
        public void onTwinlifeReady() {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContextObserver.onTwinlifeReady");
            }

            CreateSpaceCardExecutor.this.onTwinlifeReady();
            onOperation();
        }

        @Override
        public void onTwinlifeOnline() {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContextObserver.onTwinlifeOnline");
            }

            CreateSpaceCardExecutor.this.onTwinlifeOnline();
            onOperation();
        }

        @Override
        public void onTwinlifeOffline() {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinlifeObserver.onTwinlifeOffline");
            }

            CreateSpaceCardExecutor.this.onTwinlifeOffline();
        }

        @Override
        public void onError(long requestId, ErrorCode errorCode, @Nullable String errorParameter) {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContextObserver.onError: requestId=" + requestId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
            }

            Integer operationId = mRequestIds.remove(requestId);
            if (operationId != null) {
                CreateSpaceCardExecutor.this.onError(operationId, errorCode, errorParameter);
                onOperation();
            }
        }
    }

    class RepositoryServiceObserver extends RepositoryService.DefaultServiceObserver {

        @Override
        public void onCreateObject(long requestId, @NonNull RepositoryService.Object object) {
            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryServiceObserver.onCreateObject: requestId=" + requestId + " object=" + object);
            }

            Integer operationId = mRequestIds.remove(requestId);
            if (operationId != null) {
                CreateSpaceCardExecutor.this.onCreateObject(operationId, object);
                onOperation();
            }
        }

        @Override
        public void onError(long requestId, ErrorCode errorCode, @Nullable String errorParameter) {
            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryServiceObserver.onError: requestId=" + requestId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
            }

            Integer operationId = mRequestIds.remove(requestId);
            if (operationId != null) {
                CreateSpaceCardExecutor.this.onError(operationId, errorCode, errorParameter);
            }
        }
    }

    static final class GroupInvitationPair {
        @NonNull
        final UUID groupTwincodeId;
        @NonNull
        final GroupProvisioningDescriptor group;
        @NonNull
        final InvitationProvisioningDescriptor invitation;

        GroupInvitationPair(@NonNull GroupProvisioningDescriptor group, @NonNull UUID groupTwincodeId,
                            @NonNull InvitationProvisioningDescriptor invitation) {

            this.group = group;
            this.groupTwincodeId = groupTwincodeId;
            this.invitation = invitation;
        }
    }

    @NonNull
    private final TwinmeContextImpl mTwinmeContextImpl;
    @NonNull
    private final TwinmeEngineImpl mTwinmeEngineImpl;
    @NonNull
    private SpaceCard mSpaceCard;
    @NonNull
    private final EngineCard mEngineCard;
    private final List<GroupInvitationPair> mInvitations;
    @NonNull
    private final Consumer<SpaceCard> mConsumer;
    private final String mName;
    private final String mDescription;
    private final Bitmap mAvatar;
    private TwincodeFactory mSpaceTwincodeFactory;

    private int mState = 0;
    @SuppressLint("UseSparseArrays")
    private final HashMap<Long, Integer> mRequestIds = new HashMap<>();
    private final long mRequestId;
    private boolean mRestarted = false;
    private boolean mStopped = false;

    private final TwinmeContextObserver mTwinmeContextObserver;
    private final RepositoryServiceObserver mRepositoryServiceObserver;

    public CreateSpaceCardExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId,
                                    @NonNull TwinmeEngineImpl twinmeEngineImpl,
                                    @NonNull EngineCard engineCard,
                                    @NonNull Map<GroupProvisioningDescriptor, UUID> groupTwincodes,
                                    @NonNull SpaceCardProvisioningDescriptor spaceCardProvisioningDescriptor,
                                    @NonNull Consumer<SpaceCard> consumer) {

        mTwinmeContextImpl = twinmeContextImpl;
        mTwinmeEngineImpl = twinmeEngineImpl;
        mRequestId = requestId;
        mEngineCard = engineCard;
        mName = spaceCardProvisioningDescriptor.name;
        mDescription = spaceCardProvisioningDescriptor.description;
        mAvatar = spaceCardProvisioningDescriptor.avatar;
        mInvitations = new ArrayList<>();
        for (Map.Entry<GroupProvisioningDescriptor, InvitationProvisioningDescriptor> invitation : spaceCardProvisioningDescriptor.invitations.entrySet()) {

            mInvitations.add(new GroupInvitationPair(invitation.getKey(), groupTwincodes.get(invitation.getKey()), invitation.getValue()));
        }
        mConsumer = consumer;

        mTwinmeContextObserver = new TwinmeContextObserver();
        mRepositoryServiceObserver = new RepositoryServiceObserver();
    }

    public void start() {
        if (DEBUG) {
            Log.d(LOG_TAG, "start");
        }

        mTwinmeContextImpl.setObserver(mTwinmeContextObserver);
    }

    //
    // Private methods
    //

    private long newOperation(int operationId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "newOperation: operationId=" + operationId);
        }

        long requestId = mTwinmeContextImpl.newRequestId();
        mRequestIds.put(requestId, operationId);

        return requestId;
    }

    private void onTwinlifeReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeReady");
        }

        mTwinmeContextImpl.getRepositoryService().addServiceObserver(mRepositoryServiceObserver);
    }

    private void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            mRestarted = false;

            if ((mState & CREATE_OBJECT) != 0 && (mState & CREATE_OBJECT_DONE) == 0) {
                mState &= ~CREATE_OBJECT;
            }
        }
    }

    private void onTwinlifeOffline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOffline");
        }

        mRestarted = true;
    }

    private void onOperation() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOperation");
        }

        if (mStopped) {

            return;
        }

        //
        // Step 1: create the group admin twincode.
        //

        boolean completedStep1 = true;

        if ((mState & CREATE_SPACE_TWINCODE) == 0) {
            mState |= CREATE_SPACE_TWINCODE;
            completedStep1 = false;

            mTwinmeContextImpl.assertNotNull(LOG_TAG, mName);

            List<BaseService.AttributeNameValue> twincodeFactoryAttributes = new ArrayList<>();
            PairProtocol.setTwincodeAttributePair(twincodeFactoryAttributes);

            List<BaseService.AttributeNameValue> twincodeOutboundAttributes = new ArrayList<>();
            TwinmeAttributes.setTwincodeAttributeName(twincodeOutboundAttributes, mName);
            //if (mAvatar != null) {
            //    TwinmeAttributes.setTwincodeAttributeImage(twincodeOutboundAttributes, mAvatar);
            //}
            if (mDescription != null) {
                TwinmeAttributes.setTwincodeAttributeDescription(twincodeOutboundAttributes, mDescription);
            }
            //TwinmeAttributes.setTwincodeAttributeImage(twincodeOutboundAttributes, mAvatar);

            long requestId = newOperation(CREATE_SPACE_TWINCODE);
            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeFactoryService.createTwincode: requestId=" + requestId
                        + " twincodeFactoryAttributes=" + twincodeFactoryAttributes
                        + "twincodeInboundAttributes=null" + "twincodeOutboundAttributes=" + twincodeOutboundAttributes);
            }
            mTwinmeEngineImpl.createTwincode(requestId, twincodeFactoryAttributes, null,
                    twincodeOutboundAttributes, null, this::onCreateTwincode);
        }
        if ((mState & CREATE_SPACE_TWINCODE_DONE) == 0) {
            completedStep1 = false;
        }

        if (!completedStep1) {

            return;
        }

        //
        // Step 2: create the invitations provisioning for the groups.
        //
        if (!mInvitations.isEmpty()) {
            boolean completedStep2 = true;

            if ((mState & CREATE_INVITATION) == 0) {
                mState |= CREATE_INVITATION;
                completedStep2 = false;

                long requestId = newOperation(CREATE_INVITATION);
                if (DEBUG) {
                    Log.d(LOG_TAG, "twinmeContext.getGroups: requestId=" + requestId);
                }
                GroupInvitationPair groupInvitations = mInvitations.get(0);
                InvitationProvisioningDescriptor invitationProvisioningDescriptor = groupInvitations.invitation;

                mTwinmeEngineImpl.createInvitationProvisionning(requestId, groupInvitations.groupTwincodeId,
                        groupInvitations.group, invitationProvisioningDescriptor,
                        (ChannelInvitation invitation) -> {
                            mRequestIds.remove(requestId);
                            mEngineCard.getInvitations().add(invitation);
                            mSpaceCard.getInvitations().add(invitation.invitationTwincodeOutId);
                            mInvitations.remove(0);
                            if (mInvitations.isEmpty()) {
                                mState |= CREATE_INVITATION_DONE;
                            } else {
                                mState &= ~(CREATE_INVITATION);
                            }
                            onOperation();
                        });
            }
            if ((mState & CREATE_INVITATION_DONE) == 0) {
                completedStep2 = false;
            }

            if (!completedStep2) {

                return;
            }
        }

        boolean completedStep3 = true;

        if ((mState & CREATE_OBJECT) == 0) {
            mState |= CREATE_OBJECT;
            completedStep3 = false;

            long requestId = newOperation(CREATE_OBJECT);
            String content = mSpaceCard.serialize(mTwinmeContextImpl.getRepositoryService());
            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryService.createObject: requestId=" + requestId + " spaceCard=" + mSpaceCard);
            }
            mTwinmeContextImpl.getRepositoryService().createObject(requestId, RepositoryService.AccessRights.PUBLIC,
                    mSpaceCard.getSchemaId(), mSpaceCard.getSchemaVersion(),
                    mSpaceCard.getSerializer(), mSpaceCard.isImmutable(), null, content, null);
        }
        if ((mState & CREATE_OBJECT_DONE) == 0) {
            completedStep3 = false;
        }

        if (!completedStep3) {

            return;
        }

        //
        // Last Step
        //

        mTwinmeContextImpl.assertNotNull(LOG_TAG, mSpaceCard);

        mConsumer.accept(mSpaceCard);
        stop();
    }

    private void onCreateTwincode(@Nullable ErrorCode status, @Nullable TwincodeFactory twincodeFactory) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateInvitationTwincode: factory=" + twincodeFactory);
        }

        if (status != ErrorCode.SUCCESS || twincodeFactory == null) {
            onError(CREATE_SPACE_TWINCODE, status, null);
            return;
        }

        mState |= CREATE_SPACE_TWINCODE_DONE;
        mSpaceTwincodeFactory = twincodeFactory;
        mSpaceCard = new SpaceCard(mName, twincodeFactory.getTwincodeOutboundId());
        onOperation();
    }

    private void onCreateObject(int operationId, @NonNull RepositoryService.Object object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateObject: operationId=" + operationId + " object=" + object);
        }

        mTwinmeContextImpl.assertEqual(LOG_TAG, SpaceCard.SCHEMA_ID, object.getSchemaId());

        if ((mState & CREATE_OBJECT_DONE) != 0) {

            return;
        }
        mState |= CREATE_OBJECT_DONE;

        SpaceCard spaceCard = SpaceCard.deserialize(mTwinmeContextImpl.getRepositoryService(), object);

        mTwinmeContextImpl.assertNotNull(LOG_TAG, spaceCard);

        if (spaceCard != null) {
            mSpaceCard = spaceCard;
        }
    }

    private void onError(int operationId, ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: operationId=" + operationId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        // Wait for reconnection
        if (errorCode == ErrorCode.TWINLIFE_OFFLINE) {
            mRestarted = true;

            return;
        }

        mTwinmeContextImpl.fireOnError(mRequestId, errorCode, errorParameter);

        stop();
    }

    private void stop() {
        if (DEBUG) {
            Log.d(LOG_TAG, "stop");
        }

        mStopped = true;

        mTwinmeContextImpl.getRepositoryService().removeServiceObserver(mRepositoryServiceObserver);

        mTwinmeContextImpl.removeObserver(mTwinmeContextObserver);
    }
}
