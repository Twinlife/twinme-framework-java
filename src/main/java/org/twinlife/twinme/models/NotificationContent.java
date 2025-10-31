/*
 *  Copyright (c) 2018-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.PushNotificationOperation;
import org.twinlife.twinlife.PushNotificationPriority;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.Serializer;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;

import java.util.UUID;

/**
 * <pre>
 *
 * Schema version 1
 *  Date: 2018/04/26
 *
 * {
 *  "type":"record",
 *  "name":"NotificationContent",
 *  "namespace":"org.twinlife.schemas.services",
 *  "fields":
 *  [
 *   {"name":"schemaId", "type":"uuid"},
 *   {"name":"schemaVersion", "type":"int"}
 *   {"name":"sessionId", "type":"uuid"}
 *   {"name":"twincodeInboundId", "type":"uuid"}
 *   {"name":"priority", [null, "type":"string"]}
 *   {"name":"operation", [null, "type":"string"]}
 * }
 *
 * </pre>
 */

public class NotificationContent {

    public static final UUID SCHEMA_ID = UUID.fromString("946fb7cd-f8d2-46a8-a1d2-6d9f3aa0accd");
    public static final int SCHEMA_VERSION = 1;
    public static final NotificationContentSerializer SERIALIZER = new NotificationContentSerializer();

    public static class NotificationContentSerializer extends Serializer {

        NotificationContentSerializer() {

            super(SCHEMA_ID, SCHEMA_VERSION, NotificationContent.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            encoder.writeUUID(schemaId);
            encoder.writeInt(schemaVersion);

            NotificationContent notificationContent = (NotificationContent) object;
            encoder.writeUUID(notificationContent.mSessionId);
            encoder.writeUUID(notificationContent.mTwincodeInboundId);
            encoder.writeString(notificationContent.mPriority);
            encoder.writeString(notificationContent.mOperation);
        }

        @Override
        @NonNull
        public Object deserialize(@Nullable SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            UUID sessionId = decoder.readUUID();
            UUID twincodeInboundId = decoder.readUUID();
            String priority = decoder.readString();
            String operation = decoder.readString();

            return new NotificationContent(sessionId, twincodeInboundId, priority, operation);
        }
    }

    @NonNull
    private final UUID mSessionId;
    @NonNull
    private final UUID mTwincodeInboundId;
    @NonNull
    private final String mPriority;
    @NonNull
    private final String mOperation;
    @Nullable
    private RepositoryObject mSubject;

    private NotificationContent(@NonNull UUID sessionId, @NonNull UUID twincodeInboundId, @NonNull String priority, @NonNull String operation) {

        mSessionId = sessionId;
        mTwincodeInboundId = twincodeInboundId;
        mPriority = priority;
        mOperation = operation;
    }

    @NonNull
    public UUID getTwincodeId() {

        return mTwincodeInboundId;
    }

    @NonNull
    public UUID getSessionId() {

        return mSessionId;
    }

    @Nullable
    public RepositoryObject getSubject() {

        return mSubject;
    }

    public void setSubject(@NonNull RepositoryObject subject) {

        mSubject = subject;
    }

    public PushNotificationPriority getPriority() {

        switch (mPriority) {
            case "high":
                return PushNotificationPriority.HIGH;

            case "low":
                return PushNotificationPriority.LOW;

            default:
                return PushNotificationPriority.NOT_DEFINED;
        }
    }

    public PushNotificationOperation getOperation() {
        
        switch (mOperation) {
            case "audio-call":
                return PushNotificationOperation.AUDIO_CALL;

            case "video-call":
                return PushNotificationOperation.VIDEO_CALL;

            case "video-bell":
                return PushNotificationOperation.VIDEO_BELL;

            case "push-message":
                return PushNotificationOperation.PUSH_MESSAGE;

            case "push-file":
                return PushNotificationOperation.PUSH_FILE;

            case "push-image":
                return PushNotificationOperation.PUSH_IMAGE;

            case "push-audio":
                return PushNotificationOperation.PUSH_AUDIO;

            case "push-video":
                return PushNotificationOperation.PUSH_VIDEO;

            default:
                return PushNotificationOperation.NOT_DEFINED;
        }
    }

    @NonNull
    public String toString() {

        return "NotificationContent:\n" +
                " sessionId=" + mSessionId + "\n" +
                " twincodeInboundId=" + mTwincodeInboundId + "\n" +
                " priority=" + mPriority + "\n" +
                " operation=" + mOperation + "\n";
    }
}
