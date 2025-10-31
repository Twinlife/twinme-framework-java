/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.executors;

import androidx.annotation.NonNull;
import android.util.Log;

import org.twinlife.twinme.TwinmeContextImpl;

/**
 * Abstract Connected Twinme Executor
 *
 * Similar to the `AbstractTwinmeExecutor` but enforces a connection to the twinlife server.
 * - `onTwinlifeReady()` can be overridden to register specific observers.
 *   When overriding it is mandatory to call `super.onTwinlifeReady()` for correct implementation.
 * - `onOperation()` must be implemented to perform the work, it will be called when the executor
 *   is ready to perform some operation.
 */

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.0
//

public abstract class AbstractConnectedTwinmeExecutor extends AbstractTwinmeExecutor {
    private static final String LOG_TAG = "AConnectedExec";
    private static final boolean DEBUG = false;

    public AbstractConnectedTwinmeExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull String tag) {
        super(twinmeContextImpl, requestId, tag);
        if (DEBUG) {
            Log.d(LOG_TAG, "AConnectedExec: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId);
        }
    }

    //
    // Protected methods
    //

    @Override
    public void onTwinlifeReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeReady");
        }

        // Do not start the operation: wait for the onTwinlifeOnline().
        if (!mTwinmeContextImpl.isConnected()) {
            mTwinmeContextImpl.connect();
        }
    }

    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        mRestarted = false;
        onOperation();
    }
}
