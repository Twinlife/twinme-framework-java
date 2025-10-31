/*
 *  Copyright (c) 2021-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.actions;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.Conversation;
import org.twinlife.twinlife.ConversationService.Descriptor;
import org.twinlife.twinlife.ConversationService.UpdateType;
import org.twinlife.twinme.TwinmeContext;
import org.twinlife.twinme.models.Contact;

import java.util.UUID;

/**
 * A Twinme action that sends a message and optionally waits for a response.
 *
 */
public abstract class MessageAction extends TwinmeAction {
    private static final String LOG_TAG = "MessageAction";
    private static final boolean DEBUG = false;

    enum Mode {
        MESSAGE,
        COMMAND
    }

    // Conversation observer to detect when we send or receive a message.
    public class ConversationObserver extends ConversationService.DefaultServiceObserver {

        @Override
        public void onPushDescriptor(long requestId, @NonNull Conversation conversation,
                                     @NonNull Descriptor descriptor) {

            if (requestId != MessageAction.this.mRequestId) {
                return;
            }

            MessageAction.this.onPushDescriptor(conversation, descriptor);
        }

        @Override
        public void onPopDescriptor(long requestId, @NonNull Conversation conversation,
                                    @NonNull Descriptor descriptor) {

            if (!conversation.getId().equals(mConversationId)) {
                return;
            }

            MessageAction.this.onPopDescriptor(conversation, descriptor);
        }

        @Override
        public void onUpdateDescriptor(long requestId, @NonNull Conversation conversation,
                                       @NonNull Descriptor descriptor, @NonNull UpdateType updateType) {

            if (!conversation.getId().equals(mConversationId)) {
                return;
            }

            MessageAction.this.onUpdateDescriptor(conversation, descriptor, updateType);
        }
    }

    @NonNull
    protected final ConversationObserver mConversationObserver;
    @Nullable
    private ConversationService mConversationService;
    @NonNull
    private final Contact mContact;
    @NonNull
    private final String mMessage;
    @NonNull
    private final Mode mMode;
    private final long mRequestId;
    @Nullable
    private UUID mConversationId;
    @Nullable
    private Conversation mConversation;
    private boolean mNeedConversationId = true;

    public MessageAction(@NonNull TwinmeContext twinmeContext, @NonNull Contact contact,
                         @NonNull String message, @NonNull Mode mode, long timeLimit) {
        super(twinmeContext, timeLimit);
        if (DEBUG) {
            Log.d(LOG_TAG, "MessageAction");
        }

        mContact = contact;
        mMessage = message;
        mMode = mode;
        mConversationObserver = new ConversationObserver();
        mRequestId = newRequestId();
    }

    @NonNull
    public Contact getContact() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getContact");
        }

        return mContact;
    }

    @Override
    public void onTwinlifeReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeReady");
        }

        mConversationService = mTwinmeContext.getConversationService();
        mConversationService.addServiceObserver(mConversationObserver);
        onOperation();
    }

    @Override
    protected void onOperation() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOperation");
        }

        if (mConversationId == null && mConversationService != null && mNeedConversationId) {
            mNeedConversationId = false;

            UUID twincodeOutboundId = mContact.getTwincodeOutboundId();
            UUID peerTwincodeOutboundId = mContact.getPeerTwincodeOutboundId();
            UUID twincodeInboundId = mContact.getTwincodeInboundId();

            if (twincodeInboundId == null || twincodeOutboundId == null) {
                fireError(ErrorCode.BAD_REQUEST);
                return;
            }

            mConversation = mConversationService.getOrCreateConversation(mContact);
            if (mConversation == null) {
                fireError(ErrorCode.ITEM_NOT_FOUND);
            } else if (mMode == Mode.MESSAGE) {
                mConversationId = mConversation.getId();
                mConversationService.pushMessage(mRequestId, mConversation, null, null, mMessage, true, 0);
            } else if (mMode == Mode.COMMAND) {
                mConversationId = mConversation.getId();
                mConversationService.pushCommand(mRequestId, mConversation, mMessage);
            }
        }
    }

    @Override
    protected void onFinish() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onFinish");
        }

        if (mConversationService != null) {
            mConversationService.removeServiceObserver(mConversationObserver);
        }
        super.onFinish();
    }

    protected void onPushDescriptor(@NonNull Conversation conversation, @NonNull Descriptor descriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onPushDescriptor");
        }

        if (mMode == Mode.MESSAGE) {
            onFinish();
            return;
        }

        onOperation();
    }

    protected void onPopDescriptor(@NonNull Conversation conversation, @NonNull Descriptor descriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onPopDescriptor");
        }

        onOperation();
    }

    protected void onUpdateDescriptor(@NonNull Conversation conversation, @NonNull Descriptor descriptor,
                                      @NonNull UpdateType updateType) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateDescriptor");
        }

        onOperation();
    }
}
