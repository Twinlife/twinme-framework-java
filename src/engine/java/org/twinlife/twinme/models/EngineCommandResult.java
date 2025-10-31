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
public class EngineCommandResult {
    private static final String LOG_TAG = "EngineCommandResult";
    private static final boolean DEBUG = false;

    private static final UUID SCHEMA_ID = UUID.fromString("21ecbd11-be93-4d76-bd7d-3fffe3d460bd");
    private static final int SCHEMA_VERSION = 1;

    private static final int CONVERSATION_SERVICE_MIN_MAJOR_VERSION = 2;
    private static final int CONVERSATION_SERVICE_MIN_MINOR_VERSION = 10;

    public static class EngineCommandResultSerializer extends Serializer {

        public EngineCommandResultSerializer() {

            super(SCHEMA_ID, SCHEMA_VERSION, EngineCommandResult.class);
        }

        protected EngineCommandResultSerializer(@NonNull UUID schemaId, int schemaVersion, @NonNull Class<?> clazz) {
            super(schemaId, schemaVersion, clazz);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            encoder.writeUUID(schemaId);
            encoder.writeInt(schemaVersion);

            EngineCommandResult result = (EngineCommandResult) object;
            serialize(encoder, result);
        }

        protected void serialize(@NonNull Encoder encoder, @NonNull EngineCommandResult result) throws SerializerException {

            encoder.writeLong(result.mRequestId);
            switch (result.mStatus) {
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

                case NO_SPACE:
                    encoder.writeEnum(5);
                    break;

                case UNKNOWN_ENGINE_CLASS:
                    encoder.writeEnum(6);
                    break;
            }
        }

        @NonNull
        public EngineCommandResult deserialize(@NonNull Decoder decoder) throws SerializerException {

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

                case 5:
                    status = Status.NO_SPACE;
                    break;

                case 6:
                    status = Status.UNKNOWN_ENGINE_CLASS;
                    break;

                default:
                    throw new SerializerException("RoomCommand action not recognized");
            }

            return new EngineCommandResult(requestId, status);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            return deserialize(decoder);
        }

        @Override
        public boolean isSupported(int majorVersion, int minorVersion) {

            return majorVersion == CONVERSATION_SERVICE_MIN_MAJOR_VERSION && minorVersion >= CONVERSATION_SERVICE_MIN_MINOR_VERSION;
        }
    }

    private final long mRequestId;
    @NonNull
    private final Status mStatus;

    public enum Status {
        SUCCESS,
        ERROR,
        BAD_COMMAND,
        PERMISSION_DENIED,
        ITEM_NOT_FOUND,
        UNKNOWN_ENGINE_CLASS,
        NO_SPACE
    }

    public EngineCommandResult(@NonNull EngineCommand command, @NonNull Status status) {
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineCommandResult: command=" + command + " status=" + status);
        }

        mRequestId = command.getRequestId();
        mStatus = status;
    }

    protected EngineCommandResult(long requestId, @NonNull Status status) {
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineCommandResult: requestId=" + requestId + " status=" + status);
        }

        mRequestId = requestId;
        mStatus = status;
    }

    public long getRequestId() {

        return mRequestId;
    }

    @NonNull
    public Status getStatus() {

        return mStatus;
    }

    @Override
    @NonNull
    public String toString() {

        return "EngineCommandResult: requestId=" + mRequestId + " status=" + mStatus + "\n";
    }
}
