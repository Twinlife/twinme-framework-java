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
import org.twinlife.twinme.models.EngineListResult;

/**
 * Engine list action to get the list of twinrooms.
 */
public class EngineListAction extends EngineAction<EngineListResult> {
    private static final String LOG_TAG = "EngineListAction";
    private static final boolean DEBUG = false;

    @NonNull
    public static EngineListAction create(@NonNull TwinmeContext twinmeContext, @NonNull Contact contact,
                                          @NonNull String filter) {

        return new EngineListAction(twinmeContext, contact, filter);
    }

    private EngineListAction(@NonNull TwinmeContext twinmeContext, @NonNull Contact contact, @NonNull String filter) {
        super(twinmeContext, contact, EngineCommand.list(twinmeContext.newRequestId(), filter));
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineListAction");
        }
    }
}
