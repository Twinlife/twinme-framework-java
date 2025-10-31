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
 * Engine start action to start a Twinroom within an engine.
 */
public class EngineStartAction extends EngineAction<EngineCommandResult> {
    private static final String LOG_TAG = "EngineStartAction";
    private static final boolean DEBUG = false;

    @NonNull
    public static EngineStartAction create(@NonNull TwinmeContext twinmeContext, @NonNull Contact contact,
                                            @NonNull String logicalName) {

        return new EngineStartAction(twinmeContext, contact, logicalName);
    }

    @NonNull
    public static EngineStartAction create(@NonNull TwinmeContext twinmeContext, @NonNull Contact contact,
                                            @NonNull UUID engineId) {

        return new EngineStartAction(twinmeContext, contact, engineId);
    }

    private EngineStartAction(@NonNull TwinmeContext twinmeContext, @NonNull Contact contact,
                               @NonNull String logicalName) {
        super(twinmeContext, contact, EngineCommand.start(twinmeContext.newRequestId(), logicalName));
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineStartAction");
        }
    }

    private EngineStartAction(@NonNull TwinmeContext twinmeContext, @NonNull Contact contact,
                               @NonNull UUID engineId) {
        super(twinmeContext, contact, EngineCommand.start(twinmeContext.newRequestId(), engineId));
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineStartAction");
        }
    }
}
