/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme;

import static org.twinlife.twinlife.BaseService.DEFAULT_REQUEST_ID;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.TwincodeInboundService;
import org.twinlife.twinlife.TwincodeInvocation;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.Twinlife;
import org.twinlife.twinlife.conversation.GroupProtocol;
import org.twinlife.twinlife.util.EventMonitor;
import org.twinlife.twinme.executors.RefreshRosterExecutor;
import org.twinlife.twinme.executors.UpgradeGroupsExecutor;
import org.twinlife.twinme.models.Group;

import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Manages orchestration of secure roster with refresh and leave.
 * Handles the following invocations:
 * - roster::update
 * - roster::leave
 * Handles group cleaning when the group conversation is deleted.
 */
final class SecureRosterOrchestrator extends ConversationService.DefaultServiceObserver implements TwincodeInboundService.InvocationListener {
    private static final String LOG_TAG = "SecureRosterOrch..";
    private static final boolean DEBUG = false;

    private final TwinmeContextImpl mTwinmeContext;
    private final ExecutorService mTwinlifeExecutor;

    SecureRosterOrchestrator(@NonNull TwinmeContextImpl twinmeContext, @NonNull ExecutorService twinlifeExecutor) {

        mTwinmeContext = twinmeContext;
        mTwinlifeExecutor = twinlifeExecutor;
    }

    void onTwinlifeReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeReady");
        }

        final TwincodeInboundService twincodeInboundService = mTwinmeContext.getTwincodeInboundService();

        twincodeInboundService.addListener(GroupProtocol.ACTION_ROSTER_UPDATE, this);
        twincodeInboundService.addListener(GroupProtocol.ACTION_ROSTER_LEAVE, this);

        mTwinmeContext.getConversationService().addServiceObserver(this);

        // If the group secure roster migration is necessary execute it.  This step can be repeated
        // until the upgrade groups executor finishes.
        if (mTwinmeContext.needMigration(Twinlife.GROUP_SECURE_ROSTER_MIGRATION)) {
            final UpgradeGroupsExecutor upgradeGroupsExecutor = new UpgradeGroupsExecutor(mTwinmeContext, mTwinmeContext.newRequestId());
            upgradeGroupsExecutor.start();
        }
    }

    /**
     * Group invitation step 3: we are now member of the group, get the group information before the notification.
     *
     * @param conversation the group conversation.
     * @param invitation   the invitation.
     */
    @Override
    public void onJoinGroupResponse(long requestId, @NonNull ConversationService.GroupConversation conversation, @Nullable ConversationService.InvitationDescriptor invitation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onJoinGroupResponse: requestId=" + requestId + " conversation=" + conversation);
        }

        if (invitation != null) {
            mTwinmeContext.getGroup(conversation.getContactId(), (ErrorCode errorCode, Group group) -> {
                if (errorCode == ErrorCode.SUCCESS && group != null) {
                    EventMonitor.info(LOG_TAG, "Now member of ", group.getName(), "(",
                            group.getGroupTwincodeOutboundId(), ") as ", group.getTwincodeOutboundId());

                    mTwinmeContext.getNotificationCenter().onJoinGroup(group, conversation);
                }
            });
        }
    }

    /**
     * A group conversation was removed and we are no longer member of the group.
     * This is either a leaveGroup() operation that we made previously or a leave-group message
     * that we received from another member.
     *
     * @param conversationId the group conversation id.
     * @param groupId        the local group id.
     */
    @Override
    public void onDeleteGroupConversation(@NonNull UUID conversationId, @NonNull UUID groupId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteGroupConversation: conversationId=" + conversationId
                    + " groupId=" + groupId);
        }

        // Get the group to proceed to the final group cleanup.
        mTwinmeContext.getGroup(groupId, (ErrorCode errorCode, Group group) -> {
            // The group conversation was deleted, delete the group object and group member twincode.
            if (errorCode == ErrorCode.SUCCESS && group != null) {
                mTwinmeContext.deleteGroup(mTwinmeContext.newRequestId(), group);
            }
        });
    }

    @Override
    public void onError(long requestId, ErrorCode errorCode, String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: requestId=" + requestId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

    }

    @Override
    @Nullable
    public ErrorCode onInvokeTwincode(@NonNull TwincodeInvocation invocation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onInvokeTwincode: invocation=" + invocation);
        }

        if (GroupProtocol.ACTION_ROSTER_UPDATE.equals(invocation.action)
                || GroupProtocol.ACTION_ROSTER_LEAVE.equals(invocation.action)) {
            if (!(invocation.subject instanceof Group)) {
                return ErrorCode.BAD_REQUEST;
            }

            final Group group = (Group) invocation.subject;
            final TwincodeOutbound groupTwincodeOutbound = group.getGroupTwincodeOutbound();
            if (groupTwincodeOutbound == null) {
                mTwinmeContext.deleteGroup(DEFAULT_REQUEST_ID, group);
                return ErrorCode.SUCCESS;
            }

            final RefreshRosterExecutor refreshRosterExecutor = new RefreshRosterExecutor(mTwinmeContext, group, (ErrorCode errorCode, Void unused) -> {
                mTwinmeContext.acknowledgeInvocation(invocation.invocationId, errorCode);
            });
            mTwinlifeExecutor.execute(refreshRosterExecutor::start);
            return null;
        }
        return null;
    }
}
