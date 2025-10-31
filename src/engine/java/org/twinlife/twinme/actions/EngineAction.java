/*
 *  Copyright (c) 2021 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.actions;

import android.util.Log;
import androidx.annotation.NonNull;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.ConversationService.Conversation;
import org.twinlife.twinlife.ConversationService.Descriptor;
import org.twinlife.twinlife.ConversationService.TransientObjectDescriptor;
import org.twinlife.twinme.TwinmeContext;
import org.twinlife.twinme.models.Contact;
import org.twinlife.twinme.models.EngineCommand;
import org.twinlife.twinme.models.EngineCommandResult;

/**
 * Base class of supervision engine actions.
 */
public abstract class EngineAction<Result extends EngineCommandResult> extends MessageAction {
    private static final String LOG_TAG = "EngineAction";
    private static final boolean DEBUG = false;

    private static final int ENGINE_TIMEOUT = 20000;

    public interface Consumer<T> {
        void accept(@NonNull EngineAction<?> action, @NonNull T object);
    }

    private final long mCommandId;
    private Consumer<Result> mConsumer;

    public EngineAction<Result> onResult(final Consumer<Result> consumer) {

        this.mConsumer = consumer;
        return this;
    }

    protected EngineAction(@NonNull TwinmeContext twinmeContext, @NonNull Contact contact,
                           @NonNull EngineCommand command) {
        super(twinmeContext, contact, command, MessageAction.Mode.COMMAND, ENGINE_TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineAction");
        }

        mCommandId = command.getRequestId();
    }

    protected void onPopDescriptor(@NonNull Conversation conversation, @NonNull Descriptor descriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onPopDescriptor");
        }

        if (!(descriptor instanceof TransientObjectDescriptor)) {
            return;
        }

        TransientObjectDescriptor command = (TransientObjectDescriptor) descriptor;
        Object object = command.getObject();

        if (!(object instanceof EngineCommandResult)) {
            return;
        }

        EngineCommandResult response = (EngineCommandResult)object;
        if (response.getRequestId() != mCommandId) {
            return;
        }

        onResponse(response);
        onFinish();
    }

    protected void onResponse(@NonNull EngineCommandResult response) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onResponse response=" + response);
        }

        switch (response.getStatus()) {
            case SUCCESS:
                if (mConsumer != null) {
                    mConsumer.accept(this, (Result) response);
                }
                return;

            case ITEM_NOT_FOUND:
                fireError(ErrorCode.ITEM_NOT_FOUND);
                return;

            case ERROR:
                fireError(ErrorCode.SERVER_ERROR);
                return;

            case BAD_COMMAND:
                fireError(ErrorCode.BAD_REQUEST);
                return;

            case PERMISSION_DENIED:
                fireError(ErrorCode.NO_PERMISSION);
                return;

            case NO_SPACE:
                fireError(ErrorCode.NO_STORAGE_SPACE);
                return;

            case UNKNOWN_ENGINE_CLASS:
                fireError(ErrorCode.FEATURE_NOT_SUPPORTED_BY_PEER);
                return;
        }
    }
}
