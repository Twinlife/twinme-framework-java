
/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme;

import android.util.Log;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.TwincodeInboundService;
import org.twinlife.twinlife.TwincodeInvocation;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinme.models.CallReceiver;
import org.twinlife.twinme.models.ConferenceProtocol;

import java.util.concurrent.ExecutorService;

/**
 * Manages orchestration of the conference provided by the CallReceiver configured in CONFERENCE mode.
 * Handles the following invocations:
 * - conference::join,
 * - conference::start,
 * - conference::stop
 */
final class ConferenceOrchestrator implements TwincodeInboundService.InvocationListener {
    private static final String LOG_TAG = "ConferenceOrchestrator";
    private static final boolean DEBUG = false;

    private final TwinmeContextImpl mTwinmeContext;

    ConferenceOrchestrator(@NonNull TwinmeContextImpl twinmeContext, @NonNull ExecutorService twinlifeExecutor) {
        mTwinmeContext = twinmeContext;
    }

    void onTwinlifeReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeReady");
        }

        final TwincodeInboundService twincodeInboundService = mTwinmeContext.getTwincodeInboundService();
        twincodeInboundService.addListener(ConferenceProtocol.ACTION_JOIN, this);
        twincodeInboundService.addListener(ConferenceProtocol.ACTION_START, this);
        twincodeInboundService.addListener(ConferenceProtocol.ACTION_STOP, this);
    }

    @Override
    public ErrorCode onInvokeTwincode(@NonNull TwincodeInvocation invocation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onInvokeTwincode: invocation=" + invocation);
        }

        final TwincodeOutbound twincodeOutbound = invocation.subject.getTwincodeOutbound();
        if (twincodeOutbound == null) {
            return ErrorCode.EXPIRED;
        }
        if (!(invocation.subject instanceof CallReceiver) || invocation.attributes == null) {
            return ErrorCode.BAD_REQUEST;
        }

        final CallReceiver conference = (CallReceiver)invocation.subject;
        if (!conference.hasNotifyJoin()) {
            return ErrorCode.SUCCESS;
        }

        final ConversationService conversationService = mTwinmeContext.getConversationService();
        final long date = AttributeNameValue.getLongAttribute(invocation.attributes, ConferenceProtocol.PARAM_DATE, 0);
        ErrorCode errorCode;
        switch (invocation.action) {
            case ConferenceProtocol.ACTION_JOIN:
                mTwinmeContext.getNotificationCenter().onConferenceEvent(conference, ConferenceEvent.FIRST_JOIN, date);
                errorCode = ErrorCode.SUCCESS;
                break;

            case ConferenceProtocol.ACTION_START:
                errorCode = conversationService.saveCall(conference, date, 0);
                if (errorCode == ErrorCode.SUCCESS) {
                    mTwinmeContext.getNotificationCenter().onConferenceEvent(conference, ConferenceEvent.START, date);
                }
                break;

            case ConferenceProtocol.ACTION_STOP:
                final long startDate = AttributeNameValue.getLongAttribute(invocation.attributes, ConferenceProtocol.PARAM_START, 0);
                errorCode = conversationService.saveCall(conference, startDate, date);
                if (errorCode == ErrorCode.SUCCESS) {
                    mTwinmeContext.getNotificationCenter().onConferenceEvent(conference, ConferenceEvent.STOP, date);
                }
                break;

            default:
                errorCode = ErrorCode.LIBRARY_ERROR;
                break;
        }

        return errorCode;
    }
}
