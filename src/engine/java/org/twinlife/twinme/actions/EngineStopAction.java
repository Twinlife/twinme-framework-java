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
import org.twinlife.twinme.TwinmeContext;
import org.twinlife.twinme.models.Contact;
import org.twinlife.twinme.models.EngineCommand;
import org.twinlife.twinme.models.EngineCommandResult;

import java.util.UUID;

/**
 * Engine stop action to stop a Twinroom within an engine.
 */
public class EngineStopAction extends EngineAction<EngineCommandResult> {
    private static final String LOG_TAG = "EngineStopAction";
    private static final boolean DEBUG = false;

    @NonNull
    public static EngineStopAction create(@NonNull TwinmeContext twinmeContext, @NonNull Contact contact,
                                          @NonNull String logicalName) {

        return new EngineStopAction(twinmeContext, contact, logicalName);
    }

    @NonNull
    public static EngineStopAction create(@NonNull TwinmeContext twinmeContext, @NonNull Contact contact,
                                          @NonNull UUID engineId) {

        return new EngineStopAction(twinmeContext, contact, engineId);
    }

    private EngineStopAction(@NonNull TwinmeContext twinmeContext, @NonNull Contact contact,
                             @NonNull String logicalName) {
        super(twinmeContext, contact, EngineCommand.stop(twinmeContext.newRequestId(), logicalName));
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineStopAction");
        }
    }

    private EngineStopAction(@NonNull TwinmeContext twinmeContext, @NonNull Contact contact,
                             @NonNull UUID engineId) {
        super(twinmeContext, contact, EngineCommand.stop(twinmeContext.newRequestId(), engineId));
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineStopAction");
        }
    }
}
