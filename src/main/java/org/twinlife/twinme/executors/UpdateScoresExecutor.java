/*
 *  Copyright (c) 2018-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.executors;

import androidx.annotation.NonNull;
import android.util.Log;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.ContactFactory;
import org.twinlife.twinme.models.GroupFactory;

import java.util.List;

//
// Executor and observers are running in the SingleThreadExecutor provided by the twinlife library
// Observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.3
//

public  class UpdateScoresExecutor extends AbstractTwinmeExecutor {
    private static final String LOG_TAG = "UpdateScoresExecutor";
    private static final boolean DEBUG = false;

    private static final int UPDATE_CONTACT_SCORES = 1;
    private static final int UPDATE_CONTACT_SCORES_DONE = 1 << 1;
    private static final int UPDATE_GROUP_SCORES = 1 << 2;
    private static final int UPDATE_GROUP_SCORES_DONE = 1 << 3;

    private final boolean mUpdateScore;
    private List<RepositoryObject> mContacts;
    private List<RepositoryObject> mGroups;

    public UpdateScoresExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, boolean updateScore) {
        super(twinmeContextImpl, requestId, LOG_TAG);
        if (DEBUG) {
            Log.d(LOG_TAG, "UpdateScoresExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId + " updateScore=" + updateScore);
        }

        mUpdateScore = updateScore;
    }

    //
    // Private methods
    //

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        onOperation();
    }

    protected void onOperation() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOperation");
        }

        if (mStopped) {

            return;
        }

        //
        // Step 1: update the contact scores.
        //

        if ((mState & UPDATE_CONTACT_SCORES) == 0) {
            mState |= UPDATE_CONTACT_SCORES;

            mTwinmeContextImpl.getRepositoryService().updateStats(ContactFactory.INSTANCE, mUpdateScore, (BaseService.ErrorCode status, List<RepositoryObject> list) -> {
                mContacts = list;
                mState |= UPDATE_CONTACT_SCORES_DONE;
            });
        }
        if ((mState & UPDATE_CONTACT_SCORES_DONE) == 0) {
            return;
        }

        //
        // Step 2: update the group scores.
        //

        if ((mState & UPDATE_GROUP_SCORES) == 0) {
            mState |= UPDATE_GROUP_SCORES;

            mTwinmeContextImpl.getRepositoryService().updateStats(GroupFactory.INSTANCE, mUpdateScore, (BaseService.ErrorCode status, List<RepositoryObject> list) -> {
                mGroups = list;
                mState |= UPDATE_GROUP_SCORES_DONE;
            });
        }
        if ((mState & UPDATE_GROUP_SCORES_DONE) == 0) {
            return;
        }

        //
        // Last Step
        //

        mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_RESULT, mContacts, 114);
        mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_RESULT, mGroups, 115);

        mTwinmeContextImpl.onUpdateScores(mRequestId, mContacts, mGroups);

        stop();
    }
}
