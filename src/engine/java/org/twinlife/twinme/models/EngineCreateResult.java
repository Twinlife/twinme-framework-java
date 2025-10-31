/*
 *  Copyright (c) 2021 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;

import java.util.UUID;

/*
 * <pre>
 *
 * Schema version 1
 *
 * {
 *  "type":"record",
 *  "name":"RoomCommand",
 *  "namespace":"org.twinlife.twinme.schemas",
 *  "fields":
 *  [
 *   {"name":"schemaId", "type":"uuid"},
 *   {"name":"schemaVersion", "type":"int"}
 *   {"name":"requestId", "type":"long"}
 *   {"name":"status", "type":"enum"}
 *  ]
 * }
 *
 * </pre>
 */

/**
 * Result of a command sent to a Twinroom.
 *
 */
public class EngineCreateResult extends EngineCommandResult {
    private static final String LOG_TAG = "EngineCreateResult";
    private static final boolean DEBUG = false;

    private static final UUID SCHEMA_ID = UUID.fromString("9b70eb4f-a50e-4914-bcff-4c8b5514e05c");
    private static final int SCHEMA_VERSION = 1;

    public static class EngineCreateResultSerializer extends EngineCommandResult.EngineCommandResultSerializer {

        public EngineCreateResultSerializer() {

            super(SCHEMA_ID, SCHEMA_VERSION, EngineCreateResult.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            encoder.writeUUID(schemaId);
            encoder.writeInt(schemaVersion);

            EngineCreateResult result = (EngineCreateResult) object;
            serialize(encoder, result);

            if (result.mEngineInfo == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                EngineInfo.Serializer.serialize(encoder, result.mEngineInfo);
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            EngineCommandResult result = deserialize(decoder);

            EngineInfo info;
            if (decoder.readEnum() == 0) {
                info = null;
            } else {
                info = EngineInfo.Serializer.deserialize(decoder);
            }

            return new EngineCreateResult(result, info);
        }
    }

    @Nullable
    private final EngineInfo mEngineInfo;

    public EngineCreateResult(@NonNull EngineCommand command, @NonNull Status status) {
        super(command, status);
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineCreateResult: command=" + command + " status=" + status);
        }

        mEngineInfo = null;
    }

    public EngineCreateResult(@NonNull EngineCommand command, @NonNull EngineInfo info) {
        super(command, Status.SUCCESS);
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineCreateResult: command=" + command + " info=" + info);
        }

        mEngineInfo = info;
    }

    @Nullable
    public EngineInfo getEngineInfo() {

        return mEngineInfo;
    }

    private EngineCreateResult(@NonNull EngineCommandResult result, @Nullable EngineInfo info) {
        super(result.getRequestId(), result.getStatus());
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineCreateResult: result=" + result);
        }

        mEngineInfo = info;
    }

    @Override
    @NonNull
    public String toString() {

        return "EngineCreateResult: requestId=" + getRequestId() + " status=" + getStatus() + "\n";
    }
}
