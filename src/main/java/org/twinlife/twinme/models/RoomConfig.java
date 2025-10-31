/*
 *  Copyright (c) 2021 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;

import java.util.UUID;

/**
 * Information about the room configuration.
 */
public class RoomConfig {

    public static class Serializer {

        public static void serialize(@NonNull Encoder encoder, @NonNull RoomConfig config) throws SerializerException {

            switch (config.getChatMode()) {
                case CHAT_PUBLIC:
                    encoder.writeEnum(0);
                    break;

                case CHAT_CHANNEL:
                    encoder.writeEnum(1);
                    break;

                case CHAT_FEEDBACK:
                    encoder.writeEnum(2);
                    break;
            }
            switch (config.getCallMode()) {
                case CALL_DISABLED:
                    encoder.writeEnum(0);
                    break;

                case CALL_AUDIO:
                    encoder.writeEnum(1);
                    break;

                case CALL_VIDEO:
                    encoder.writeEnum(2);
                    break;
            }
            switch (config.getNotificationMode()) {
                case QUIET:
                    encoder.writeEnum(0);
                    break;

                case INFORM:
                    encoder.writeEnum(1);
                    break;

                case NOISY:
                    encoder.writeEnum(2);
                    break;
            }
            switch (config.getInvitationMode()) {
                case INVITE_PUBLIC:
                    encoder.writeEnum(0);
                    break;

                case INVITE_ADMIN:
                    encoder.writeEnum(1);
                    break;
            }
            if (config.getInvitationTwincode() == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                encoder.writeUUID(config.getInvitationTwincode());
            }
            if (config.getWelcome() == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                encoder.writeString(config.getWelcome());
            }

            // Finish with a 0 so that we can more easily extend the RoomConfig object.
            encoder.writeEnum(0);
        }

        @NonNull
        public static RoomConfig deserialize(@NonNull Decoder decoder) throws SerializerException {

            ChatMode chatMode;
            switch (decoder.readEnum()) {
                case 0:
                    chatMode = ChatMode.CHAT_PUBLIC;
                    break;

                case 1:
                    chatMode = ChatMode.CHAT_CHANNEL;
                    break;

                case 2:
                    chatMode = ChatMode.CHAT_FEEDBACK;
                    break;

                default:
                    chatMode = ChatMode.CHAT_PUBLIC;
                    break;
            }

            CallMode callMode;
            switch (decoder.readEnum()) {
                case 0:
                    callMode = CallMode.CALL_DISABLED;
                    break;

                case 1:
                    callMode = CallMode.CALL_AUDIO;
                    break;

                case 2:
                    callMode = CallMode.CALL_VIDEO;
                    break;

                default:
                    callMode = CallMode.CALL_DISABLED;
                    break;
            }

            NotificationMode notificationMode;
            switch (decoder.readEnum()) {
                case 0:
                    notificationMode = NotificationMode.QUIET;
                    break;

                case 1:
                    notificationMode = NotificationMode.INFORM;
                    break;

                case 2:
                    notificationMode = NotificationMode.NOISY;
                    break;

                default:
                    notificationMode = NotificationMode.QUIET;
                    break;
            }

            InvitationMode invitationMode;
            switch (decoder.readEnum()) {
                case 0:
                    invitationMode = InvitationMode.INVITE_PUBLIC;
                    break;

                case 1:
                    invitationMode = InvitationMode.INVITE_ADMIN;
                    break;

                default:
                    invitationMode = InvitationMode.INVITE_PUBLIC;
                    break;
            }
            UUID invitationTwincodeId;
            if (decoder.readEnum() == 0) {
                invitationTwincodeId = null;
            } else {
                invitationTwincodeId = decoder.readUUID();
            }

            String welcome;
            if (decoder.readEnum() == 0) {
                welcome = null;
            } else {
                welcome = decoder.readString();
            }

            // If we add information in RoomConfig, we can extract it with.  It is ignored otherwise.
            int unused = decoder.readEnum();
            // if (decoder.readEnum() != 0) {
            //
            // }

            return new RoomConfig(welcome, chatMode, callMode, notificationMode, invitationMode, invitationTwincodeId);
        }
    }

