/*
 *  Copyright (c) 2015-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
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
 *  "name":"Message",
 *  "namespace":"org.twinlife.twinme.schemas",
 *  "fields":
 *  [
 *   {"name":"schemaId", "type":"uuid"},
 *   {"name":"schemaVersion", "type":"int"}
 *   {"name":"content", "type":"string"}
 *  ]
 * }
 *
 * </pre>
 */

public class Message {
    private static final String LOG_TAG = "Message";
    private static final boolean DEBUG = false;

    private static final UUID SCHEMA_ID = UUID.fromString("c1ba9e82-43a7-413a-ab9f-b743859e7595");
    private static final int SCHEMA_VERSION = 1;

    public static class MessageSerializer extends Serializer {

        public MessageSerializer() {

            super(SCHEMA_ID, SCHEMA_VERSION, Message.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            encoder.writeUUID(schemaId);
            encoder.writeInt(schemaVersion);

            Message message = (Message) object;
            encoder.writeString(message.mContent);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            String content = decoder.readString();

            return new Message(content);
        }
    }

    @NonNull
    private final String mContent;

    public Message(@NonNull String content) {
        if (DEBUG) {
            Log.d(LOG_TAG, "Message: content=" + content);
        }

        mContent = content;
    }

    @NonNull
    public String getContent() {

        return mContent;
    }

    @Override
    @NonNull
    public String toString() {

        return "Message:[...]";
    }
}
