/*
 *  Copyright (c) 2015-2024 twinlife SA.
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

import org.twinlife.twinlife.AssertPoint;
import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TwincodeInvocation;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.conversation.GroupProtocol;
import org.twinlife.twinlife.util.Utils;
import org.twinlife.twinme.TwinmeContext;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Contact;
import org.twinlife.twinme.models.AccountMigration;
import org.twinlife.twinme.models.Group;
import org.twinlife.twinme.models.GroupRegisteredInvocation;
import org.twinlife.twinme.models.GroupSubscribeInvocation;
import org.twinlife.twinme.models.Invitation;
import org.twinlife.twinme.models.Invocation;
import org.twinlife.twinme.models.PairBindInvocation;
import org.twinlife.twinme.models.PairInviteInvocation;
import org.twinlife.twinme.models.PairProtocol;
import org.twinlife.twinme.models.PairRefreshInvocation;
import org.twinlife.twinme.models.PairUnbindInvocation;
import org.twinlife.twinme.models.Profile;

import java.util.List;
import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.15
//

public class ProcessInvocationExecutor extends AbstractTwinmeExecutor {
    private static final String LOG_TAG = "ProcessInvocationExe...";
    private static final boolean DEBUG = false;

    private static final int GET_TWINCODE_OUTBOUND = 1 << 2;
    private static final int GET_TWINCODE_OUTBOUND_DONE = 1 << 3;

    @NonNull
    private final TwincodeInvocation mInvocation;
    @NonNull
    private final TwinmeContext.ConsumerWithError<Invocation> mConsumer;
    @NonNull
    private final RepositoryObject mReceiver;
    @Nullable
    private UUID mPeerTwincodeId;
    @Nullable
    private TwincodeOutbound mPeerTwincodeOutbound;
    private boolean mPairInviteAction = false;
    private boolean mPairBindAction = false;
    private boolean mPairUnbindAction = false;
    private boolean mPairRefreshAction = false;
    private boolean mGroupSubscribeAction = false;
    private boolean mGroupRegisteredAction = false;
    private long mPermissions;
    private long mAdminPermissions;

    public ProcessInvocationExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, @NonNull TwincodeInvocation invocation,
                                     @NonNull TwinmeContext.ConsumerWithError<Invocation> consumer) {
        super(twinmeContextImpl, BaseService.DEFAULT_REQUEST_ID, LOG_TAG);
        if (DEBUG) {
            Log.d(LOG_TAG, "ProcessInvocationExecutor: twinmeContextImpl=" + twinmeContextImpl +
                    " invocation=" + invocation);
        }

        mInvocation = invocation;
        mConsumer = consumer;
        mReceiver = invocation.subject;

        final String action = invocation.action;
        final List<AttributeNameValue> attributes = invocation.attributes;
        if (PairProtocol.ACTION_PAIR_INVITE.equals(invocation.action)) {
            mPairInviteAction = true;
            if (invocation.attributes != null) {
                for (AttributeNameValue attribute : attributes) {
                    if (PairProtocol.invokeTwincodeActionPairInviteAttributeTwincodeId().equals(attribute.name)) {
                        mPeerTwincodeId = Utils.UUIDFromString((String) attribute.value);
                    }
                }
            }
        } else if (PairProtocol.ACTION_PAIR_BIND.equals(action)) {
            mPairBindAction = true;
            if (attributes != null) {
                for (AttributeNameValue attribute : attributes) {
                    if (PairProtocol.invokeTwincodeActionPairBindAttributeTwincodeId().equals(attribute.name)) {
                        mPeerTwincodeId = Utils.UUIDFromString((String) attribute.value);
                    }
                }
            }
        } else if (PairProtocol.ACTION_PAIR_UNBIND.equals(action)) {
            mPairUnbindAction = true;
        } else if (PairProtocol.ACTION_PAIR_REFRESH.equals(action)) {
            mPairRefreshAction = true;
        } else if (GroupProtocol.ACTION_GROUP_SUBSCRIBE.equals(action)) {
            mGroupSubscribeAction = true;
            if (attributes != null) {
                for (AttributeNameValue attribute : attributes) {
                    if (GroupProtocol.invokeTwincodeActionMemberTwincodeOutboundId().equals(attribute.name)) {
                        mPeerTwincodeId = Utils.UUIDFromString((String) attribute.value);
                    }
                }
            }
        } else if (GroupProtocol.ACTION_GROUP_REGISTERED.equals(action)) {
            mGroupRegisteredAction = true;
            if (attributes != null) {
                for (AttributeNameValue attribute : attributes) {
                    if (GroupProtocol.getInvokeTwincodeAdminTwincodeId().equals(attribute.name)) {
                        mPeerTwincodeId = Utils.UUIDFromString((String) attribute.value);
                    } else if (GroupProtocol.getInvokeTwincodeAdminPermissions().equals(attribute.name)) {
                        mAdminPermissions = Long.parseLong((String) attribute.value);
                    } else if (GroupProtocol.getInvokeTwincodeMemberPermissions().equals(attribute.name)) {
                        mPermissions = Long.parseLong((String) attribute.value);
                    }
                }
            }
        }
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            mRestarted = false;

            if ((mState & GET_TWINCODE_OUTBOUND) != 0 && (mState & GET_TWINCODE_OUTBOUND_DONE) == 0) {
                mState &= ~GET_TWINCODE_OUTBOUND;
            }
        }
        onOperation();
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
        // Step 2
        //

        if (mPeerTwincodeId != null) {
            if ((mState & GET_TWINCODE_OUTBOUND) == 0) {
                mState |= GET_TWINCODE_OUTBOUND;

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeOutboundService.getTwincode: twincodeOutboundId=" + mPeerTwincodeId);
                }
                if (mInvocation.publicKey != null) {
                    mTwinmeContextImpl.getTwincodeOutboundService().getSignedTwincodeWithSecret(mPeerTwincodeId, mInvocation.publicKey,
                            mInvocation.keyIndex, mInvocation.secretKey, mInvocation.trustMethod, this::onGetTwincodeOutbound);
                } else {
                    mTwinmeContextImpl.getTwincodeOutboundService().getTwincode(mPeerTwincodeId, TwincodeOutboundService.LONG_REFRESH_PERIOD,
                            this::onGetTwincodeOutbound);
                }
                return;
            }
            if ((mState & GET_TWINCODE_OUTBOUND_DONE) == 0) {
                return;
            }
        }

        //
        // Last Step
        //

        // Mark the executor as stopped before calling the result method either fireOnError() or onProcessInvocation().
        stop();

        final Invocation invocation;
        final ErrorCode errorCode;
        if (mPairInviteAction) {
            if (mPeerTwincodeId == null || mPeerTwincodeOutbound == null) {
                errorCode = ErrorCode.EXPIRED;
                invocation = null;
            } else if (acceptInvite()) {
                errorCode = ErrorCode.SUCCESS;
                invocation = new PairInviteInvocation(mInvocation.invocationId, mReceiver, mPeerTwincodeOutbound);
            } else {
                errorCode = ErrorCode.BAD_REQUEST;
                invocation = null;
                mTwinmeContextImpl.assertion(ExecutorAssertPoint.PROCESS_INVOCATION, AssertPoint.create(mInvocation));
            }
        } else if (mPairBindAction) {
            if (mPeerTwincodeId == null || mPeerTwincodeOutbound == null) {
                errorCode = ErrorCode.SUCCESS;
                invocation = new PairUnbindInvocation(mInvocation.invocationId, mReceiver);
            } else if (acceptBind()) {
                errorCode = ErrorCode.SUCCESS;
                invocation = new PairBindInvocation(mInvocation.invocationId, mReceiver, mPeerTwincodeOutbound);
            } else {
                errorCode = ErrorCode.BAD_REQUEST;
                invocation = null;
            }
        } else if (mPairUnbindAction) {
            if (acceptBind()) {
                errorCode = ErrorCode.SUCCESS;
                invocation = new PairUnbindInvocation(mInvocation.invocationId, mReceiver);
            } else {
                errorCode = ErrorCode.BAD_REQUEST;
                invocation = null;
            }
        } else if (mPairRefreshAction) {
            if (acceptBind()) {
                errorCode = ErrorCode.SUCCESS;
                invocation = new PairRefreshInvocation(mInvocation.invocationId, mReceiver, mInvocation.attributes);
            } else {
                errorCode = ErrorCode.BAD_REQUEST;
                invocation = null;
            }
        } else if (mGroupSubscribeAction) {
            if (mReceiver instanceof Invitation) {
                errorCode = ErrorCode.SUCCESS;
                invocation = new GroupSubscribeInvocation(mInvocation.invocationId, mReceiver, mPeerTwincodeOutbound);
            } else {
                errorCode = ErrorCode.BAD_REQUEST;
                invocation = null;
            }
        } else if (mGroupRegisteredAction) {
            if (mReceiver instanceof Group) {
                errorCode = ErrorCode.SUCCESS;
                invocation = new GroupRegisteredInvocation(mInvocation.invocationId, mReceiver, mPeerTwincodeOutbound, mAdminPermissions, mPermissions);
            } else {
                errorCode = ErrorCode.BAD_REQUEST;
                invocation = null;
            }
        } else {
            errorCode = ErrorCode.BAD_REQUEST;
            invocation = null;
        }
        mConsumer.onGet(errorCode, invocation);
    }

    private boolean acceptInvite() {

        return (mReceiver instanceof Profile) || (mReceiver instanceof Invitation)
                || (mReceiver instanceof AccountMigration);
    }

    private boolean acceptBind() {

        return (mReceiver instanceof Contact) || (mReceiver instanceof Group)
                || (mReceiver instanceof AccountMigration);
    }

    private void onGetTwincodeOutbound(@NonNull ErrorCode errorCode, @Nullable TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetTwincodeOutbound: errorCode=" + errorCode + " twincodeOutbound=" + twincodeOutbound);
        }

        if (errorCode == ErrorCode.TWINLIFE_OFFLINE) {
            mRestarted = true;
            return;

        } else if (errorCode == ErrorCode.ITEM_NOT_FOUND) {
            // The receiver was found for this invocation but the twincode associated with the invocation
            // is now obsolete: this invocation has expired.
            mConsumer.onGet(ErrorCode.EXPIRED, null);
            stop();
            return;

        } else if (twincodeOutbound == null) {
            mConsumer.onGet(ErrorCode.BAD_REQUEST, null);
            stop();
            return;
        }

        mState |= GET_TWINCODE_OUTBOUND_DONE;
        mPeerTwincodeOutbound = twincodeOutbound;
        onOperation();
    }
}
