/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.executors;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.Permission;
import org.twinlife.twinlife.RosterId;
import org.twinlife.twinlife.RosterMember;
import org.twinlife.twinlife.SecureRoster;
import org.twinlife.twinlife.SignedRosterGroup;
import org.twinlife.twinlife.TrustMethod;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Group;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

//
// Executor and observers are running in the SingleThreadExecutor provided by the twinlife library
// Observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.0
//
// Called as background operation: must be connected and no timeout.

public class RefreshRosterExecutor extends AbstractConnectedTwinmeExecutor {
    private static final String LOG_TAG = "RefreshRosterExecutor";
    private static final boolean DEBUG = false;

    private static final int REFRESH_GROUP_TWINCODE = 1;
    private static final int REFRESH_GROUP_TWINCODE_DONE = 1 << 1;
    private static final int REFRESH_ROSTER = 1 << 2;
    private static final int REFRESH_ROSTER_DONE = 1 << 3;
    private static final int GET_PEER_TWINCODE = 1 << 4;
    private static final int GET_PEER_TWINCODE_DONE = 1 << 5;

    @Nullable
    private final TwincodeOutbound mGroupTwincode;
    @NonNull
    private final Group mGroup;
    @NonNull
    private final Consumer<Void> mComplete;
    private final Map<UUID, TwincodeOutbound> mMemberTWincodes;
    private final List<RosterMember> mMembers;
    @Nullable
    private RosterId mRosterId;
    @Nullable
    private SecureRoster mSecureRoster;
    private RosterMember mCheckMember;
    @Nullable
    private ConversationService.GroupConversation mGroupConversation;

