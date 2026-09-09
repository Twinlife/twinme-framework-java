/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.ErrorCode;
import org.twinlife.twinlife.Permission;
import org.twinlife.twinlife.RosterId;
import org.twinlife.twinlife.RosterMember;
import org.twinlife.twinlife.SecureRoster;
import org.twinlife.twinlife.SecureRosterService;
import org.twinlife.twinlife.SignedRosterGroup;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.conversation.GroupProtocol;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Group;

import java.util.ArrayList;
import java.util.List;

//
// Executor and observers are running in the SingleThreadExecutor provided by the twinlife library
// Observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.1
//
// User foreground operation: must be connected with a timeout if connection does not work.

public class UpdateGroupPermissionsExecutor extends AbstractTimeoutTwinmeExecutor {
    private static final String LOG_TAG = "UpdateGroupPerm..";
    private static final boolean DEBUG = false;

    private static final int UPDATE_GROUP_TWINCODE_OUTBOUND = 1;
    private static final int UPDATE_GROUP_TWINCODE_OUTBOUND_DONE = 1 << 1;
    private static final int LIST_SECURE_ROSTER = 1 << 2;
    private static final int LIST_SECURE_ROSTER_DONE = 1 << 3;
    private static final int UPDATE_MEMBER_PERMISSIONS = 1 << 4;
    private static final int UPDATE_MEMBER_PERMISSIONS_DONE = 1 << 5;

    @NonNull
    private final Group mGroup;
    @Nullable
    private final TwincodeOutbound mGroupTwincodeOutbound;
    private final TwincodeOutbound mMemberTwincodeOutbound;
    @Nullable
    private final RosterId mRosterId;
    private final Permission mJoinPermissions;
    @Nullable
    private List<SecureRosterService.MemberIdentity> mMembers;

    public UpdateGroupPermissionsExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull Group group,
                               @NonNull Permission memberPermissions) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "UpdateGroupPermissionsExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId +
                    " group=" + group + " memberPermissions=" + memberPermissions);
        }

        mGroup = group;
        mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.PARAMETER, mGroup, 133);

        mGroupTwincodeOutbound = group.getGroupTwincodeOutbound();
        mMemberTwincodeOutbound = group.getTwincodeOutbound();
        mRosterId = GroupProtocol.getSecureRosterId(mGroupTwincodeOutbound);
        mJoinPermissions = memberPermissions;
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            if ((mState & UPDATE_GROUP_TWINCODE_OUTBOUND) != 0 && (mState & UPDATE_GROUP_TWINCODE_OUTBOUND_DONE) == 0) {
                mState &= ~UPDATE_GROUP_TWINCODE_OUTBOUND;
            }
            if ((mState & LIST_SECURE_ROSTER) != 0 && (mState & LIST_SECURE_ROSTER_DONE) == 0) {
                mState &= ~LIST_SECURE_ROSTER;
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

        if (mGroupTwincodeOutbound != null) {

            //
            // Step 1: update the group join permissions.
            //
            if ((mState & UPDATE_GROUP_TWINCODE_OUTBOUND) == 0) {
                mState |= UPDATE_GROUP_TWINCODE_OUTBOUND;

                final List<BaseService.AttributeNameValue> attributes = new ArrayList<>();
                GroupProtocol.setJoinPermissions(attributes, mJoinPermissions);
                mTwinmeContextImpl.getTwincodeOutboundService().updateTwincode(mGroupTwincodeOutbound, attributes, null,
                        this::onUpdateGroupTwincodeOutbound);
                return;
            }
            if ((mState & UPDATE_GROUP_TWINCODE_OUTBOUND_DONE) == 0) {
                return;
            }

            //
            // Step 2: list the group members as known by the server.
            //
            if (mRosterId != null) {
                if ((mState & LIST_SECURE_ROSTER) == 0) {
                    mState |= LIST_SECURE_ROSTER;

                    mTwinmeContextImpl.getSecureRosterService().listRoster(mRosterId, 0, this::onListRoster);
                    return;
                }
                if ((mState & LIST_SECURE_ROSTER_DONE) == 0) {
                    return;
                }

                //
                // Step 3: update the member permissions.  The server will trigger the invoke twincode for each member
                // so that they will refresh the group members and their permissions.
                //
                if (mMembers != null) {
                    if ((mState & UPDATE_MEMBER_PERMISSIONS) == 0) {
                        mState |= UPDATE_MEMBER_PERMISSIONS;

                        mTwinmeContextImpl.getSecureRosterService().updateMembers(mRosterId, mGroupTwincodeOutbound, mMembers, this::onUpdatePermissions);
                        return;
                    }
                    if ((mState & UPDATE_MEMBER_PERMISSIONS_DONE) == 0) {
                        return;
                    }
                }
            }
        }

        //
        // Last Step
        //
        mTwinmeContextImpl.onUpdateGroup(mRequestId, mGroup);

        stop();
    }

    private void onUpdateGroupTwincodeOutbound(@NonNull ErrorCode errorCode, @Nullable TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateGroupTwincodeOutbound: errorCode=" + errorCode + " twincodeOutbound=" + twincodeOutbound);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeOutbound == null) {
            onOperationError(UPDATE_GROUP_TWINCODE_OUTBOUND, errorCode, null);
            return;
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_TWINCODE, twincodeOutbound, mGroupTwincodeOutbound);

        mState |= UPDATE_GROUP_TWINCODE_OUTBOUND_DONE;

        mGroup.setGroupTwincodeOutbound(twincodeOutbound);
        onOperation();
    }

    private void onListRoster(@NonNull ErrorCode errorCode, @Nullable SecureRoster secureRoster) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onListRoster: errorCode=" + errorCode + " secureRoster=" + secureRoster);
        }

        if (errorCode != ErrorCode.SUCCESS || secureRoster == null) {
            onOperationError(LIST_SECURE_ROSTER, errorCode, null);
            return;
        }

        mState |= LIST_SECURE_ROSTER_DONE;

        mMembers = new ArrayList<>();
        for (SignedRosterGroup group : secureRoster.getGroups()) {
            for (RosterMember member : group.members) {
                if (member.memberTwincodeId.equals(mMemberTwincodeOutbound.getId())) {
                    mMembers.add(new SecureRosterService.MemberIdentity(member.memberTwincodeId, Permission.ALL_PERMISSIONS, member.publicKey));
                } else {
                    mMembers.add(new SecureRosterService.MemberIdentity(member.memberTwincodeId, mJoinPermissions, member.publicKey));
                }
            }
        }
        onOperation();
    }

    private void onUpdatePermissions(@NonNull ErrorCode errorCode, @Nullable Void unused) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdatePermissions: errorCode=" + errorCode);
        }

        if (errorCode != ErrorCode.SUCCESS) {
            onOperationError(UPDATE_MEMBER_PERMISSIONS, errorCode, null);
            return;
        }

        mState |= UPDATE_MEMBER_PERMISSIONS_DONE;
        onOperation();
    }

    @Override
    protected void onOperationError(int operationId, ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: operationId=" + operationId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        super.onOperationError(operationId, errorCode, errorParameter);
    }
}
