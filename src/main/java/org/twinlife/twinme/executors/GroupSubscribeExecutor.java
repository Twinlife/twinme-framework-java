/*
 *  Copyright (c) 2019-2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.Conversation;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinlife.conversation.GroupProtocol;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Group;
import org.twinlife.twinme.models.GroupSubscribeInvocation;
import org.twinlife.twinme.models.Invitation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.3
//

/**
 * Executor to accept a group subscription from a new member.
 */
public class GroupSubscribeExecutor extends AbstractConnectedTwinmeExecutor {
    private static final String LOG_TAG = "GroupSubscribeExecutor";
    private static final boolean DEBUG = false;

    private static final int GET_GROUP = 1;
    private static final int GET_GROUP_DONE = 1 << 1;
    private static final int SUBSCRIBE_MEMBER = 1 << 2;
    private static final int SUBSCRIBE_MEMBER_DONE = 1 << 3;
    private static final int INVOKE_TWINCODE = 1 << 6;
    private static final int INVOKE_TWINCODE_DONE = 1 << 7;

    @NonNull
    private final UUID mInvocationId;
    @NonNull
    private final Invitation mInvitation;
    @NonNull
    private final TwincodeOutbound mMemberTwincode;
    private Group mGroup;
    private UUID mGroupOwnerTwincode;
    private long mAdminPermissions;

    public GroupSubscribeExecutor(@NonNull TwinmeContextImpl twinmeContextImpl,
                                  @NonNull GroupSubscribeInvocation invocation,
                                  @NonNull Invitation invitation) {
        super(twinmeContextImpl, BaseService.DEFAULT_REQUEST_ID, LOG_TAG);
        if (DEBUG) {
            Log.d(LOG_TAG, "GroupSubscribeExecutor: twinmeContextImpl=" + twinmeContextImpl
                    + " invocation=" + invocation);
        }

        mInvocationId = invocation.getId();
        mMemberTwincode = null; // SCz invocation.getTwincodeOutboundId();
        mInvitation = invitation;
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            mRestarted = false;

            if ((mState & INVOKE_TWINCODE) != 0 && (mState & INVOKE_TWINCODE_DONE) == 0) {
                mState &= ~INVOKE_TWINCODE;
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
        // Step 1: get the Group object.
        //

        if (mInvitation.getGroupId() != null) {

            if ((mState & GET_GROUP) == 0) {
                mState |= GET_GROUP;

                mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mInvitation, 119);
                mTwinmeContextImpl.getGroup(mInvitation.getGroupId(), this::onGetGroup);
                return;
            }
            if ((mState & GET_GROUP_DONE) == 0) {
                return;
            }
        }

        //
        // Step 2: add the member in the group.
        //

        if (mGroup != null) {

            if ((mState & SUBSCRIBE_MEMBER) == 0) {
                mState |= SUBSCRIBE_MEMBER | SUBSCRIBE_MEMBER_DONE;

                long requestId = newOperation(SUBSCRIBE_MEMBER);
                if (DEBUG) {
                    Log.d(LOG_TAG, "ConversationService.subscribeGroup: requestId=" + requestId + " memberTwincodeId=" + mMemberTwincode);
                }

                ConversationService conversationService = mTwinmeContextImpl.getConversationService();
                ErrorCode result = conversationService.subscribeGroup(requestId, mGroup, mMemberTwincode, mInvitation.getPermissions());

                if (result == ErrorCode.SUCCESS) {
                    Conversation conversation = conversationService.getConversation(mGroup);

                    if (conversation != null) {
                        mGroupOwnerTwincode = conversation.getTwincodeOutboundId();
                        mAdminPermissions = -1L;
                    }
                }
            }
        }

        //
        // Step 3: send the registered invocation on the group member twincode.
        //

        if ((mState & INVOKE_TWINCODE) == 0) {
            mState |= INVOKE_TWINCODE;

            mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_RESULT, mMemberTwincode, 163);
            List<BaseService.AttributeNameValue> attributes;
            attributes = new ArrayList<>();

            // If we succeeded, send back the admin member's twincode with its permissions and the
            // new member's permission.  If we fail, the registered invocation will get an empty attribute list.
            if (mGroupOwnerTwincode != null) {
                GroupProtocol.setInvokeTwincodeGroupAdminTwincodeId(attributes, mGroupOwnerTwincode);
                GroupProtocol.setInvokeTwincodeGroupAdminPermissions(attributes, mAdminPermissions);
                GroupProtocol.setInvokeTwincodeGroupMemberPermissions(attributes, mInvitation.getPermissions());
            }

            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeOutboundService.invokeTwincode: member=" + mMemberTwincode +
                        " attributes=" + attributes);
            }
            mTwinmeContextImpl.getTwincodeOutboundService().invokeTwincode(mMemberTwincode,
                    TwincodeOutboundService.INVOKE_URGENT, GroupProtocol.ACTION_GROUP_REGISTERED, attributes, this::onInvokeTwincode);
            return;
        }
        if ((mState & INVOKE_TWINCODE_DONE) == 0) {
            return;
        }

        //
        // Last Step
        //
        if (mGroup != null) {
            mTwinmeContextImpl.onUpdateGroup(mRequestId, mGroup);
        }

        stop();
    }

    private void onGetGroup(@NonNull ErrorCode errorCode, @Nullable Group group) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetGroup: group=" + group);
        }

        mState |= GET_GROUP_DONE;
        mGroup = group;
        if (group != null) {
            mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_SUBJECT, group.getId(), mInvitation.getGroupId());
        }

        onOperation();
    }

    private void onInvokeTwincode(@NonNull ErrorCode errorCode, @Nullable UUID invocationId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onInvokeTwincode: invocationId=" + invocationId);
        }

        if (errorCode != ErrorCode.SUCCESS || invocationId == null) {

            onOperationError(INVOKE_TWINCODE, errorCode, null);
            return;
        }

        mState |= INVOKE_TWINCODE_DONE;
        onOperation();
    }

    protected void stop() {
        if (DEBUG) {
            Log.d(LOG_TAG, "stop");
        }

        mTwinmeContextImpl.acknowledgeInvocation(mInvocationId, ErrorCode.SUCCESS);

        super.stop();
    }
}
