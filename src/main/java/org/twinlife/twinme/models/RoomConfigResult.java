/*
 *  Copyright (c) 2020-2021 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.Serializer;
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
 *  "name":"RoomConfigResult",
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
public class RoomConfigResult extends RoomCommandResult {
    private static final String LOG_TAG = "RoomConfigResult";
    private static final boolean DEBUG = false;

    private static final UUID SCHEMA_ID = UUID.fromString("a9a2a78b-b224-4aab-b61b-1a8ed17b80a7");
    private static final int SCHEMA_VERSION = 1;

    private static final int CONVERSATION_SERVICE_MIN_MAJOR_VERSION = 2;
    private static final int CONVERSATION_SERVICE_MIN_MINOR_VERSION = 10;

    public static class RoomConfigResultSerializer extends Serializer {

        public RoomConfigResultSerializer() {

            super(SCHEMA_ID, SCHEMA_VERSION, RoomConfigResult.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            encoder.writeUUID(schemaId);
            encoder.writeInt(schemaVersion);

            RoomConfigResult result = (RoomConfigResult) object;
            encoder.writeLong(result.getRequestId());
            switch (result.getStatus()) {
                case SUCCESS:
                    encoder.writeEnum(0);
                    break;

                case ERROR:
                    encoder.writeEnum(1);
                    break;

                case BAD_COMMAND:
                    encoder.writeEnum(2);
                    break;

                case PERMISSION_DENIED:
                    encoder.writeEnum(3);
                    break;

                case ITEM_NOT_FOUND:
                    encoder.writeEnum(4);
                    break;
            }

            RoomConfig config = result.mRoomConfig;
            if (config == null) {
                encoder.writeInt(0);
            } else {
                encoder.writeInt(1);
                RoomConfig.Serializer.serialize(encoder, config);
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            Status status;
            long requestId = decoder.readLong();
            switch (decoder.readEnum()) {
                case 0:
                    status = Status.SUCCESS;
                    break;

                case 1:
                    status = Status.ERROR;
                    break;

                case 2:
                    status = Status.BAD_COMMAND;
                    break;

                case 3:
                    status = Status.PERMISSION_DENIED;
                    break;

                case 4:
                    status = Status.ITEM_NOT_FOUND;
                    break;

                default:
                    throw new SerializerException("RoomCommand action not recognized");
            }

            int hasConfig = decoder.readInt();
            RoomConfig config = null;
            if (hasConfig > 0) {
                config = RoomConfig.Serializer.deserialize(decoder);
            }

            return new RoomConfigResult(requestId, status, config);
        }

        @Override
        public boolean isSupported(int majorVersion, int minorVersion) {

            return majorVersion == CONVERSATION_SERVICE_MIN_MAJOR_VERSION && minorVersion >= CONVERSATION_SERVICE_MIN_MINOR_VERSION;
        }
    }

    @Nullable
    private final RoomConfig mRoomConfig;

    public RoomConfigResult(@NonNull RoomCommand command, @NonNull Status status, @Nullable RoomConfig roomConfig) {
        super(command, status);
        if (DEBUG) {
            Log.d(LOG_TAG, "RoomConfigResult: command=" + command + " status=" + status + " roomConfig=" + roomConfig);
        }

        mRoomConfig = roomConfig;
    }

    @Nullable
    public RoomConfig getRoomConfig() {

        return mRoomConfig;
    }

    @Override
    @NonNull
    public String toString() {

        return "RoomConfigResult: requestId=" + getRequestId() + " status=" + getStatus() + "\n";
    }

    private RoomConfigResult(long requestId, @NonNull Status status, @Nullable RoomConfig roomConfig) {
        super(requestId, status, null);
        if (DEBUG) {
            Log.d(LOG_TAG, "RoomConfigResult: requestId=" + requestId + " status=" + status + " roomConfig=" + roomConfig);
        }

        mRoomConfig = roomConfig;
    }
}
