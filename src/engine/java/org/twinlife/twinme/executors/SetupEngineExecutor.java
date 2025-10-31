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
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.GroupConversation;
import org.twinlife.twinme.TwinmeContext;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.TwinmeEngineImpl;
import org.twinlife.twinme.models.EngineCard;
import org.twinlife.twinme.models.EngineCard.GroupProvisioning;
import org.twinlife.twinme.models.Group;
import org.twinlife.twinme.models.GroupProvisioningDescriptor;
import org.twinlife.twinme.models.Invitation;
import org.twinlife.twinme.models.Space;

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

public class SetupEngineExecutor {
    private static final String LOG_TAG = "SetupEngineExec";
    private static final boolean DEBUG = false;

    private static final int GET_INVITATIONS = 1;
    private static final int GET_INVITATIONS_DONE = 1 << 1;
    private static final int GET_GROUPS = 1 << 2;
    private static final int GET_GROUPS_DONE = 1 << 3;
    private static final int CREATE_GROUP = 1 << 4;
    private static final int CREATE_GROUP_DONE = 1 << 5;
    private static final int CREATE_INVITATION = 1 << 6;
    private static final int CREATE_INVITATION_DONE = 1 << 7;

    private class TwinmeContextObserver extends TwinmeContext.DefaultObserver {

        @Override
        public void onTwinlifeReady() {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContextObserver.onTwinlifeReady");
            }

            SetupEngineExecutor.this.onTwinlifeReady();
            onOperation();
        }

        @Override
        public void onTwinlifeOnline() {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContextObserver.onTwinlifeOnline");
            }

