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

import java.util.ArrayList;
import java.util.List;
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
public class EngineListResult extends EngineCommandResult {
    private static final String LOG_TAG = "EngineListResult";
    private static final boolean DEBUG = false;

    private static final UUID SCHEMA_ID = UUID.fromString("acedb8aa-347d-49f4-a5f1-c44c8bf68c96");
    private static final int SCHEMA_VERSION = 1;

    public static class EngineListResultSerializer extends EngineCommandResult.EngineCommandResultSerializer {

        public EngineListResultSerializer() {

            super(SCHEMA_ID, SCHEMA_VERSION, EngineListResult.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            encoder.writeUUID(schemaId);
            encoder.writeInt(schemaVersion);

            EngineListResult result = (EngineListResult) object;
            serialize(encoder, result);

            if (result.mEngines == null) {
                encoder.writeInt(0);
            } else {
                encoder.writeInt(result.mEngines.size());
                for (EngineInfo info : result.mEngines) {
                    EngineInfo.Serializer.serialize(encoder, info);
                }
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            EngineCommandResult result = deserialize(decoder);
            int count = decoder.readInt();
            List<EngineInfo> list = null;
            if (count > 0) {
                list = new ArrayList<>();
                while (count > 0) {
                    list.add(EngineInfo.Serializer.deserialize(decoder));
                    count--;
                }
            }

            return new EngineListResult(result, list);
        }
    }

    @Nullable
    private final List<EngineInfo> mEngines;

    public EngineListResult(@NonNull EngineCommand command, @NonNull Status status, @Nullable List<EngineInfo> list) {
        super(command, status);
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineListResult: command=" + command + " status=" + status);
        }

        mEngines = list;
    }

    @Nullable
    public List<EngineInfo> getEngines() {

        return mEngines;
    }

    private EngineListResult(@NonNull EngineCommandResult result, @Nullable List<EngineInfo> list) {
        super(result.getRequestId(), result.getStatus());
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineListResult: result=" + result);
        }

        mEngines = list;
    }

    @Override
    @NonNull
    public String toString() {

        return "EngineListResult: requestId=" + getRequestId() + " status=" + getStatus() + "\n";
    }
}
