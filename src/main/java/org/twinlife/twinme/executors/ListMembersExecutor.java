/*
 *  Copyright (c) 2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.GroupMember;
import org.twinlife.twinme.models.Originator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.1
//

/**
 * Executor to retrieve the identification of a group member from the group member twincode.
 *
 * Do not use the AbstractConnectedExecutor because we rely on the database cache.
 */
public class ListMembersExecutor extends AbstractTwinmeExecutor {
    private static final String LOG_TAG = "ListMembersExecutor";
    private static final boolean DEBUG = false;

    private static final int LIST_MEMBERS = 1;
    private static final int FETCH_MEMBERS = 1 << 1;
    private static final int GET_GROUP_MEMBER = 1 << 2;
    private static final int GET_GROUP_MEMBER_DONE = 1 << 3;

    @NonNull
    private final Originator mSubject;
    @Nullable
    private final ConversationService.MemberFilter mFilter;
    @NonNull
    private final Consumer<List<GroupMember>> mConsumer;
    @NonNull
    private final List<GroupMember> mMembers;
    @NonNull
    private final List<UUID> mUnkownMembers;
    @NonNull
    private final List<UUID> mMemberTwincodes;

    public ListMembersExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, @NonNull Originator subject,
                               @Nullable ConversationService.MemberFilter filter,
                               @Nullable List<UUID> memberTwincodes,
                               @NonNull Consumer<List<GroupMember>> consumer) {
        super(twinmeContextImpl, 0, LOG_TAG);
        if (DEBUG) {
            Log.d(LOG_TAG, "ListGroupMemberExecutor: twinmeContextImpl=" + twinmeContextImpl
                    + " subject=" + subject + " filter=" + filter);
        }

        mSubject = subject;
        mFilter = filter;
        mMembers = new ArrayList<>();
        mUnkownMembers = new ArrayList<>();
        mMemberTwincodes = memberTwincodes == null ? new ArrayList<>() : memberTwincodes;
        mConsumer = consumer;
        mState = memberTwincodes == null ? 0 : LIST_MEMBERS;
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
        // Step 1: get the list of group member conversations.
        //
        if ((mState & LIST_MEMBERS) == 0) {
            mState |= LIST_MEMBERS;

            final ConversationService.Conversation conversation = mTwinmeContextImpl.getConversationService().getConversation(mSubject);
            if (!(conversation instanceof ConversationService.GroupConversation)) {
                mConsumer.onGet(ErrorCode.ITEM_NOT_FOUND, null);
                stop();
                return;
            }

            final ConversationService.GroupConversation groupConversation = (ConversationService.GroupConversation) conversation;
            final List<ConversationService.GroupMemberConversation> memberConversations = groupConversation.getGroupMembers(mFilter);
            for (final ConversationService.GroupMemberConversation groupMemberConversation : memberConversations) {
               mMemberTwincodes.add(groupMemberConversation.getMemberTwincodeOutboundId());
            }
        }

        //
        // Step 2: look in the twinme context cache to get the members associated with the list of twincodes.
        // Fill up the group members which are already known and get a list of unknown members.
        //
        if ((mState & FETCH_MEMBERS) == 0) {
            mState |= FETCH_MEMBERS;

            mTwinmeContextImpl.fetchExistingMembers(mSubject, mMemberTwincodes, mMembers, mUnkownMembers);
        }

        //
        // Step 3: get the group member for the first unknown member until the list becomes empty.
        //
        if (!mUnkownMembers.isEmpty()) {
            if ((mState & GET_GROUP_MEMBER) == 0) {
                mState |= GET_GROUP_MEMBER;

                mTwinmeContextImpl.getGroupMember(mSubject, mUnkownMembers.get(0), this::onGetGroupMember);
                return;
            }
            if ((mState & GET_GROUP_MEMBER_DONE) == 0) {
                return;
            }
        }

        //
        // Last Step
        //
        mConsumer.onGet(ErrorCode.SUCCESS, mMembers);

        stop();
    }

    private void onGetGroupMember(@NonNull ErrorCode status, @Nullable GroupMember groupMember) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetGroupMember: status=" + status + " groupMember=" + groupMember);
        }

        if (status == ErrorCode.SUCCESS && groupMember != null) {
            mMembers.add(groupMember);
        }
        if (!mUnkownMembers.isEmpty()) {
            mUnkownMembers.remove(0);
            mState &= ~GET_GROUP_MEMBER;
        } else {
            mState |= GET_GROUP_MEMBER_DONE;
        }
        onOperation();
    }
}
