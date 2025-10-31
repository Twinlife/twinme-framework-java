/*
 *  Copyright (c) 2014-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinme;

import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.Descriptor;
import org.twinlife.twinlife.ConversationService.InvitationDescriptor;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.ConversationService.DescriptorAnnotation;
import org.twinlife.twinlife.ConversationService.Conversation;
import org.twinlife.twinlife.ConversationService.GroupConversation;
import org.twinlife.twinlife.DisplayCallsMode;
import org.twinlife.twinlife.Filter;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.Notification;
import org.twinlife.twinlife.NotificationService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TrustMethod;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwincodeURI;
import org.twinlife.twinlife.TwinlifeContext;
import org.twinlife.twinme.actions.TwinmeAction;
import org.twinlife.twinme.models.CallReceiver;
import org.twinlife.twinme.models.Capabilities;
import org.twinlife.twinme.models.Contact;
import org.twinlife.twinme.models.AccountMigration;
import org.twinlife.twinme.models.Group;
import org.twinlife.twinme.models.GroupMember;
import org.twinlife.twinme.models.Invitation;
import org.twinlife.twinme.models.NotificationContent;
import org.twinlife.twinme.models.Originator;
import org.twinlife.twinme.models.Profile;
import org.twinlife.twinme.models.RoomConfig;
import org.twinlife.twinme.models.Space;
import org.twinlife.twinme.models.SpaceSettings;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("unused")
public interface TwinmeContext extends TwinlifeContext {

    String VERSION = BuildConfig.VERSION;

    String CONTACTS_CONTEXT_NAME = "contacts";
    String PROFILE_CONTEXT_NAME = "profile";
    String LEVELS_CONTEXT_NAME = "levels";

    long DEFAULT_TIMEOUT = 15000; // 15s to give enough time for connection to setup...

    interface Predicate<T> {
        boolean test(T object);
    }

    interface Consumer<T> {
        void accept(T object);
    }

    interface ConsumerWithError<T> extends org.twinlife.twinlife.Consumer<T> {
    }

    boolean ENABLE_REPORT_LOCATION = BuildConfig.ENABLE_REPORT_LOCATION;

    @SuppressWarnings("EmptyMethod")
    interface Observer extends TwinlifeContext.Observer {

        void onCreateProfile(long requestId, @NonNull Profile profile);

        void onUpdateProfile(long requestId, @NonNull Profile profile);

        void onDeleteProfile(long requestId, @NonNull UUID profileId);

        void onDeleteAccount(long requestId);

        void onCreateContact(long requestId, @NonNull Contact contact);

        void onDeleteContact(long requestId, @NonNull UUID contactId);

        void onUpdateContact(long requestId, @NonNull Contact contact);

        void onCreateInvitation(long requestId, @NonNull Invitation invitation);

        void onDeleteInvitation(long requestId, @NonNull UUID invitationId);

        void onCreateInvitationWithCode(long requestId, @NonNull Invitation invitation);

        void onGetInvitationCode(long requestId, @NonNull TwincodeOutbound twincodeOutbound, @Nullable String publicKey);

        void onCreateGroup(long requestId, @NonNull Group group, @NonNull GroupConversation conversation);

        void onUpdateGroup(long requestId, @NonNull Group group);

        void onDeleteGroup(long requestId, @NonNull UUID groupId);

        void onUpdateStats(long requestId, @NonNull List<Contact> updatedContacts, @NonNull List<Group> updatedGroups);

        void onDeleteLevel(long requestId);

        void onCreateSpace(long requestId, @NonNull Space space);

        void onUpdateSpace(long requestId, @NonNull Space space);

        void onSetCurrentSpace(long requestId, @NonNull Space space);

        void onDeleteSpace(long requestId, @NonNull UUID spaceId);

        void onMoveToSpace(long requestId, @NonNull Contact contact, @NonNull Space oldSpace);

        void onMoveToSpace(long requestId, @NonNull Group group, @NonNull Space oldSpace);

        void onAddNotification(@NonNull Notification notification);

        void onAcknowledgeNotification(long requestId, @NonNull Notification notification);

        void onDeleteNotification(long requestId, @NonNull UUID notificationId);

        void onUpdatePendingNotifications(long requestId, boolean hasPendingNotifications);

        void onUpdateAccountMigration(long requestId, @NonNull AccountMigration accountMigration);

        void onDeleteAccountMigration(long requestId, @NonNull UUID accountMigrationId);

        void onCreateCallReceiver(long requestId, @NonNull CallReceiver callReceiver);

        void onDeleteCallReceiver(long requestId, @NonNull UUID callReceiverId);

        void onUpdateCallReceiver(long requestId, @NonNull CallReceiver callReceiver);

        void onChangeCallReceiverTwincode(long requestId, @NonNull CallReceiver callReceiver);
    }

    class DefaultObserver extends TwinlifeContext.DefaultObserver implements Observer {

        @Override
        public void onCreateProfile(long requestId, @NonNull Profile profile) {
        }

        @Override
        public void onUpdateProfile(long requestId, @NonNull Profile profile) {
        }

        @Override
        public void onDeleteProfile(long requestId, @NonNull UUID profileId) {
        }

        @Override
        public void onDeleteAccount(long requestId) {
        }

        @Override
        public void onUpdateGroup(long requestId, @NonNull Group group) {
        }

        @Override
        public void onDeleteGroup(long requestId, @NonNull UUID groupId) {
        }

        @Override
        public void onUpdateStats(long requestId, @NonNull List<Contact> updatedContacts, @NonNull List<Group> updatedGroups) {
        }

        @Override
        public void onCreateContact(long requestId, @NonNull Contact contact) {
        }

        @Override
        public void onDeleteContact(long requestId, @NonNull UUID contactId) {
        }

        @Override
        public void onUpdateContact(long requestId, @NonNull Contact contact) {
        }

        @Override
        public void onCreateInvitation(long requestId, @NonNull Invitation invitation) {
        }

        @Override
        public void onDeleteInvitation(long requestId, @NonNull UUID invitationId) {
        }

        @Override
        public void onCreateInvitationWithCode(long requestId, @NonNull Invitation invitation) {
        }

        @Override
        public void onGetInvitationCode(long requestId, @NonNull TwincodeOutbound twincodeOutbound, @Nullable String publicKey) {
        }

        @Override
        public void onCreateGroup(long requestId, @NonNull Group group, @NonNull GroupConversation conversation) {
        }

        @Override
        public void onDeleteLevel(long requestId) {
        }

        @Override
        public void onCreateSpace(long requestId, @NonNull Space space) {
        }

        @Override
        public void onUpdateSpace(long requestId, @NonNull Space space) {
        }

        @Override
        public void onSetCurrentSpace(long requestId, @NonNull Space space) {
        }

        @Override
        public void onDeleteSpace(long requestId, @NonNull UUID spaceId) {
        }

        @Override
        public void onMoveToSpace(long requestId, @NonNull Contact contact, @NonNull Space oldSpace) {
        }

        @Override
        public void onMoveToSpace(long requestId, @NonNull Group group, @NonNull Space oldSpace) {
        }

        @Override
        public void onAddNotification(@NonNull Notification notification) {
        }

        @Override
        public void onAcknowledgeNotification(long requestId, @NonNull Notification notification) {
        }

        @Override
        public void onDeleteNotification(long requestId, @NonNull UUID notificationId) {
        }

        @Override
        public void onUpdatePendingNotifications(long requestId, boolean hasPendingNotifications) {
        }

        @Override
        public void onUpdateAccountMigration(long requestId, @NonNull AccountMigration accountMigration) {
        }

        @Override
        public void onDeleteAccountMigration(long requestId, @NonNull UUID accountMigrationId) {
        }

        @Override
        public void onCreateCallReceiver(long requestId, @NonNull CallReceiver callReceiver) {
        }

        @Override
        public void onDeleteCallReceiver(long requestId, @NonNull UUID callReceiverId) {
        }

        @Override
        public void onUpdateCallReceiver(long requestId, @NonNull CallReceiver callReceiver) {
        }

        @Override
        public void onChangeCallReceiverTwincode(long requestId, @NonNull CallReceiver callReceiver) {
        }
    }

    boolean isDatabaseUpgraded();

    //
    // Twincode management
    //

    // Register a twincode attribute name that must be copied from the Profile to the Contact when an identity is created.
    // The Profile attribute has the name 'name' while the Contact attribute has the name 'newName'.
    void registerSharedTwincodeAttribute(@NonNull String name, @NonNull String newName);

    //
    // Profile management
    //
    boolean isProfileTwincode(@NonNull UUID twincodeId);

    void createProfile(long requestId, @NonNull String name, @NonNull Bitmap avatar, @Nullable File avatarFile,
                       @Nullable String description, @Nullable Capabilities capabilities, @NonNull Space space);

    boolean isCurrentProfile(@NonNull Profile profile);

    boolean isSpaceProfile(@NonNull Profile profile);

    void getProfile(@NonNull UUID profileId, @NonNull ConsumerWithError<Profile> consumer);

    void getProfiles(long requestId, @NonNull Consumer<List<Profile>> consumer);

    void createProfile(long requestId, @NonNull String name, @NonNull Bitmap avatar, @Nullable File avatarFile,
                       @Nullable String description, @Nullable Capabilities capabilities);

    void updateProfile(long requestId, @NonNull Profile profile, @NonNull Profile.UpdateMode updateMode,
                       @NonNull String identityName, @Nullable Bitmap identityAvatar, @Nullable File identityAvatarFile,
                       @Nullable String description, @Nullable Capabilities capabilities);

    void changeProfileTwincode(@NonNull Profile profile, @NonNull ConsumerWithError<Profile> consumer);

    void deleteProfile(long requestId, @NonNull Profile profile);

    void deleteAccount(long requestId);

    //
    // Identity management
    //

    String getAnonymousName();

    Bitmap getAnonymousAvatar();

    Bitmap getDefaultAvatar();

    Bitmap getDefaultGroupAvatar();

    //
    // Contact management
    //

    void getContact(@NonNull UUID contactId, ConsumerWithError<Contact> consumer);

    void getOriginator(@NonNull UUID originatorId, ConsumerWithError<Originator> consumer);

    void getOriginator(@NonNull UUID originatorId, @Nullable UUID groupId, @NonNull ConsumerWithError<Originator> consumer);

    void findContacts(@NonNull Filter<RepositoryObject> filter, @NonNull Consumer<List<Contact>> consumer);

    void createContactPhase1(long requestId, @NonNull TwincodeOutbound peerTwincodeOutbound,
                             @Nullable Space space, @NonNull Profile profile, @Nullable GroupMember contactGroupMember);

    void createContactPhase1(long requestId, @NonNull TwincodeOutbound peerTwincodeOutbound,
                             @Nullable Space space, @NonNull String identityName, @NonNull ImageId identityAvatarId);

    void updateContact(long requestId, @NonNull Contact contact, @NonNull String contactName, @Nullable String description);

    void updateContactIdentity(long requestId, @NonNull Contact contact, @NonNull String identityName,
                               @NonNull Bitmap identityAvatar, @Nullable File identityAvatarFile,
                               @Nullable String description, @Nullable Capabilities capabilities, @Nullable Capabilities privateCapabilities);

    void updateContactIdentity(long requestId, @NonNull Contact contact, @NonNull String identityName,
                               @Nullable ImageId identityAvatarId, @Nullable String description,
                               @Nullable Capabilities capabilities, @Nullable Capabilities privateCapabilities);

    void unbindContact(long requestId, @Nullable UUID invocationId, @NonNull Contact contact);

    void verifyContact(@NonNull TwincodeURI twincodeURI, @NonNull TrustMethod trustMethod,
                       @NonNull ConsumerWithError<Contact> complete);

    void deleteContact(long requestId, @NonNull Contact contact);

    //
    // Contact invitation management
    //

    void createInvitation(long requestId, @Nullable GroupMember contactGroupMember);

    void createInvitation(long requestId, @NonNull Group group, long permissions);

    void createInvitation(long requestId, @NonNull Contact contact, @NonNull UUID sendTo);

    void updateInvitation(@NonNull Invitation invitation, @NonNull ConsumerWithError<Invitation> consumer);

    void getInvitation(@NonNull UUID invitationId, @NonNull ConsumerWithError<Invitation> consumer);

    void findInvitations(@NonNull Filter<RepositoryObject> filter, @NonNull Consumer<List<Invitation>> consumer);

    void deleteInvitation(long requestId, @NonNull Invitation invitation);

    //
    // Invitation code management
    //

    void createInvitationWithCode(long requestId, int validityPeriod);

    void getInvitationCode(long requestId, @NonNull String code);

    //
    // Group management
    //

    void getGroup(@NonNull UUID groupId, @NonNull ConsumerWithError<Group> consumer);

    void findGroups(@NonNull Filter<RepositoryObject> filter, @NonNull Consumer<List<Group>> consumer);

    void createGroup(long requestId, @NonNull String name, @Nullable String description, @Nullable Bitmap avatar, @Nullable File avatarFile);

    void createGroup(long requestId, @NonNull InvitationDescriptor invitation);

    void updateGroup(long requestId, @NonNull Group group, @NonNull String name, @Nullable String description, @Nullable Bitmap avatar, @Nullable File avatarFile, @Nullable Capabilities capabilities);

    void updateGroupProfile(long requestId, @NonNull Group group, @NonNull String name, @Nullable Bitmap avatar, @Nullable File avatarFile);

    void deleteGroup(long requestId, @NonNull Group group);

    void getGroupMember(@NonNull Originator group, @NonNull UUID groupMemberTwincodeId, @NonNull ConsumerWithError<GroupMember> consumer);

    void listGroupMembers(@NonNull Group group, @NonNull ConversationService.MemberFilter filter,
                          @NonNull ConsumerWithError<List<GroupMember>> consumer);

    void listMembers(@NonNull Originator subject, @NonNull List<UUID> memberTwincodeList,
                     @NonNull ConsumerWithError<List<GroupMember>> consumer);

    void updateScores(long requestId, boolean updateScore);

    //
    // Level management
    //

    void setLevel(long requestId, @NonNull String name);

    void createLevel(long requestId, @NonNull String name);

    void deleteLevel(long requestId, @NonNull String name);

    //
    // Space management
    //

    @NonNull
    Filter<RepositoryObject> createSpaceFilter();

    void getSpace(@NonNull UUID spaceId, ConsumerWithError<Space> consumer);

    void getCurrentSpace(ConsumerWithError<Space> consumer);

    void setCurrentSpace(long requestId, @NonNull String name);

    void setCurrentSpace(long requestId, @NonNull Space space);

    boolean isDefaultSpace(@NonNull Space space);

    void getDefaultSpace(ConsumerWithError<Space> consumer);

    void setDefaultSpace(@NonNull Space space);

    void setDefaultSpaceSettings(@NonNull SpaceSettings defaultSpaceSettings,
                                 @NonNull String oldDefaultLabel);

    void saveDefaultSpaceSettings(@NonNull SpaceSettings defaultSpaceSettings,
                                  @NonNull ConsumerWithError<SpaceSettings> consumer);

    @NonNull
    SpaceSettings getDefaultSpaceSettings();

    void createSpace(long requestId, @NonNull SpaceSettings settings, @Nullable Bitmap spaceAvatar, @Nullable File spaceAvatarFile);

    void createSpace(long requestId, @NonNull SpaceSettings settings, @Nullable Bitmap spaceAvatar, @Nullable File spaceAvatarFile,
                     @NonNull String name, @NonNull Bitmap avatar, @Nullable File avatarFile);

    void deleteSpace(long requestId, @NonNull Space space);

    void findSpaces(@NonNull Predicate<Space> predicate, @NonNull ConsumerWithError<List<Space>> consumer);

    void moveToSpace(long requestId, @NonNull Contact contact, @NonNull Space space);

    void moveToSpace(long requestId, @NonNull Group group, @NonNull Space space);

    void updateSpace(long requestId, @NonNull Space space, @NonNull SpaceSettings settings,
                     @Nullable Bitmap spaceAvatar, @Nullable File spaceAvatarFile);

    void updateSpace(long requestId, @NonNull Space space, @NonNull Profile profile);

    boolean isVisible(@Nullable Originator originator);

    boolean isCurrentSpace(@Nullable RepositoryObject originator);

    //
    // Conversation management
    //

    void findConversations(@NonNull Filter<Conversation> filter, @NonNull Consumer<List<Conversation>> consumer);

    void findConversationDescriptors(@NonNull Filter<Conversation> filter, @NonNull DisplayCallsMode callsMode,
                                     @NonNull Consumer<Map<Conversation, Descriptor>> consumer);

    void setActiveConversation(@NonNull Conversation conversation);

    void resetActiveConversation(@NonNull Conversation conversation);

    void markDescriptorRead(long requestId, @NonNull DescriptorId descriptorId);

    void markDescriptorDeleted(long requestId, @NonNull DescriptorId descriptorId);

    void deleteDescriptor(long requestId, @NonNull DescriptorId descriptorId);

    void forwardDescriptor(long requestId, @NonNull Conversation conversation, @Nullable UUID sendTo,
                           @NonNull DescriptorId descriptorId, boolean copyAllowed, long expireTimeout);

    void pushMessage(long requestId, @NonNull Conversation conversation, @Nullable UUID sendTo, @Nullable DescriptorId replyTo,
                     @NonNull String message, boolean copyAllowed, long expiration);

    void pushTransientObject(long requestId, @NonNull Conversation conversation, @NonNull Object object);

    void pushGeolocation(long requestId, @NonNull Conversation conversation, @Nullable UUID sendTo,
                         @Nullable DescriptorId replyTo, double longitude, double latitude, double altitude,
                         double mapLongitudeDelta, double mapLatitudeDelta, @Nullable Uri localMapPath, long expiration);

    void updateGeolocation(long requestId, @NonNull Conversation conversation, @NonNull DescriptorId descriptorId,
                           double longitude, double latitude, double altitude,
                           double mapLongitudeDelta, double mapLatitudeDelta, @Nullable Uri localMapPath);

    void saveGeolocationMap(long requestId, @NonNull Conversation conversation, @NonNull DescriptorId descriptorId,
                            @Nullable Uri localMapPath);

    void pushTwincode(long requestId, @NonNull Conversation conversation, @Nullable UUID sendTo, @Nullable DescriptorId replyTo,
                      @NonNull UUID twincodeId, @NonNull UUID schemaId, @Nullable String publicKey, boolean copyAllowed, long expiration);

    void withdrawInviteGroup(long requestId, @NonNull ConversationService.InvitationDescriptor descriptor);

    void deleteAnnotation(@NonNull DescriptorId descriptorId, @NonNull ConversationService.AnnotationType type);

    void toggleAnnotation(@NonNull DescriptorId descriptorId,
                          @NonNull ConversationService.AnnotationType type, int value);

    void listAnnotations(@NonNull DescriptorId descriptorId,
                         @NonNull ConsumerWithError<Map<TwincodeOutbound, DescriptorAnnotation>>consumer);

    void getDescriptor(@NonNull DescriptorId descriptorId, @NonNull ConsumerWithError<Descriptor> consumer);

    //
    // Room commands
    //
    void roomSetName(long requestId, @NonNull Contact contact, @NonNull String name);

    void roomSetImage(long requestId, @NonNull Contact contact, @NonNull Bitmap image, @Nullable File imageFile);

    void roomSetWelcome(long requestId, @NonNull Contact contact, @NonNull String message);

    void roomSetConfig(long requestId, @NonNull Contact contact, @NonNull RoomConfig config);

    void roomGetConfig(long requestId, @NonNull Contact contact);

    void roomChangeTwincode(long requestId, @NonNull Contact contact);

    void roomDeleteMessage(long requestId, @NonNull Contact contact, @NonNull ConversationService.DescriptorId messageId);

    void roomForwardMessage(long requestId, @NonNull Contact contact, @NonNull ConversationService.DescriptorId messageId);

    void roomBlockSender(long requestId, @NonNull Contact contact, @NonNull ConversationService.DescriptorId messageId);

    void roomDeleteMember(long requestId, @NonNull Contact contact, @NonNull UUID memberTwincodeOutboundId);

    void roomSignalMember(long requestId, @NonNull Contact contact, @NonNull UUID memberTwincodeOutboundId);

    void roomSetRoles(long requestId, @NonNull Contact contact, @NonNull String role, @NonNull List<UUID> members);

    void roomListMembers(long requestId, @NonNull Contact contact, @NonNull String filter);

    //
    // Account migration management
    //
    void createAccountMigration(@NonNull ConsumerWithError<AccountMigration> consumer);

    void getAccountMigration(@NonNull UUID deviceMigrationId, @NonNull ConsumerWithError<AccountMigration> consumer);

    void bindAccountMigration(@NonNull AccountMigration accountMigration, @NonNull TwincodeOutbound peerTwincodeOutbound,
                              @NonNull ConsumerWithError<AccountMigration> consumer);

    void deleteAccountMigration(@NonNull AccountMigration accountMigration, @NonNull ConsumerWithError<UUID> consumer);

    //
    // Call Receiver management
    //

    void createCallReceiver(long requestId, @NonNull Space space, @Nullable String name,
                            @Nullable String description,
                            @Nullable String identityName, @Nullable String identityDescription,
                            @Nullable Bitmap avatar, @Nullable File avatarFile,
                            @Nullable Capabilities capabilities, @NonNull Consumer<CallReceiver> consumer);

    void getCallReceiver(@NonNull UUID callReceiverId, @NonNull ConsumerWithError<CallReceiver> consumer);

    void findCallReceivers(@NonNull Filter<RepositoryObject> filter, @NonNull Consumer<List<CallReceiver>> consumer);

    void deleteCallReceiver(long requestId, @NonNull CallReceiver callReceiver);

    void updateCallReceiver(long requestId, @NonNull CallReceiver callReceiver, @NonNull String name, @Nullable String description, @NonNull String identityName,
                            @Nullable String identityDescription, @Nullable Bitmap identityAvatar, @Nullable File identityAvatarFile,
                            @Nullable Capabilities capabilities);

    void changeCallReceiverTwincode(long requestId, @NonNull CallReceiver callReceiver);

    //
    // Notification management
    //

    @NonNull
    NotificationCenter getNotificationCenter();

    void getNotification(@NonNull UUID notificationId, @NonNull ConsumerWithError<Notification> consumer);

    void findNotifications(@NonNull Filter<Notification> filter, int maxDescriptors, @NonNull Consumer<List<Notification>> consumer);

    @Nullable
    Notification createNotification(@NonNull NotificationService.NotificationType notificationType,
                                    int notificationId, @NonNull Originator subject,
                                    @Nullable DescriptorId descriptorId,
                                    @Nullable TwincodeOutbound annotatingUser);

    void acknowledgeNotification(long requestId, @NonNull Notification notification);

    void deleteNotification(long requestId, @NonNull Notification notification);

    void getSpaceNotificationStats(@NonNull ConsumerWithError<NotificationService.NotificationStat> consumer);

    void getNotificationStats(@NonNull ConsumerWithError<Map<Space, NotificationService.NotificationStat>> consumer);

    //
    // System Notification management
    //

    @Nullable
    NotificationContent systemNotification(@NonNull android.content.Context context, @NonNull Map<String, String> parameters);

    void reportStats(long requestId);

    //
    // Timeout based actions
    //

    void startAction(@NonNull TwinmeAction action);

    void finishAction(@NonNull TwinmeAction action);

    void setDynamicShortcuts();

    void removeAllDynamicShortcuts();
}
