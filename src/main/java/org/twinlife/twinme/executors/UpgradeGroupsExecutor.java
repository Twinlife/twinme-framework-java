/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.executors;

import static org.twinlife.twinlife.BaseService.DEFAULT_REQUEST_ID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.AssertPoint;
import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.GroupConversation;
import org.twinlife.twinlife.Filter;
import org.twinlife.twinlife.RosterId;
import org.twinlife.twinlife.SecureRosterService;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Group;
import org.twinlife.twinme.util.TwinmeAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.1
//
// User foreground operation: must be connected with a timeout if connection does not work.

/**
 * Executor to upgrade groups and create the secure roster and register current group members.
 */
public class UpgradeGroupsExecutor extends AbstractConnectedTwinmeExecutor {
    private static final String LOG_TAG = "UpgradeGroupsExe..";
    private static final boolean DEBUG = false;

    private static final int SYNC_TWINCODES = 1;
    private static final int SYNC_TWINCODES_DONE = 1 << 1;
    private static final int GET_GROUPS = 1 << 2;
    private static final int GET_GROUPS_DONE = 1 << 3;
    private static final int CREATE_PRIVATE_KEY = 1 << 4;
    private static final int CREATE_PRIVATE_KEY_DONE = 1 << 5;
    private static final int CREATE_ROSTER = 1 << 6;
    private static final int CREATE_ROSTER_DONE = 1 << 7;
    private static final int ADD_ROSTER_KEY = 1 << 8;
    private static final int ADD_ROSTER_KEY_DONE = 1 << 9;
    private static final int ADD_ROSTER_MEMBER = 1 << 10;
    private static final int ADD_ROSTER_MEMBER_DONE = 1 << 11;
    private static final int UPDATE_GROUP_TWINCODE = 1 << 12;
    private static final int UPDATE_GROUP_TWINCODE_DONE = 1 << 13;

    @Nullable
    private List<Group> mGroups;
    @Nullable
    private Group mGroup;
    @Nullable
    private RosterId mSecureRosterId;
    @Nullable
    private TwincodeOutbound mGroupTwincodeOutbound;
    @Nullable
    private GroupConversation mGroupConversation;

