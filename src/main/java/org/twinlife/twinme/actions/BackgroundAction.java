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

import org.twinlife.twinme.TwinmeContext;

/**
 * A Twinme action to execute some reasonably long background work execution.
 * The method execute() must be overridden and it has to perform the background work.
 * It is running from a dedicated thread shared by background actions.
 */
public abstract class BackgroundAction extends TwinmeAction {
    private static final String LOG_TAG = "BackgroundAction";
    private static final boolean DEBUG = false;

    private static final int RUN_ACTION = 1;
    public static final int DEFAULT_TIMEOUT = 20000;

    private int mState = 0;

    public BackgroundAction(@NonNull TwinmeContext twinmeContext, long timeLimit) {
        super(twinmeContext, timeLimit);
        if (DEBUG) {
            Log.d(LOG_TAG, "BackgroundAction");
        }
    }

    @Override
    public void onTwinlifeReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeReady");
        }

        onOperation();
    }

    /**
     * The background work that must be implemented.
     */
    protected abstract void execute();

    @Override
    protected void onOperation() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOperation");
        }

        // Save the object attributes.
        if ((mState & RUN_ACTION) == 0) {
            mState |= RUN_ACTION;

            mTwinmeContext.executeImage(this::fetchAction);
        }
    }

    private void fetchAction() {

        execute();
        onFinish();
    }
}
