/*
 *  Copyright (c) 2019 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.executors;

import android.annotation.SuppressLint;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinme.TwinmeContext;
import org.twinlife.twinme.TwinmeContext.Consumer;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.TwinmeEngineImpl;
import org.twinlife.twinme.models.EngineCard;
import org.twinlife.twinme.models.EngineCard.GroupProvisioning;
import org.twinlife.twinme.models.EngineProvisioningDescriptor;
import org.twinlife.twinme.models.GroupProvisioningDescriptor;
import org.twinlife.twinme.models.InvitationProvisioningDescriptor;
import org.twinlife.twinme.models.SpaceCard;
import org.twinlife.twinme.models.SpaceCardProvisioningDescriptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.0
//

public class CreateEngineCardExecutor {
    private static final String LOG_TAG = "CreateEngineCardExecutor";
    private static final boolean DEBUG = false;

    private static final int CREATE_GROUP = 1;
    private static final int CREATE_GROUP_DONE = 1 << 1;
    private static final int CREATE_SPACE_CARD = 1 << 2;
    private static final int CREATE_SPACE_CARD_DONE = 1 << 3;
    private static final int CREATE_OBJECT = 1 << 4;
    private static final int CREATE_OBJECT_DONE = 1 << 5;

    private class TwinmeContextObserver extends TwinmeContext.DefaultObserver {

        @Override
        public void onTwinlifeReady() {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContextObserver.onTwinlifeReady");
            }

            CreateEngineCardExecutor.this.onTwinlifeReady();
            onOperation();
        }

        @Override
        public void onTwinlifeOnline() {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContextObserver.onTwinlifeOnline");
            }

            CreateEngineCardExecutor.this.onTwinlifeOnline();
            onOperation();
        }

        @Override
        public void onTwinlifeOffline() {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinlifeObserver.onTwinlifeOffline");
            }

            CreateEngineCardExecutor.this.onTwinlifeOffline();
        }

        @Override
        public void onError(long requestId, ErrorCode errorCode, @Nullable String errorParameter) {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContextObserver.onError: requestId=" + requestId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
            }

            Integer operationId = mRequestIds.remove(requestId);
            if (operationId != null) {
                CreateEngineCardExecutor.this.onError(operationId, errorCode, errorParameter);
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
                CreateEngineCardExecutor.this.onCreateObject(operationId, object);
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
                CreateEngineCardExecutor.this.onError(operationId, errorCode, errorParameter);
            }
        }
    }

    static final class GroupInvitationPair {
        @NonNull
        final UUID groupTwincodeId;
        @NonNull
        final GroupProvisioningDescriptor group;
        @NonNull
        final List<InvitationProvisioningDescriptor> invitations;

        GroupInvitationPair(@NonNull GroupProvisioningDescriptor group, @NonNull UUID groupTwincodeId,
                            @NonNull List<InvitationProvisioningDescriptor> invitations) {

            this.group = group;
            this.groupTwincodeId = groupTwincodeId;
            this.invitations = new ArrayList<>(invitations);
        }
    }

    @NonNull
    private final TwinmeContextImpl mTwinmeContextImpl;
    @NonNull
    private final TwinmeEngineImpl mTwinmeEngineImpl;
    @NonNull
    private EngineCard mEngineCard;
    private final List<GroupProvisioningDescriptor> mGroups;
    private final List<GroupInvitationPair> mInvitations;
    private final List<SpaceCardProvisioningDescriptor> mSpaceCards;
    private final Map<GroupProvisioningDescriptor, UUID> mGroupTwincodes;
    @NonNull
    private final Consumer<EngineCard> mConsumer;

    private int mState = 0;
    @SuppressLint("UseSparseArrays")
    private final HashMap<Long, Integer> mRequestIds = new HashMap<>();
    private final long mRequestId;
    private boolean mRestarted = false;
    private boolean mStopped = false;

    private final TwinmeContextObserver mTwinmeContextObserver;
    private final RepositoryServiceObserver mRepositoryServiceObserver;

    public CreateEngineCardExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId,
                                    @NonNull TwinmeEngineImpl twinmeEngineImpl,
                                    @NonNull EngineProvisioningDescriptor engineDescriptor,
                                    @NonNull Consumer<EngineCard> consumer) {

        mTwinmeContextImpl = twinmeContextImpl;
        mTwinmeEngineImpl = twinmeEngineImpl;
        mRequestId = requestId;
        mGroups = engineDescriptor.groups;
        mGroupTwincodes = new HashMap<>();
        mSpaceCards = engineDescriptor.spaceCards;
        mEngineCard = new EngineCard();
        mInvitations = new ArrayList<>();
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
        // Step 1: create the group provisioning list (admin and group twincodes).
        //

        if (!mGroups.isEmpty()) {
            boolean completedStep1 = true;

            if ((mState & CREATE_GROUP) == 0) {
                mState |= CREATE_GROUP;
                completedStep1 = false;

                long requestId = newOperation(CREATE_GROUP);
                if (DEBUG) {
                    Log.d(LOG_TAG, "twinmeContext.getGroups: requestId=" + requestId);
                }
                GroupProvisioningDescriptor groupDescriptor = mGroups.get(0);
                mTwinmeEngineImpl.createGroupProvisionning(requestId, groupDescriptor, (GroupProvisioning group) -> {
                    mRequestIds.remove(requestId);
                    //if (!groupDescriptor.invitations.isEmpty()) {
                    //    mInvitations.add(new GroupInvitationPair(groupDescriptor, group.groupTwincodeOutId, groupDescriptor.invitations));
                    //}
                    mGroupTwincodes.put(groupDescriptor, group.groupTwincodeOutId);
                    mEngineCard.getGroups().add(group);
                    mGroups.remove(0);
                    if (mGroups.isEmpty()) {
                        mState |= CREATE_GROUP_DONE;
                    } else {
                        mState &= ~(CREATE_GROUP);
                    }
                    onOperation();
                });
            }
            if ((mState & CREATE_GROUP_DONE) == 0) {
                completedStep1 = false;
            }

            if (!completedStep1) {

                return;
            }
        }

        //
        // Step 2: create the invitations provisionning for the groups.
        //
        if (!mSpaceCards.isEmpty()) {
            boolean completedStep2 = true;

            if ((mState & CREATE_SPACE_CARD) == 0) {
                mState |= CREATE_SPACE_CARD;
                completedStep2 = false;

                long requestId = newOperation(CREATE_SPACE_CARD);
                if (DEBUG) {
                    Log.d(LOG_TAG, "twinmeContext.getGroups: requestId=" + requestId);
                }
                SpaceCardProvisioningDescriptor spaceCardProvisioningDescriptor = mSpaceCards.get(0);

                mTwinmeEngineImpl.createSpaceCardProvisionning(requestId, mEngineCard, mGroupTwincodes, spaceCardProvisioningDescriptor,
                        (SpaceCard spaceCard) -> {
                            mRequestIds.remove(requestId);
                            mEngineCard.getSpaceCards().add(spaceCard);
                            mSpaceCards.remove(0);
                            if (mSpaceCards.isEmpty()) {
                                mState |= CREATE_SPACE_CARD_DONE;
                            } else {
                                mState &= ~(CREATE_SPACE_CARD);
                            }
                            onOperation();
                        });
            }
            if ((mState & CREATE_SPACE_CARD_DONE) == 0) {
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
            String content = mEngineCard.serialize(mTwinmeContextImpl.getRepositoryService());
            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryService.createObject: requestId=" + requestId + " engineCard=" + mEngineCard);
            }
            mTwinmeContextImpl.getRepositoryService().createObject(requestId, RepositoryService.AccessRights.PUBLIC,
                    mEngineCard.getSchemaId(), mEngineCard.getSchemaVersion(),
                    mEngineCard.getSerializer(), mEngineCard.isImmutable(), mEngineCard.getId(), content, null);
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

        mTwinmeContextImpl.assertNotNull(LOG_TAG, mEngineCard);

        mConsumer.accept(mEngineCard);
        stop();
    }

    private void onCreateObject(int operationId, @NonNull RepositoryService.Object object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateObject: operationId=" + operationId + " object=" + object);
        }

        mTwinmeContextImpl.assertEqual(LOG_TAG, EngineCard.SCHEMA_ID, object.getSchemaId());

        if ((mState & CREATE_OBJECT_DONE) != 0) {

            return;
        }
        mState |= CREATE_OBJECT_DONE;
        EngineCard engineCard = EngineCard.deserialize(mTwinmeContextImpl.getRepositoryService(), object);
        if (engineCard != null) {
            mEngineCard.updateFrom(engineCard);
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
