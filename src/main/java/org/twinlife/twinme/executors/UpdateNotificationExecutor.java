/*
 *  Copyright (c) 2017-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.executors;

import androidx.annotation.NonNull;
import android.util.Log;

import org.twinlife.twinlife.Notification;
import org.twinlife.twinme.TwinmeContextImpl;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.2
//

public class UpdateNotificationExecutor extends AbstractTwinmeExecutor {
    private static final String LOG_TAG = "UpdateNotificationEx...";
    private static final boolean DEBUG = false;

    @NonNull
    private final Notification mNotification;

    public UpdateNotificationExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull Notification notification) {
        super(twinmeContextImpl, requestId, LOG_TAG);
        if (DEBUG) {
            Log.d(LOG_TAG, "UpdateNotificationExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId + " notification=" + notification);
        }

        mNotification = notification;
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
        // Step 1: update the notification (the update is done immediately, no need for any observer).
        //

        if (DEBUG) {
            Log.d(LOG_TAG, "NotificationService.acknowledgeNotification: notification=" + mNotification);
        }
        mTwinmeContextImpl.getNotificationService().acknowledgeNotification(mNotification);

        //
        // Last Step
        //

        mTwinmeContextImpl.onUpdateNotification(mRequestId, mNotification);

        stop();
    }
}