    public RefreshRosterExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, @NonNull Group group, @NonNull Consumer<Void> complete) {
        super(twinmeContextImpl, BaseService.DEFAULT_REQUEST_ID, LOG_TAG);
        if (DEBUG) {
            Log.d(LOG_TAG, "RefreshRosterExecutor: twinmeContextImpl=" + twinmeContextImpl + " complete=" + complete);
        }

        mMemberTWincodes = new HashMap<>();
        mMembers = new ArrayList<>();
        mGroup = group;
        mRosterId = mGroup.getSecureRosterId();
        mGroupTwincode = mGroup.getGroupTwincodeOutbound();
        mComplete = complete;
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            if ((mState & REFRESH_GROUP_TWINCODE) != 0 && (mState & REFRESH_GROUP_TWINCODE_DONE) == 0) {
                mState &= ~REFRESH_GROUP_TWINCODE;
            }
            if ((mState & REFRESH_ROSTER) != 0 && (mState & REFRESH_ROSTER_DONE) == 0) {
                mState &= ~REFRESH_ROSTER;
            }
            if ((mState & GET_PEER_TWINCODE) != 0 && (mState & GET_PEER_TWINCODE_DONE) == 0) {
                mState &= ~GET_PEER_TWINCODE;
            }
        }
        super.onTwinlifeOnline();
    }

    //
    // Private methods
    //

    protected void onOperation() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOperation");
        }

        if (mStopped) {

            return;
        }

        //
        // Step 1: refresh the group twincode to make sure we have the correct secure roster attributes.
        //
        if (mGroupTwincode != null) {
            if ((mState & REFRESH_GROUP_TWINCODE) == 0) {
                mState |= REFRESH_GROUP_TWINCODE;

                mTwinmeContextImpl.getTwincodeOutboundService().refreshTwincode(mGroupTwincode, this::onRefreshGroupTwincode);
                return;
            }
            if ((mState & REFRESH_GROUP_TWINCODE_DONE) == 0) {
                return;
            }
            mGroupConversation = mTwinmeContextImpl.getConversationService().getGroupConversationWithGroupTwincodeId(mGroupTwincode.getId());
        }

        //
        // Step 2: get the list of members of the secure roster.
        //
        if (mRosterId != null) {
            if ((mState & REFRESH_ROSTER) == 0) {
                mState |= REFRESH_ROSTER;
                mTwinmeContextImpl.getSecureRosterService().listRoster(mRosterId, 0, this::onListRoster);
                return;
            }
            if ((mState & REFRESH_ROSTER_DONE) == 0) {
                return;
            }
        }

        //
        // Step 3: get the twincode representing each twincode member of the secure roster.
        //
        if (mCheckMember != null) {
            if ((mState & GET_PEER_TWINCODE) == 0) {
                mState |= GET_PEER_TWINCODE;

                if (mCheckMember.publicKey.isEmpty()) {
                    mTwinmeContextImpl.getTwincodeOutboundService().getTwincode(mCheckMember.memberTwincodeId, TwincodeOutboundService.REFRESH_PERIOD, this::onGetMemberTwincode);
                } else {
                    mTwinmeContextImpl.getTwincodeOutboundService().getSignedTwincode(mCheckMember.memberTwincodeId, mCheckMember.publicKey, TrustMethod.PEER, this::onGetMemberTwincode);
                }
            }
            return;
        }

        //
        // Step 4: refresh the conversations representing group members.
        //
        if (mRosterId != null) {
            mTwinmeContextImpl.getConversationService().refreshGroup(mGroup, mMembers, mMemberTWincodes);
            mGroup.markMemberRefreshed(mTwinmeContextImpl);
        }

        //
        // Last Step
        //
        mComplete.onGet(ErrorCode.SUCCESS, null);
        stop();
    }

    private void onRefreshGroupTwincode(@NonNull ErrorCode errorCode, @Nullable List<BaseService.AttributeNameValue> attributes) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onRefreshGroupTwincode: errorCode=" + errorCode + " attributes=" + attributes);
        }

        if (errorCode != ErrorCode.SUCCESS) {
            onOperationError(REFRESH_GROUP_TWINCODE, errorCode, null);
            return;
        }

        mState |= REFRESH_GROUP_TWINCODE_DONE;
        mRosterId = mGroup.getSecureRosterId();
        onOperation();
    }

    private void onListRoster(@NonNull ErrorCode errorCode, @Nullable SecureRoster secureRoster) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onListRoster: errorCode=" + errorCode + " secureRoster=" + secureRoster);
        }

        if (errorCode != ErrorCode.SUCCESS || secureRoster == null) {
            onOperationError(REFRESH_ROSTER, errorCode, null);
            return;
        }

        mState |= REFRESH_ROSTER_DONE;
        mSecureRoster = secureRoster;
        mGroup.putLong(Group.MAX_GROUP_MEMBERS, secureRoster.getMaxMemberCount(), mTwinmeContextImpl);
        nextRosterMember();
        onOperation();
    }

    private void nextRosterMember() {

        mState &= ~(GET_PEER_TWINCODE | GET_PEER_TWINCODE_DONE);
        boolean allowEmptyKey = mRosterId != null && mRosterId.schemaId.equals(Group.LEGACY_SCHEMA_ID);
        while (mSecureRoster != null && !mSecureRoster.isEmpty()) {
            final List<SignedRosterGroup> groups = mSecureRoster.getGroups();
            final int last = groups.size() - 1;
            final SignedRosterGroup rosterGroup = groups.get(last);
            if (rosterGroup != null) {
                final List<RosterMember> members = rosterGroup.members;
                while (!members.isEmpty()) {
                    final RosterMember member = members.remove(members.size() - 1);
                    if (member != null) {
                        // Accept only verified members and members with a public key or a legacy group member.
                        if (member.verified && (!member.publicKey.isEmpty() || allowEmptyKey)) {
                            mCheckMember = member;
                            return;
                        }
                    }
                }
            }
            groups.remove(last);
        }
        mSecureRoster = null;
        mCheckMember = null;
    }

    private void onGetMemberTwincode(@NonNull ErrorCode errorCode, @Nullable TwincodeOutbound memberTwincode) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetMemberTwincode: errorCode=" + errorCode + " memberTwincode=" + memberTwincode);
        }

        if (errorCode != ErrorCode.SUCCESS || memberTwincode == null) {
            onOperationError(GET_PEER_TWINCODE, errorCode, null);
            return;
        }

        mState |= GET_PEER_TWINCODE_DONE;
        mMemberTWincodes.put(memberTwincode.getId(), memberTwincode);
        mMembers.add(mCheckMember);
        nextRosterMember();
        onOperation();
    }

    protected void onOperationError(int operationId, ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: operationId=" + operationId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        // Wait for reconnection
        if (errorCode == ErrorCode.TWINLIFE_OFFLINE) {
            mRestarted = true;
            return;
        }

        if (errorCode == ErrorCode.ITEM_NOT_FOUND || errorCode == ErrorCode.EXPIRED) {
            // If the peer member is not found, remove it from the secure roster (the result is ignored and we don't even retry if we are disconnected).
            if (operationId == GET_PEER_TWINCODE) {
                if (mRosterId != null && mCheckMember != null && mGroupConversation != null && mGroupConversation.hasPermission(Permission.REMOVE_MEMBER)) {
                    mTwinmeContextImpl.getSecureRosterService().deleteMember(mRosterId.id, mCheckMember.memberTwincodeId, (ErrorCode lErrorCode, Void unused) -> {

                    });
                }
                nextRosterMember();
                onOperation();
                return;
            }

            // If the group twincode or secure roster are not found, the group must be deleted.
            mTwinmeContextImpl.deleteGroup(BaseService.DEFAULT_REQUEST_ID, mGroup);
        }

        // Mark the executor as stopped before calling fireOnError().
        stop();

        mTwinmeContextImpl.fireOnError(mRequestId, errorCode, errorParameter);
    }
}
