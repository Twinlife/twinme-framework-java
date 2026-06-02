/*
 *  Copyright (c) 2021-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.CryptoService;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.TwincodeURI;

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
            final TwincodeURI twincodeURI = config.getInvitationURI();
            if (twincodeURI == null || twincodeURI.twincodeId == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                encoder.writeUUID(twincodeURI.twincodeId);
            }
            if (config.getWelcome() == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                encoder.writeString(config.getWelcome());
            }

            // 2025-08-29: RoomConfig extended format: we add the optional invitation public key.
            encoder.writeEnum(1);
            if (twincodeURI == null || twincodeURI.pubKey == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                encoder.writeString(twincodeURI.pubKey.asString());
            }
            if (twincodeURI == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                encoder.writeString(twincodeURI.uri);
            }
            if (twincodeURI == null || twincodeURI.twincodeOptions == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                encoder.writeString(twincodeURI.twincodeOptions);
            }
            if (twincodeURI == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                encoder.writeString(twincodeURI.label);
            }

            // Finish with a 0 so that we can more easily extend the RoomConfig object.
            encoder.writeEnum(0);
        }

        @NonNull
        public static RoomConfig deserialize(@NonNull Decoder decoder) throws SerializerException {

            ChatMode chatMode;
            switch (decoder.readEnum()) {

                case 1:
                    chatMode = ChatMode.CHAT_CHANNEL;
                    break;

                case 2:
                    chatMode = ChatMode.CHAT_FEEDBACK;
                    break;

                case 0:
                default:
                    chatMode = ChatMode.CHAT_PUBLIC;
                    break;
            }

            CallMode callMode;
            switch (decoder.readEnum()) {

                case 1:
                    callMode = CallMode.CALL_AUDIO;
                    break;

                case 2:
                    callMode = CallMode.CALL_VIDEO;
                    break;

                case 0:
                default:
                    callMode = CallMode.CALL_DISABLED;
                    break;
            }

            NotificationMode notificationMode;
            switch (decoder.readEnum()) {

                case 1:
                    notificationMode = NotificationMode.INFORM;
                    break;

                case 2:
                    notificationMode = NotificationMode.NOISY;
                    break;

                case 0:
                default:
                    notificationMode = NotificationMode.QUIET;
                    break;
            }

            InvitationMode invitationMode;
            switch (decoder.readEnum()) {

                case 1:
                    invitationMode = InvitationMode.INVITE_ADMIN;
                    break;

                case 0:
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

            // 2025-08-29: check if we have the RoomConfig format with public key.
            String invitationPublicKey = null;
            String twincodeOptions = null;
            String uri = null;
            String label = null;
            if (decoder.readEnum() != 0) {
                invitationPublicKey = decoder.readOptionalString();
                uri = decoder.readOptionalString();
                twincodeOptions = decoder.readOptionalString();
                label = decoder.readOptionalString();
                decoder.readEnum();

                // If we add information in RoomConfig, we can extract it with.  It is ignored otherwise.
                // if (decoder.readEnum() != 0) {
                //
                // }
            }

            final TwincodeURI twincodeURI;
            if (invitationTwincodeId != null && uri != null && label != null) {
                twincodeURI = new TwincodeURI(TwincodeURI.Kind.Invitation, invitationTwincodeId,
                        twincodeOptions, CryptoService.PublicKeyData.create(invitationPublicKey), uri, label);
            } else {
                twincodeURI = null;
            }

            return new RoomConfig(welcome, chatMode, callMode, notificationMode, invitationMode, twincodeURI);
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
    private final TwincodeURI mInvitationTwincode;
    @NonNull
    private InvitationMode mInvitationMode;

    public RoomConfig(@Nullable String welcome, @NonNull ChatMode chatMode,
                      @NonNull CallMode callMode, @NonNull NotificationMode notificationMode,
                      @NonNull InvitationMode invitationMode,
                      @Nullable TwincodeURI invitationTwincode) {
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
     * The result can be null if the user is not allowed to invite other members in the twinroom.
     *
     * @return the twincode id or null.
     */
    @Nullable
    public UUID getInvitationTwincode() {

        return mInvitationTwincode == null ? null : mInvitationTwincode.twincodeId;
    }

    public String getInvitationLink() {

        return mInvitationTwincode == null ? null : mInvitationTwincode.uri;
    }

    @Nullable
    public TwincodeURI getInvitationURI() {

        return mInvitationTwincode;
    }
}