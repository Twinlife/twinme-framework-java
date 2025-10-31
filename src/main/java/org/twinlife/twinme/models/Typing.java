/*
 *  Copyright (c) 2019 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
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
 *  "name":"Typing",
 *  "namespace":"org.twinlife.twinme.schemas",
 *  "fields":
 *  [
 *   {"name":"schemaId", "type":"uuid"},
 *   {"name":"schemaVersion", "type":"int"}
 *   {"name":"action", "type":"enum"}
 *  ]
 * }
 *
 * </pre>
 */

/**
 * Transient user typing action.
 *
 * The `Typing` object is intended to be sent through the pushTransientObject() operation to notify the peer
 * that the user starts or stops typing some text.
 */
public class Typing {
    private static final String LOG_TAG = "Typing";
    private static final boolean DEBUG = false;

    private static final UUID SCHEMA_ID = UUID.fromString("4d23a645-233b-4d8f-a9aa-2b15b37e2ba3");
    private static final int SCHEMA_VERSION = 1;

    private static final int CONVERSATION_SERVICE_MIN_MAJOR_VERSION = 2;
    private static final int CONVERSATION_SERVICE_MIN_MINOR_VERSION = 9;

    public static class TypingSerializer extends Serializer {

        public TypingSerializer() {

            super(SCHEMA_ID, SCHEMA_VERSION, Typing.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            encoder.writeUUID(schemaId);
            encoder.writeInt(schemaVersion);

            Typing typing = (Typing) object;
            switch (typing.mAction) {
                case STOP:
                    encoder.writeEnum(0);
                    break;

                case START:
                    encoder.writeEnum(1);
                    break;

                default:
                    throw new SerializerException("Typing action not supported");
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            switch (decoder.readEnum()) {
                case 0:
                    return new Typing(Action.STOP);

                case 1:
                    return new Typing(Action.START);

                default:
                    throw new SerializerException("Typing action not recognized");
            }
        }

        @Override
        public boolean isSupported(int majorVersion, int minorVersion) {

            return majorVersion == CONVERSATION_SERVICE_MIN_MAJOR_VERSION && minorVersion >= CONVERSATION_SERVICE_MIN_MINOR_VERSION;
        }
    }

    @NonNull
    private final Action mAction;

    public enum Action {
        START,
        STOP
    }

    public Typing(@NonNull Action action) {
        if (DEBUG) {
            Log.d(LOG_TAG, "Typing: action=" + action);
        }

        mAction = action;
    }

    @NonNull
    public Action getAction() {

        return mAction;
    }

    @Override
    @NonNull
    public String toString() {

        return "Typing: action=" + mAction + "\n";
    }
}
