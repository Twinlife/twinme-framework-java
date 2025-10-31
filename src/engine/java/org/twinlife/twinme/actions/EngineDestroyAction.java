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
 * Engine destroy action to delete a Twinroom within an engine (dangerous operation!!!).
 *
 * When the engine is destroyed, all contacts it had are deleted and its account is removed from the server.
 * All the data are then erased.
 */
public class EngineDestroyAction extends EngineAction<EngineCommandResult> {
    private static final String LOG_TAG = "EngineDestroyAction";
    private static final boolean DEBUG = false;

    @NonNull
    public static EngineDestroyAction create(@NonNull TwinmeContext twinmeContext, @NonNull Contact contact,
                                            @NonNull String logicalName) {

        return new EngineDestroyAction(twinmeContext, contact, logicalName);
    }

    @NonNull
    public static EngineDestroyAction create(@NonNull TwinmeContext twinmeContext, @NonNull Contact contact,
                                            @NonNull UUID engineId) {

        return new EngineDestroyAction(twinmeContext, contact, engineId);
    }

    private EngineDestroyAction(@NonNull TwinmeContext twinmeContext, @NonNull Contact contact,
                                @NonNull String logicalName) {
        super(twinmeContext, contact, EngineCommand.destroy(twinmeContext.newRequestId(), logicalName));
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineDestroyAction");
        }
    }

    private EngineDestroyAction(@NonNull TwinmeContext twinmeContext, @NonNull Contact contact,
                                @NonNull UUID engineId) {
        super(twinmeContext, contact, EngineCommand.destroy(twinmeContext.newRequestId(), engineId));
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineDestroyAction");
        }
    }
}