    public enum ChatMode {
        // Room is public, anybody can write and messages are dispatched to members
        CHAT_PUBLIC,

        // Room is a channel, only administrators can write messages, users can post feedbacks to admin
        CHAT_FEEDBACK,

        // Room is a channel where only administrators can write messages.
        CHAT_CHANNEL
    }

    public enum CallMode {
        // Audio and video calls are disabled.
        CALL_DISABLED,

        // Only the audio call is allowed.
        CALL_AUDIO,

        // Audio and video calls are allowed.
        CALL_VIDEO
    }

    public enum NotificationMode {
        // The room is quiet when a member joins an audio/video call.
        QUIET,

        // Post a notification when the conference starts (first person join) and stops (last person leaves).
        INFORM,

        // The room send a message each time a member joins or leaves the call.
        NOISY
    }

    public enum InvitationMode {
        // The room Twincode is public and anybody can join the twinroom.
        INVITE_PUBLIC,

        // The room Twincode is visible only to admin users.
        INVITE_ADMIN
    }

    @Nullable
    private String mWelcome;
    @NonNull
    private ChatMode mChatMode;
    @NonNull
    private CallMode mCallMode;
    @NonNull
    private NotificationMode mNotificationMode;
    @Nullable
    private final UUID mInvitationTwincode;
    @NonNull
    private InvitationMode mInvitationMode;

    public RoomConfig(@Nullable String welcome, @NonNull ChatMode chatMode,
                      @NonNull CallMode callMode, @NonNull NotificationMode notificationMode,
                      @NonNull InvitationMode invitationMode,
                      @Nullable UUID invitationTwincode) {
        this.mWelcome = welcome;
        this.mCallMode = callMode;
        this.mChatMode = chatMode;
        this.mNotificationMode = notificationMode;
        this.mInvitationTwincode = invitationTwincode;
        this.mInvitationMode = invitationMode;
    }

    /**
     * Get the welcome message.
     *
     * @return the welcome message.
     */
    @Nullable
    public String getWelcome() {

        return mWelcome;
    }

    public void setWelcome(@Nullable String message) {

        mWelcome = message;
    }

    /**
     * Get the chat mode that describes how chat is managed.
     *
     * @return the  chat mode.
     */
    @NonNull
    public ChatMode getChatMode() {

        return mChatMode;
    }

    public void setChatMode(@NonNull ChatMode chatMode) {

        mChatMode = chatMode;
    }

    /**
     * Get the audio/video call mode.
     *
     * @return the audio/video call mode.
     */
    @NonNull
    public CallMode getCallMode() {

        return mCallMode;
    }

    public void setCallMode(@NonNull CallMode callMode) {

        mCallMode = callMode;
    }

    /**
     * Get the notification mode for audio/video conferences.
     *
     * @return notification mode.
     */
    @NonNull
    public NotificationMode getNotificationMode() {

        return mNotificationMode;
    }

    public void setNotificationMode(@NonNull NotificationMode notificationMode) {

        mNotificationMode = notificationMode;
    }

    /**
     * Get the invitation mode of the twinroom.
     * This controls how the Twinroom twincode is shared and how new members are accepted.
     *
     * @return the invitation mode.
     */
    @NonNull
    public InvitationMode getInvitationMode() {

        return mInvitationMode;
    }

    public void setInvitationMode(@NonNull InvitationMode invitationMode) {

        mInvitationMode = invitationMode;
    }

    /**
     * Get the invitation twincode for the twinroom.
     *
     * The result can be null if the user is not allowed to invite other members in the twinroom.
     *
     * @return the twincode id or null.
     */
    @Nullable
    public UUID getInvitationTwincode() {

        return mInvitationTwincode;
    }
}