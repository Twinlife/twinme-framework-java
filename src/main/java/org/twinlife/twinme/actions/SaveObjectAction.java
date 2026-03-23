/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.actions;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinme.TwinmeContext;

/**
 * A Twinme action to save the attributes of a repository object.
 */
public class SaveObjectAction extends TwinmeAction {
    private static final String LOG_TAG = "SaveObjectAction";
    private static final boolean DEBUG = false;

    private static final int SAVE_OBJECT = 1;
    private static final int SAVE_OBJECT_DONE = 1 << 1;

    private static final int TIMEOUT = 10000; // 10s

    @NonNull
    private final RepositoryObject mObject;
    private int mState = 0;
    private Consumer mConsumer;

    public interface Consumer {
        void onSaveObjectAction(@NonNull ErrorCode errorCode, @Nullable RepositoryObject object);
    }

    public SaveObjectAction(@NonNull TwinmeContext twinmeContext, @NonNull RepositoryObject object) {
        super(twinmeContext, TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "SaveObjectAction object=" + object);
        }

        mObject = object;
    }

    public SaveObjectAction onResult(final Consumer consumer) {

        mConsumer = consumer;
        return this;
    }

    @Override
    public void onTwinlifeReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeReady");
        }

        onOperation();
    }

    @Override
    protected void onOperation() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOperation");
        }

        // Save the object attributes.
        if ((mState & SAVE_OBJECT) == 0) {
            mState |= SAVE_OBJECT;

            ErrorCode result = mTwinmeContext.getRepositoryService().saveAttributes(mObject);
            if (mConsumer != null) {
                mConsumer.onSaveObjectAction(result, mObject);
                mConsumer = null;
            }
            mState |= SAVE_OBJECT_DONE;
            onFinish();
            return;
        }
        if ((mState & SAVE_OBJECT_DONE) == 0) {
            return;
        }

        onFinish();
    }

    @Override
    protected void fireError(@NonNull ErrorCode errorCode) {
        if (DEBUG) {
            Log.d(LOG_TAG, "fireError errorCode=" + errorCode);
        }

        if (mConsumer != null) {
            mConsumer.onSaveObjectAction(errorCode, null);
        }

        onFinish();
    }
}
