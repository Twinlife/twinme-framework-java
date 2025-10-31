/*
 *  Copyright (c) 2014-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme;

import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.ConnectionStatus;
import org.twinlife.twinlife.JobService;
import org.twinlife.twinme.models.Profile;

public interface TwinmeApplication {

    boolean getDisplayNotificationContent();

    @NonNull
    JobService.ProcessingLock allocateProcessingLock();

    @NonNull
    String getApplicationName();

    enum Feature {
        GROUP_CALL
    }

    @Nullable
    TwinmeContext getTwinmeContext();

    boolean isRunning();

    void setNotRunning();

    @NonNull
    ConnectionStatus getConnectionStatus();

            void stop();

    void restart();

    @NonNull
    String getAnonymousName();

    @NonNull
    Bitmap getAnonymousAvatar();

    @NonNull
    Bitmap getDefaultAvatar();

    @NonNull
    Bitmap getDefaultGroupAvatar();

    void setDefaultProfile(@Nullable Profile profile);

    @NonNull
    NotificationCenter newNotificationCenter(@NonNull TwinmeContext twinmeContext);

    /**
     * Check if the feature is enabled for the application.
     *
     * @param feature the feature identification.
     * @return true if the feature is enabled.
     */
    boolean isFeatureSubscribed(@NonNull Feature feature);

    boolean getDisplayNotificationSender();

    boolean screenLocked();
}
