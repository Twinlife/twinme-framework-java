/*
 *  Copyright (c) 2021 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.actions;

import android.graphics.Bitmap;
import android.util.Log;
import androidx.annotation.NonNull;
import org.twinlife.twinme.TwinmeContext;
import org.twinlife.twinme.models.Contact;
import org.twinlife.twinme.models.EngineCommand;
import org.twinlife.twinme.models.EngineConfig;
import org.twinlife.twinme.models.EngineCreateResult;
import org.twinlife.twinme.models.RoomConfig;

/**
 * Engine create action to create and instantiate a new twinroom in the engine.
 */
public class EngineCreateAction extends EngineAction<EngineCreateResult> {
    private static final String LOG_TAG = "EngineCreateAction";
    private static final boolean DEBUG = false;

    @NonNull
    public static EngineCreateAction create(@NonNull TwinmeContext twinmeContext, @NonNull Contact contact,
                                            @NonNull String name, @NonNull Bitmap avatar,
                                            @NonNull EngineConfig engineConfig,
                                            @NonNull RoomConfig roomConfig) {

        return new EngineCreateAction(twinmeContext, contact, name, avatar, engineConfig, roomConfig);
    }

    private EngineCreateAction(@NonNull TwinmeContext twinmeContext, @NonNull Contact contact,
                               @NonNull String name, @NonNull Bitmap avatar, @NonNull EngineConfig engineConfig,
                               @NonNull RoomConfig roomConfig) {
        super(twinmeContext, contact, EngineCommand.create(twinmeContext.newRequestId(), name, avatar, engineConfig, roomConfig));
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineCreateAction");
        }
    }
}
