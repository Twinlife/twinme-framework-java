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
import java.util.Map;
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
public class EngineStatResult extends EngineCommandResult {
    private static final String LOG_TAG = "EngineStatResult";
    private static final boolean DEBUG = false;

    private static final UUID SCHEMA_ID = UUID.fromString("51877bff-baf1-4286-8343-d9c8f1fade23");
    private static final int SCHEMA_VERSION = 1;

    public static class EngineStatResultSerializer extends EngineCommandResult.EngineCommandResultSerializer {

        public EngineStatResultSerializer() {

            super(SCHEMA_ID, SCHEMA_VERSION, EngineStatResult.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            encoder.writeUUID(schemaId);
            encoder.writeInt(schemaVersion);

            EngineStatResult result = (EngineStatResult) object;
            serialize(encoder, result);

            if (result.mEngineStats == null) {
                encoder.writeInt(0);
            } else {
                encoder.writeInt(result.mEngineStats.size());
                for (EngineStat stat : result.mEngineStats) {
                    serializeEngineStat(encoder, stat);
                }
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            EngineCommandResult result = deserialize(decoder);
            int count = decoder.readInt();

            List<EngineStat> list = null;
            if (count > 0) {
                list = new ArrayList<>();
                while (count > 0) {
                    EngineStat stat = deserializeEngineStat(decoder);
                    list.add(stat);
                    count--;
                }
            }

            return new EngineStatResult(result, list);
        }

        private static void serializeEngineStat(@NonNull Encoder encoder, @NonNull EngineStat stat) throws SerializerException {
            encoder.writeString(stat.getLogicalName());
            encoder.writeUUID(stat.getEngineId());

            Map<String, Object> stats = stat.getEngineStats();
            encoder.writeInt(stats.size());
            for (Map.Entry<String, Object> item : stats.entrySet()) {
                String name = item.getKey();
                Object value = item.getValue();
                if ((value instanceof Long)) {
                    encoder.writeString(name);
                    encoder.writeInt(1);
                    encoder.writeLong((Long) value);
                } else if ((value instanceof String)) {
                    encoder.writeString(name);
                    encoder.writeInt(2);
                    encoder.writeString((String) value);
                } else {
                    encoder.writeString(name);
                    encoder.writeInt(0);
                }
            }
        }

        @NonNull
        private static EngineStat deserializeEngineStat(@NonNull Decoder decoder) throws SerializerException {
            String logicalName = decoder.readString();
            UUID engineId = decoder.readUUID();
            EngineStat stat = new EngineStat(logicalName, engineId);

            int statCount = decoder.readInt();

            while (statCount > 0) {
                statCount--;
                String name = decoder.readString();
                switch (decoder.readEnum()) {
                    case 0:
                        break;

                    case 1:
                        stat.putValue(name, decoder.readLong());
                        break;

                    case 2:
                        stat.putValue(name, decoder.readString());
                        break;

                    default:
                        break;
                }

            }
            return stat;
        }
    }

    @Nullable
    private final List<EngineStat> mEngineStats;

    public EngineStatResult(@NonNull EngineCommand command, @NonNull Status status,
                            @Nullable List<EngineStat> roomStats) {
        super(command, status);
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineStatResult: command=" + command + " status=" + status);
        }

        mEngineStats = roomStats;
    }

    @Nullable
    public List<EngineStat> getStats() {

        return mEngineStats;
    }

    private EngineStatResult(@NonNull EngineCommandResult result, @Nullable List<EngineStat> stats) {
        super(result.getRequestId(), result.getStatus());
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineStatResult: result=" + result);
        }

        mEngineStats = stats;
    }

    @Override
    @NonNull
    public String toString() {

        return "EngineStatResult: requestId=" + getRequestId() + " status=" + getStatus() + "\n";
    }
}
