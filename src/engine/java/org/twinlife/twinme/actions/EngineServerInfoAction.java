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
import org.twinlife.twinme.models.EngineServerInfoResult;

/**
 * Engine server info action to get the information about the engine server.
 */
public class EngineServerInfoAction extends EngineAction<EngineServerInfoResult> {
    private static final String LOG_TAG = "EngineServerInfoAction";
    private static final boolean DEBUG = false;

    @NonNull
    public static EngineServerInfoAction create(@NonNull TwinmeContext twinmeContext, @NonNull Contact contact) {

        return new EngineServerInfoAction(twinmeContext, contact);
    }

    private EngineServerInfoAction(@NonNull TwinmeContext twinmeContext, @NonNull Contact contact) {
        super(twinmeContext, contact, EngineCommand.serverInformation(twinmeContext.newRequestId()));
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineServerInfoAction");
        }
    }
}
