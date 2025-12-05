/*
 *  Copyright (c) 2025 twinlife SA.
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
import org.twinlife.twinlife.ManagementService;
import org.twinlife.twinme.TwinmeContext;

/**
 * A Twinme action to send a feedback and handle some timeout for this operation.
 */
public class FeedbackAction extends TwinmeAction {
    private static final String LOG_TAG = "FeedbackAction";
    private static final boolean DEBUG = false;

    private static final int SEND_FEEDBACK = 1;
    private static final int SEND_FEEDBACK_DONE = 1 << 1;

    private static final int TIMEOUT = 10000; // 10s

    private final long mRequestId;
    private final String mEmail;
    private final String mSubject;
    private final String mDescription;
    private final boolean mSendLogReport;
    private int mState = 0;
    private Consumer mConsumer;

    public interface Consumer {
        void onSendFeedbackAction(@NonNull ErrorCode errorCode);
    }

    public FeedbackAction(@NonNull TwinmeContext twinmeContext, @NonNull String email, @NonNull String subject, @NonNull String description,
                          boolean sendLogReport) {
        super(twinmeContext, TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "FeedbackAction email=" + email);
        }

        mRequestId = newRequestId();
        mEmail = email;
        mSubject = subject;
        mDescription = description;
        mSendLogReport = sendLogReport;
    }

    public FeedbackAction onResult(final Consumer consumer) {

        mConsumer = consumer;
        return this;
    }

    @Override
    protected void onOperation() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOperation");
        }

        if (!mIsOnline) {
            return;
        }
        if ((mState & SEND_FEEDBACK) == 0) {
            mState |= SEND_FEEDBACK;

            if (DEBUG) {
                Log.d(LOG_TAG, "sendFeedback requestId=" + mRequestId + " email=" + mEmail);
            }
            ManagementService managementService = mTwinmeContext.getManagementService();
            String logReport = mSendLogReport ? managementService.getLogReport() : null;
            managementService.sendFeedback(mEmail, mSubject, mDescription, logReport, this::onSendFeedback);
            return;
        }
        if ((mState & SEND_FEEDBACK_DONE) == 0) {
            return;
        }

        onFinish();
    }

    protected void onSendFeedback(@NonNull ErrorCode errorCode, @Nullable Boolean status) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSendFeedback errorCode=" + errorCode);
        }

        mState |= SEND_FEEDBACK_DONE;

        if (errorCode != ErrorCode.SUCCESS) {
            fireError(errorCode);
            return;
        }

        if (mConsumer != null) {
            mConsumer.onSendFeedbackAction(errorCode);
        }

        onOperation();
    }

    @Override
    protected void fireError(@NonNull ErrorCode errorCode) {
        if (DEBUG) {
            Log.d(LOG_TAG, "fireError errorCode=" + errorCode);
        }

        if (mConsumer != null) {
            mConsumer.onSendFeedbackAction(errorCode);
        }

        onFinish();
    }
}
