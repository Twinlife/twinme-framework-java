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
import org.twinlife.twinme.models.EngineStatResult;

/**
 * Engine stat action to retrieve statistics on Twinroom engines.
 */
public class EngineStatAction extends EngineAction<EngineStatResult> {
    private static final String LOG_TAG = "EngineStatAction";
    private static final boolean DEBUG = false;

    @NonNull
    public static EngineStatAction create(@NonNull TwinmeContext twinmeContext, @NonNull Contact contact,
                                          @NonNull String filter) {

        return new EngineStatAction(twinmeContext, contact, filter);
    }

    private EngineStatAction(@NonNull TwinmeContext twinmeContext, @NonNull Contact contact,
                             @NonNull String filter) {
        super(twinmeContext, contact, EngineCommand.stats(twinmeContext.newRequestId(), filter));
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineStatAction");
        }
    }
}