    public UpgradeGroupsExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId) {
        super(twinmeContextImpl, requestId, LOG_TAG);
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            if ((mState & SYNC_TWINCODES) != 0 && (mState & SYNC_TWINCODES_DONE) == 0) {
                mState &= ~SYNC_TWINCODES;
            }
            if ((mState & CREATE_PRIVATE_KEY) != 0 && (mState & CREATE_PRIVATE_KEY_DONE) == 0) {
                mState &= ~CREATE_PRIVATE_KEY;
            }
            if ((mState & CREATE_ROSTER) != 0 && (mState & CREATE_ROSTER_DONE) == 0) {
                mState &= ~CREATE_ROSTER;
            }
            if ((mState & ADD_ROSTER_KEY) != 0 && (mState & ADD_ROSTER_KEY_DONE) == 0) {
                mState &= ~ADD_ROSTER_KEY;
            }
            if ((mState & ADD_ROSTER_MEMBER) != 0 && (mState & ADD_ROSTER_MEMBER_DONE) == 0) {
                mState &= ~ADD_ROSTER_MEMBER;
            }
            if ((mState & UPDATE_GROUP_TWINCODE) != 0 && (mState & UPDATE_GROUP_TWINCODE_DONE) == 0) {
                mState &= ~UPDATE_GROUP_TWINCODE;
            }
        }
        super.onTwinlifeOnline();
    }

    //
    // Private methods
    //

    private boolean hasLegacyMembers() {
        if (DEBUG) {
            Log.d(LOG_TAG, "hasLegacyMembers " + mGroupTwincodeOutbound);
        }

        if (mGroupConversation == null) {
            return false;
        }

        List<ConversationService.GroupMemberConversation> groupMembers = mGroupConversation.getGroupMembers(ConversationService.MemberFilter.ALL_MEMBERS);
        for (ConversationService.GroupMemberConversation groupMember : groupMembers) {
            final TwincodeOutbound peerTwincodeOutbound = groupMember.getPeerTwincodeOutbound();
            if (peerTwincodeOutbound != null && !peerTwincodeOutbound.isSigned()) {
                return true;
            }
        }
        return false;
    }

    protected void onOperation() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOperation");
        }

        if (mStopped) {

            return;
        }

        if ((mState & SYNC_TWINCODES) == 0) {
            mState |= SYNC_TWINCODES;
            mTwinmeContextImpl.getTwincodeInboundService().syncTwincodes(this::onSyncTwincodes);
            return;
        }
        if ((mState & SYNC_TWINCODES_DONE) == 0) {
            return;
        }

        if ((mState & GET_GROUPS) == 0) {
            mState |= GET_GROUPS;

            mTwinmeContextImpl.findGroups(new Filter<>(null), this::onFindGroups);
            return;
        }
        if ((mState & GET_GROUPS_DONE) == 0) {
            return;
        }

        if (mGroupTwincodeOutbound != null) {
            // If the group twincode does not have a private/public key create one.
            if ((mState & CREATE_PRIVATE_KEY) == 0) {
                mState |= CREATE_PRIVATE_KEY;

                if (!mGroupTwincodeOutbound.isSigned()) {
                    TwincodeInbound twincodeInbound = mTwinmeContextImpl.getTwincodeInboundService().getTwincodeInbound(mGroupTwincodeOutbound);
                    if (twincodeInbound != null) {
                        mTwinmeContextImpl.getTwincodeOutboundService().createPrivateKey(twincodeInbound, this::onCreatePrivateKey);
                        return;
                    }
                }
                mState |= CREATE_PRIVATE_KEY_DONE;
            }
            if ((mState & CREATE_PRIVATE_KEY_DONE) == 0) {
                return;
            }

            //
            // Step 1: create the secure roster for the group:
            // - if we have group members that don't have a public key, use the LEGACY_SCHEMA_ID
            // - for others use the normal group schema and don't allow empty keys.
            //
            if ((mState & CREATE_ROSTER) == 0) {
                mState |= CREATE_ROSTER;

                final boolean hasLegacyMembers = hasLegacyMembers();
                final UUID schemaId = hasLegacyMembers ? Group.LEGACY_SCHEMA_ID : Group.SCHEMA_ID;
                final int createOptions = hasLegacyMembers ? SecureRosterService.ALLOW_EMPTY_KEY : 0;
                mTwinmeContextImpl.getSecureRosterService().createRoster(createOptions, schemaId, mGroupTwincodeOutbound, this::onCreateRoster);
                return;
            }
            if ((mState & CREATE_ROSTER_DONE) == 0) {
                return;
            }

            //
            // Step 2: register our public key to sign members.
            //
            if (mSecureRosterId != null) {
                if ((mState & ADD_ROSTER_KEY) == 0) {
                    mState |= ADD_ROSTER_KEY;
                    mTwinmeContextImpl.getSecureRosterService().setRosterPublicKey(mSecureRosterId, mGroupTwincodeOutbound, mGroupTwincodeOutbound, this::onSetRosterPublicKey);
                    return;
                }
                if ((mState & ADD_ROSTER_KEY_DONE) == 0) {
                    return;
                }

                //
                // Step 3: register every member that we know.
                //
                if ((mState & ADD_ROSTER_MEMBER) == 0) {
                    mState |= ADD_ROSTER_MEMBER;
                    if (mGroupConversation != null) {
                        mTwinmeContextImpl.getSecureRosterService().addMembers(mSecureRosterId, mGroupTwincodeOutbound, mGroupConversation, this::onAddMember);
                        return;
                    }
                    mState |= ADD_ROSTER_MEMBER_DONE;
                }
                if ((mState & ADD_ROSTER_MEMBER_DONE) == 0) {
                    return;
                }

                //
                // Step 4: save the roster ID in the group twincode.
                //
                if ((mState & UPDATE_GROUP_TWINCODE) == 0) {
                    mState |= UPDATE_GROUP_TWINCODE;

                    final List<BaseService.AttributeNameValue> twincodeOutboundAttributes = new ArrayList<>();
                    TwinmeAttributes.setTwincodeAttributeRosterId(twincodeOutboundAttributes, mSecureRosterId);

                    if (DEBUG) {
                        Log.d(LOG_TAG, "updateTwincode: twincodeOutboundAttributes=" + twincodeOutboundAttributes);
                    }
                    mTwinmeContextImpl.getTwincodeOutboundService().updateTwincode(mGroupTwincodeOutbound,
                            twincodeOutboundAttributes, null, this::onUpdateGroupTwincode);
                    return;
                }
                if ((mState & UPDATE_GROUP_TWINCODE_DONE) == 0) {
                    return;
                }
            }
        }

        mTwinmeContextImpl.finishMigration(TwinlifeImpl.GROUP_SECURE_ROSTER_MIGRATION);
        stop();
    }

    private void onSyncTwincodes(@NonNull ErrorCode errorCode, @Nullable Void unused) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSyncTwincodes: errorCode=" + errorCode);
        }

        if (errorCode != ErrorCode.SUCCESS) {
            onOperationError(SYNC_TWINCODES, errorCode, null);
            return;
        }

        mState |= SYNC_TWINCODES_DONE;
        onOperation();
    }

    private void onFindGroups(@Nullable List<Group> groups) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onFindGroups: groups=" + groups);
        }

        mState |= GET_GROUPS_DONE;
        mGroups = groups;
        nextGroup();
        onOperation();
    }

    private void onCreatePrivateKey(@NonNull ErrorCode errorCode, @Nullable TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreatePrivateKey: errorCode=" + errorCode + " twincodeOutbound=" + twincodeOutbound);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeOutbound == null) {

            onOperationError(CREATE_PRIVATE_KEY, errorCode, null);
            return;
        }

        mState |= CREATE_PRIVATE_KEY_DONE;;
        onOperation();
    }

    private void onCreateRoster(@NonNull ErrorCode errorCode, @Nullable RosterId rosterId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateRoster: errorCode=" + errorCode + " rosterId=" + rosterId);
        }

        if (errorCode != ErrorCode.SUCCESS || rosterId == null) {

            onOperationError(CREATE_ROSTER, errorCode, null);
            return;
        }

        mState |= CREATE_ROSTER_DONE;
        mSecureRosterId = rosterId;
        onOperation();
    }

    private void onSetRosterPublicKey(@NonNull ErrorCode errorCode, @Nullable Void unused) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSetRosterPublicKey: errorCode=" + errorCode);
        }

        if (errorCode != ErrorCode.SUCCESS) {

            onOperationError(ADD_ROSTER_KEY, errorCode, null);
            return;
        }

        mState |= ADD_ROSTER_KEY_DONE;
        onOperation();
    }

    private void onAddMember(@NonNull ErrorCode errorCode, @Nullable Void unused) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onAddMember: errorCode=" + errorCode);
        }

        if (errorCode != ErrorCode.SUCCESS) {

            onOperationError(ADD_ROSTER_MEMBER, errorCode, null);
            return;
        }

        mState |= ADD_ROSTER_MEMBER_DONE;
        onOperation();
    }

    private void onUpdateGroupTwincode(@NonNull ErrorCode errorCode, @Nullable TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateGroupTwincode: errorCode=" + errorCode + " twincodeOutbound=" + twincodeOutbound);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeOutbound == null) {

            onOperationError(UPDATE_GROUP_TWINCODE, errorCode, null);
            return;
        }

        mState |= UPDATE_GROUP_TWINCODE_DONE;
        nextGroup();
        onOperation();
    }

    private void nextGroup() {

        if (mGroups != null) {
            while (!mGroups.isEmpty()) {

                mGroup = mGroups.remove(mGroups.size() - 1);
                // Only migrate the group that we created.
                if (mGroup.getGroupTwincodeFactoryId() != null) {
                    mGroupTwincodeOutbound = mGroup.getGroupTwincodeOutbound();
                    mGroupConversation = mGroupTwincodeOutbound == null ? null : mTwinmeContextImpl.getConversationService().getGroupConversationWithGroupTwincodeId(mGroupTwincodeOutbound.getId());
                    if (mGroupTwincodeOutbound == null || mGroupConversation == null) {
                        mTwinmeContextImpl.deleteGroup(DEFAULT_REQUEST_ID, mGroup);
                    } else if (mGroup.getSecureRosterId() == null) {
                        mSecureRosterId = null;
                        mState = GET_GROUPS | GET_GROUPS_DONE;
                        return;
                    }
                }
            }
        }

        mGroup = null;
        mGroupTwincodeOutbound = null;
        mSecureRosterId = null;
        mGroupConversation = null;
    }

    protected void onOperationError(int operationId, BaseService.ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: operationId=" + operationId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        // Wait for reconnection
        if (errorCode.isTransient()) {
            mRestarted = true;
            return;
        }

        // If we fail to update the group twincode because the twincode no longer exist, delete this group.
        if (errorCode == ErrorCode.ITEM_NOT_FOUND || errorCode == ErrorCode.EXPIRED) {
            if (mGroup != null) {
                mTwinmeContextImpl.deleteGroup(DEFAULT_REQUEST_ID, mGroup);
            }
            nextGroup();
            onOperation();
            return;
        }

        // If we failed to created/have a public key for the group twincode, the creation of secure roster cannot be made.
        if (errorCode == ErrorCode.NO_PUBLIC_KEY && operationId == CREATE_ROSTER) {
            mTwinmeContextImpl.assertion(ExecutorAssertPoint.UPGRADE_GROUP_NO_PUBLIC_KEY, AssertPoint.create(mGroupTwincodeOutbound));

            nextGroup();
            onOperation();
            return;
        }

        mTwinmeContextImpl.assertion(ExecutorAssertPoint.UPGRADE_GROUP_ERROR, AssertPoint.create(mGroup).put(errorCode).put(operationId));
        super.onOperationError(operationId, errorCode, errorParameter);
    }
}
