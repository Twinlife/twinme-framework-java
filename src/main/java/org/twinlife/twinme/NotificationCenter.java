/*
 *  Copyright (c) 2016-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinme;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.Descriptor;
import org.twinlife.twinlife.ConversationService.UpdateType;
import org.twinlife.twinlife.ConversationService.Conversation;
import org.twinlife.twinlife.Notification;
import org.twinlife.twinlife.Offer;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinme.calls.CallStatus;
import org.twinlife.twinme.models.Originator;
import org.twinlife.twinme.models.AccountMigration;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface NotificationCenter {
    /**
     * Use a different notification ID for several notification intents.  The incoming Audio and Video calls are using
     * a different full screen intent and some Android devices fail to update the intent if the same notification ID is used
     * (we end up going to the previous Audio/Video activity instead of the new one).
     */
    int FOREGROUND_SERVICE_NOTIFICATION_ID = 1;
    int CALL_SERVICE_NOTIFICATION_ID = 2;
    int ACCOUNT_MIGRATION_NOTIFICATION_ID = 3;
    int CALL_SERVICE_INCOMING_NOTIFICATION_ID = 4;
    int CALL_SERVICE_INCALL_NOTIFICATION_ID = 5;
    int EXPORT_NOTIFICATION_ID = 6;
    int MESSAGE_SUMMARY_NOTIFICATION_ID = 7;

    void onIncomingCall(@NonNull Originator contact, @Nullable Bitmap avatar, @NonNull UUID peerConnectionId, @NonNull Offer offer);

    void onIncomingAccountMigration(@NonNull AccountMigration accountMigration, @NonNull UUID peerConnectionId);

    void onPopDescriptor(@NonNull Originator contact, @NonNull Conversation conversation, @NonNull UUID sessionId, @NonNull Descriptor descriptor);

    void onUpdateDescriptor(@NonNull Originator contact, @NonNull Conversation conversation, @NonNull Descriptor descriptor, UpdateType updateType);

    void onUpdateAnnotation(@NonNull Originator contact, @NonNull Conversation conversation, @NonNull Descriptor descriptor,
                            @NonNull TwincodeOutbound annotatingUser);

    void onJoinGroup(@NonNull Originator group, @NonNull Conversation conversation);

    void onLeaveGroup(@NonNull ConversationService.GroupConversation conversation);

    void onSetActiveConversation(@NonNull Conversation conversation);

    void onAcknowledgeNotification(@NonNull Notification notification);

    void onNewContact(@NonNull Originator contact);

    void onUnbindContact(@NonNull Originator contact);

    void onUpdateContact(@NonNull Originator contact, @NonNull List<AttributeNameValue> previousAttributes);

    void updateApplicationBadgeNumber(int applicationBadgeNumber);

    void cancel(int notificationId);

    void cancelAll();

    void startForegroundService(Service service, boolean transferring);

    android.app.Notification createCallNotification(@NonNull CallStatus mode, @NonNull Originator originator, @Nullable UUID callId, boolean mute);

    android.app.Notification createIncomingCallNotification(@NonNull Originator contact, @Nullable Bitmap avatar, @NonNull CallStatus callStatus, @Nullable UUID callId);

    /**
     * Create a dummy call notification (without a CallStyle), which we need in CallService to call
     * startForeground ASAP.
     *
     * @return an incoming audio call notification with no details on the caller. We can use it for
     * both audio and video calls, as it will be replaced by the actual notification right after.
     */
    android.app.Notification getPlaceholderCallNotification();

    android.app.Notification createOutgoingCallNotification(@NonNull Originator contact, @NonNull CallStatus callStatus, @Nullable UUID callId);

    Intent createAcceptCallIntent(@NonNull CallStatus mode, @NonNull Originator originator, @Nullable UUID callId);

    void missedCallNotification(Originator originator, boolean video);

    boolean isDoNotDisturb();

    boolean videoVibrate();

    boolean audioVibrate();

    Uri getRingtone(boolean video);

    int startExportService(@NonNull Service service, int progress);

    int startMigrationService(@NonNull Service service, boolean fullScreenActivity);

    void setDynamicShortcuts(@Nullable Map<Conversation, Descriptor> descriptors);

    void pushDynamicShortcut(@NonNull Originator originator, boolean incoming);

    void removeAllDynamicShortcuts();

    void acknowledgeReply(@NonNull UUID conversationId, @Nullable String reply);
}