            SetupEngineExecutor.this.onTwinlifeOnline();
            onOperation();
        }

        @Override
        public void onTwinlifeOffline() {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinlifeObserver.onTwinlifeOffline");
            }

            SetupEngineExecutor.this.onTwinlifeOffline();
        }

        @Override
        public void onCreateGroup(long requestId, @NonNull Group group, @NonNull GroupConversation groupConversation) {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinlifeObserver.onCreateGroup");
            }

            Integer operationId = mRequestIds.remove(requestId);
            if (operationId != null) {
                SetupEngineExecutor.this.onCreateGroup(group, groupConversation);
                onOperation();
            }
        }

        @Override
        public void onCreateInvitation(long requestId, @NonNull Invitation invitation) {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinlifeObserver.onCreateInvitation");
            }

            Integer operationId = mRequestIds.remove(requestId);
            if (operationId != null) {
                SetupEngineExecutor.this.onCreateInvitation(invitation);
                onOperation();
            }
        }

        @Override
        public void onError(long requestId, ErrorCode errorCode, @Nullable String errorParameter) {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContextObserver.onError: requestId=" + requestId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
            }

            Integer operationId = mRequestIds.remove(requestId);
            if (operationId != null) {
                SetupEngineExecutor.this.onError(operationId, errorCode, errorParameter);
                onOperation();
            }
        }
    }

    @NonNull
    private final TwinmeContextImpl mTwinmeContextImpl;
    @NonNull
    private final TwinmeEngineImpl mTwinmeEngineImpl;
    private final EngineCard mEngineCard;
    private final List<EngineCard.ChannelInvitation> mInvitations;
    private final Map<UUID, Group> mGroups = new HashMap<>();
    private final List<GroupProvisioning> mCreateGroupList = new ArrayList<>();
    private final Space mSpace;

    private int mState = 0;
    @SuppressLint("UseSparseArrays")
    private final HashMap<Long, Integer> mRequestIds = new HashMap<>();
    private final long mRequestId;
    private boolean mRestarted = false;
    private boolean mStopped = false;

    private final TwinmeContextObserver mTwinmeContextObserver;

    public SetupEngineExecutor(@NonNull TwinmeEngineImpl twinmeEngineImpl, long requestId, @NonNull EngineCard engineCard, @NonNull Space space) {

        mTwinmeEngineImpl = twinmeEngineImpl;
        mTwinmeContextImpl = (TwinmeContextImpl)twinmeEngineImpl.getTwinmeContext();
        mRequestId = requestId;
        mEngineCard = engineCard;
        mSpace = space;

        mTwinmeContextObserver = new TwinmeContextObserver();
        mInvitations = new ArrayList<>(engineCard.getInvitations());
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
    }

    private void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            mRestarted = false;
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
        // Step 1: get the list of known groups.
        //

        boolean completedStep1 = true;

        if ((mState & GET_GROUPS) == 0) {
            mState |= GET_GROUPS;
            completedStep1 = false;

            long requestId = newOperation(GET_GROUPS);
            if (DEBUG) {
                Log.d(LOG_TAG, "twinmeContext.getGroups: requestId=" + requestId);
            }
            mTwinmeContextImpl.findGroups(requestId, (Group group) -> true, (List<Group> groups) -> {
                onGetGroups(groups);
                onOperation();
            });
        }
        if ((mState & GET_GROUPS_DONE) == 0) {
            completedStep1 = false;
        }

        if (!completedStep1) {

            return;
        }

        //
        // Step 2: get the list of known invitations.
        //

        boolean completedStep2 = true;

        if ((mState & GET_INVITATIONS) == 0) {
            mState |= GET_INVITATIONS;
            completedStep2 = false;

            long requestId = newOperation(GET_INVITATIONS);
            if (DEBUG) {
                Log.d(LOG_TAG, "twinmeContext.findInvitations: requestId=" + requestId);
            }
            mTwinmeContextImpl.findInvitations(requestId, (Invitation invitation) -> true, (List<Invitation> invitations) -> {
                onFindInvitations(invitations);
                onOperation();
            });
        }
        if ((mState & GET_INVITATIONS_DONE) == 0) {
            completedStep2 = false;
        }

        if (!completedStep2) {

            return;
        }

        //
        // Step 3: create the group that are not yet created.
        //
        if (!mCreateGroupList.isEmpty()) {

            boolean completedStep3 = true;

            if ((mState & CREATE_GROUP) == 0) {
                mState |= CREATE_GROUP;
                completedStep3 = false;

                long requestId = newOperation(CREATE_GROUP);
                if (DEBUG) {
                    Log.d(LOG_TAG, "twinmeContext.findInvitations: requestId=" + requestId);
                }
                GroupProvisioning info = mCreateGroupList.get(0);
                mTwinmeEngineImpl.createGroup(requestId, mSpace, info);
            }
            if ((mState & CREATE_GROUP_DONE) == 0) {
                completedStep3 = false;
            }

            if (!completedStep3) {

                return;
            }
        }

        //
        // Step 4: create the invitation to join the groups.
        //
        if (!mInvitations.isEmpty()) {

            boolean completedStep4 = true;

            if ((mState & CREATE_INVITATION) == 0) {
                mState |= CREATE_INVITATION;
                completedStep4 = false;

                long requestId = newOperation(CREATE_INVITATION);
                if (DEBUG) {
                    Log.d(LOG_TAG, "twinmeContext.createInvitation: requestId=" + requestId);
                }
                EngineCard.ChannelInvitation info = mInvitations.get(0);
                Group group = mGroups.get(info.groupTwincodeId);
                mTwinmeEngineImpl.createInvitation(requestId, mSpace, group, info.permissions,
                        info.invitationTwincodeInId, info.invitationTwincodeOutId);
            }
            if ((mState & CREATE_INVITATION_DONE) == 0) {
                completedStep4 = false;
            }

            if (!completedStep4) {

                return;
            }
        }

        //
        // Last Step
        //

        mTwinmeContextImpl.assertNotNull(LOG_TAG, mEngineCard);

        stop();
    }

    private void onGetGroups(List<Group> groups) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetGroups: operationId=" + groups);
        }

        mState |= GET_GROUPS_DONE;
        for (Group group : groups) {
            mGroups.put(group.getGroupTwincodeOutboundId(), group);
        }

        // Identify groups that are already created and setup the engine to forward their messages.
        // Prepare to create the others.
        ConversationService conversationService = mTwinmeContextImpl.getConversationService();
        for (GroupProvisioning toProvision : mEngineCard.getGroups()) {
            if (!mGroups.containsKey(toProvision.groupTwincodeOutId)) {
                mCreateGroupList.add(toProvision);
            } else {
                GroupConversation c = conversationService.getGroupConversationWithGroupTwincodeId(toProvision.groupTwincodeOutId);
                if (c != null) {
                    mTwinmeEngineImpl.addForward(c.getContactId(), c.getId());
                }
            }
        }
    }

    private void onFindInvitations(List<Invitation> invitations) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onFindInvitations: invitations=" + invitations);
        }

        // Check that every invitation is known, what will remain in mInvitations correspond to the invitations to create.
        mState |= GET_INVITATIONS_DONE;
        for (int i = mInvitations.size(); i > 0; ) {
            i--;
            EngineCard.ChannelInvitation channelInvitation = mInvitations.get(i);
            Group group = mGroups.get(channelInvitation.groupTwincodeId);

            if (group != null) {
                for (Invitation invitation : invitations) {
                    if (group.getId().equals(invitation.getGroupId()) && invitation.getPermissions() == channelInvitation.permissions) {
                        mInvitations.remove(i);
                        break;
                    }
                }
            } else {
                boolean found = false;

                for (GroupProvisioning groupProvisioning : mCreateGroupList) {
                    if (groupProvisioning.groupTwincodeOutId.equals(channelInvitation.groupTwincodeId)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    mInvitations.remove(i);
                }
            }
        }
    }

    private void onCreateGroup(@NonNull Group group, @NonNull GroupConversation groupConversation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateGroup: group=" + group + " groupConversation=" + groupConversation);
        }

        mState |= CREATE_GROUP_DONE;
        mGroups.put(group.getGroupTwincodeOutboundId(), group);
        mCreateGroupList.remove(0);
        if (!mCreateGroupList.isEmpty()) {
            mState &= ~(CREATE_GROUP | CREATE_GROUP_DONE);
        }

        // Setup the engine to forward messages.
        mTwinmeEngineImpl.addForward(groupConversation.getContactId(), groupConversation.getId());
    }

    private void onCreateInvitation(Invitation invitation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateInvitation: invitation=" + invitation);
        }

        mState |= CREATE_INVITATION_DONE;
        mInvitations.remove(0);
        if (!mInvitations.isEmpty()) {
            mState &= ~(CREATE_INVITATION | CREATE_INVITATION_DONE);
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

        mTwinmeContextImpl.removeObserver(mTwinmeContextObserver);
    }
}
