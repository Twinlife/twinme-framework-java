/*
 *  Copyright (c) 2023-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.export;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.Conversation;
import org.twinlife.twinlife.ConversationService.GroupConversation;
import org.twinlife.twinlife.ConversationService.GroupMemberConversation;
import org.twinlife.twinlife.ConversationService.Descriptor.Type;
import org.twinlife.twinlife.ConversationService.MemberFilter;
import org.twinlife.twinlife.Filter;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinme.TwinmeContext;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.executors.AbstractTwinmeExecutor;
import org.twinlife.twinme.models.Contact;
import org.twinlife.twinme.models.Group;
import org.twinlife.twinme.models.GroupMember;
import org.twinlife.twinme.models.Originator;
import org.twinlife.twinme.models.Space;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.lingala.zip4j.io.outputstream.ZipOutputStream;

import static org.twinlife.twinlife.BaseService.DEFAULT_REQUEST_ID;

/**
 * Public exporter to export contact and group conversations.
 *
 * - the ExportExecutor instance is created and associated with an ExportObserver.
 *   the observer will be called at different steps during the scanning and export process.
 * - the export filter is configured with setTypeFilter() and setDateFilter() to choose
 *   the descriptors and filter on the date.
 * - the scanning process is started by calling prepareContacts() or prepareGroups() or prepareSpace().
 *   during that process, conversations are scanned and the observer is called to report
 *   in the ExportStats some statistics about the export.  When the state reported is
 *   EXPORT_WAIT, the export stats reported by onProgress() indicates the expected size
 *   of the final export.
 * - the export process is started by calling runExport() and giving the directory where
 *   conversations are exported.  While exporting, the observer is also called with
 *   a new ExportStats that indicates the current export state.  When the final state
 *   reached EXPORT_DONE, the export process is finished.
 *
 * The scanning and export processes are executed from a dedicated thread.
 */
public final class ExportExecutor extends AbstractTwinmeExecutor {
    private static final String LOG_TAG = "ExportExecutor";
    private static final boolean DEBUG = false;

    private static final int GET_SPACES = 1;
    private static final int GET_SPACES_DONE = 1 << 1;
    private static final int GET_SPACE = 1 << 2;
    private static final int GET_SPACE_DONE = 1 << 3;
    private static final int GET_CONVERSATIONS = 1 << 4;
    private static final int GET_GROUP = 1 << 5;
    private static final int GET_GROUP_DONE = 1 << 6;
    private static final int GET_CONTACT = 1 << 7;
    private static final int GET_CONTACT_DONE = 1 << 8;
    private static final int GET_CONTACTS = 1 << 9;
    private static final int GET_CONTACTS_DONE = 1 << 10;
    private static final int GET_GROUPS = 1 << 11;
    private static final int GET_GROUPS_DONE = 1 << 12;
    private static final int LIST_GROUP_MEMBER = 1 << 13;
    private static final int GET_GROUP_MEMBER = 1 << 14;
    private static final int GET_GROUP_MEMBER_DONE = 1 << 15;
    private static final int EXPORT_PHASE_1 = 1 << 16;
    private static final int EXPORT_PHASE_1_DONE = 1 << 17;
    private static final int EXPORT_PHASE_2 = 1 << 18;
    private static final int EXPORT_PHASE_2_DONE = 1 << 19;

    private int mState = 0;
    private int mWork = 0;
    @Nullable
    private UUID mGroupId;
    @Nullable
    private UUID mContactId;
    @Nullable
    private UUID mSpaceId;
    private Space mSpace;
    @Nullable
    private List<Contact> mContacts;
    @Nullable
    private List<Group> mGroups;
    @Nullable
    private List<Space> mListSpaces;
    private boolean mAddSpacePrefix;
    private final Map<Originator, Map<UUID, String>> mGroupMembers;
    private final List<GroupMemberQuery> mGroupMemberList;
    private final boolean mStatAll;
    @Nullable
    private GroupMemberQuery mCurrentGroupMember;
    @Nullable
    private Exporter mExporter;
    @NonNull
    private final ExportObserver mObserver;
    private long mBeforeDate;
    @NonNull
    private Type[] mExportTypes = new Type[] {};
    private ZipOutputStream mExportZIP;
    @Nullable
    private ExecutorService mExecutor;
    @Nullable
    private final List<Conversation> mConversations;

    /**
     * Sanitize the name to get a valid export file name.
     *
     * @param name the space/group or contact name.
     * @return the name with special characters removed.
     */
    @NonNull
    public static String exportName(@NonNull String name) {

        return name.replaceAll("[|\\?*<\":>/']", "");
    }

    public ExportExecutor(@NonNull TwinmeContext twinmeContext, @NonNull ExportObserver observer, boolean statAll, boolean needConversations) {
        super((TwinmeContextImpl) twinmeContext, DEFAULT_REQUEST_ID, LOG_TAG);
        if (DEBUG) {
            Log.d(LOG_TAG, "ExportExecutor");
        }

        mObserver = observer;
        mGroupMemberList = new ArrayList<>();
        mGroupMembers = new HashMap<>();
        mBeforeDate = Long.MAX_VALUE;
        mStatAll = statAll;
        mAddSpacePrefix = false;
        if (needConversations) {
            mWork |= GET_CONVERSATIONS;
            mConversations = new ArrayList<>();
        } else {
            mConversations = null;
        }
    }

    @Nullable
    public Space getSpace() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getSpace");
        }

        return mSpace;
    }

    @Nullable
    public Contact getContact() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getContact");
        }

        if (mContacts == null || mContacts.isEmpty()) {
            return null;
        } else {
            return mContacts.get(0);
        }
    }

    @Nullable
    public Group getGroup() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getGroup");
        }

        if (mGroups == null || mGroups.isEmpty()) {
            return null;
        } else {
            return mGroups.get(0);
        }
    }

    /**
     * After execution of first phase, get the list of conversations that will be exported.
     *
     * @return the list of conversations.
     */
    @Nullable
    public List<Conversation> getConversations() {

        return mConversations;
    }

    /**
     * Set a descriptor type filter to only export the specified types.
     *
     * @param types the list of descriptor types to export.
     */
    public void setTypeFilter(@NonNull Type[] types) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setTypeFilter");
        }

        mExportTypes = types;
    }

    /**
     * Set a date filter to only export the descriptors before the date.
     *
     * @param beforeDate the date.
     */
    public void setDateFilter(long beforeDate) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setDateFilter beforeDate=" + beforeDate);
        }

        mBeforeDate = beforeDate;
    }

    /**
     * Prepare the export process to export the conversations of the contact.
     * The scanning process is started.
     *
     * @param contactId the contact to export.
     */
    public void prepareContact(@NonNull UUID contactId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "prepareContact contactId=" + contactId);
        }

        mContactId = contactId;
        mWork |= GET_CONTACT | LIST_GROUP_MEMBER | EXPORT_PHASE_1;
        mState &= ~(GET_CONTACT | EXPORT_PHASE_1 | EXPORT_PHASE_1_DONE);
        onOperation();
    }

    /**
     * Prepare the export process to export the conversations of the list of contacts.
     * The scanning process is started.
     *
     * @param contacts the list of contacts to export.
     */
    public void prepareContacts(@NonNull List<Contact> contacts) {
        if (DEBUG) {
            Log.d(LOG_TAG, "prepareContacts contacts=" + contacts);
        }

        mContacts = contacts;
        mWork |= LIST_GROUP_MEMBER | EXPORT_PHASE_1;
        mState &= ~(EXPORT_PHASE_1 | EXPORT_PHASE_1_DONE);
        onOperation();
    }

    /**
     * Prepare the export process to export the conversations of the group.
     * The scanning process is started.
     *
     * @param groupId the groupId to export.
     */
    public void prepareGroup(@NonNull UUID groupId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "prepareGroup groupId=" + groupId);
        }

        mGroupId = groupId;
        mWork |= GET_GROUP | LIST_GROUP_MEMBER | EXPORT_PHASE_1;
        mState &= ~(GET_GROUP | EXPORT_PHASE_1 | EXPORT_PHASE_1_DONE);
        onOperation();
    }

    /**
     * Prepare the export process to export the conversations of the list of groups.
     * The scanning process is started.
     *
     * @param groups the list of groups to export.
     */
    public void prepareGroups(@NonNull List<Group> groups) {
        if (DEBUG) {
            Log.d(LOG_TAG, "prepareGroups groups=" + groups);
        }

        mGroups = groups;
        mWork |= LIST_GROUP_MEMBER | EXPORT_PHASE_1;
        mState &= ~(EXPORT_PHASE_1 | EXPORT_PHASE_1_DONE);
        onOperation();
    }

    /**
     * Prepare the export process to export the conversations of the space.
     * The contacts and groups of the space is first retrieved and the scanning process is started.
     *
     * @param spaceId the spaceId to export or null for the default space.
     */
    public void prepareSpace(@Nullable UUID spaceId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "prepareSpace spaceId=" + spaceId);
        }

        mSpaceId = spaceId;
        mWork |= GET_SPACE | GET_CONTACTS | GET_GROUPS | LIST_GROUP_MEMBER | EXPORT_PHASE_1;
        mState &= ~(GET_SPACE | GET_CONTACTS | GET_CONTACTS_DONE | GET_GROUPS | GET_GROUPS_DONE | LIST_GROUP_MEMBER | GET_GROUP_MEMBER | GET_GROUP_MEMBER_DONE| EXPORT_PHASE_1 | EXPORT_PHASE_1_DONE);
        onOperation();
    }

    /**
     * Prepare the export process to export the conversations of the space.
     * The contacts and groups of the space is first retrieved and the scanning process is started.
     *
     * @param space the space to export.
     */
    public void prepareSpace(@NonNull Space space) {
        if (DEBUG) {
            Log.d(LOG_TAG, "prepareSpace space=" + space);
        }

        if (mConversations != null) {
            mConversations.clear();
        }
        mContacts = null;
        mGroups = null;
        mSpace = space;
        mGroupMemberList.clear();
        mWork |= GET_CONTACTS | GET_GROUPS | LIST_GROUP_MEMBER | EXPORT_PHASE_1;
        mState &= ~(GET_CONTACTS | GET_CONTACTS_DONE | GET_GROUPS | GET_GROUPS_DONE | LIST_GROUP_MEMBER | GET_GROUP_MEMBER | GET_GROUP_MEMBER_DONE| EXPORT_PHASE_1 | EXPORT_PHASE_1_DONE);
        onOperation();
    }

    /**
     * Prepare the export process for every visible space.  Secret spaces are ignored and must be entered manually.
     */
    public void prepareAll() {
        if (DEBUG) {
            Log.d(LOG_TAG, "prepareAll");
        }

        if (mConversations != null) {
            mConversations.clear();
        }
        mContacts = null;
        mGroups = null;
        mSpace = null;
        mGroupMemberList.clear();

        // The GET_CONTACTS, GET_GROUPS, LIST_GROUP_MEMBER will be activated from prepareSpace() when we find the list of spaces.
        mWork |= GET_SPACES | EXPORT_PHASE_1;
        mWork &= ~(GET_CONTACTS | GET_GROUPS | LIST_GROUP_MEMBER);
        mState &= ~(GET_SPACES | GET_SPACES_DONE | GET_CONTACTS | GET_CONTACTS_DONE | GET_GROUPS | GET_GROUPS_DONE | LIST_GROUP_MEMBER | GET_GROUP_MEMBER | GET_GROUP_MEMBER_DONE| EXPORT_PHASE_1 | EXPORT_PHASE_1_DONE);
        onOperation();
    }

    /**
     * After the prepare action and scanning process, export the selected conversations to
     * the given external directory.
     *
     * @param outputStream the ZIP output stream.
     */
    public void runExport(@NonNull ZipOutputStream outputStream) {
        if (DEBUG) {
            Log.d(LOG_TAG, "runExport outputStream=" + outputStream);
        }

        mWork |= EXPORT_PHASE_2;
        mState &= ~(EXPORT_PHASE_2 | EXPORT_PHASE_2_DONE);
        mExportZIP = outputStream;
        onOperation();
    }

    /**
     * Release the resources used by the exporter.
     */
    public void dispose() {
        if (DEBUG) {
            Log.d(LOG_TAG, "dispose");
        }

        stop();
    }

    /**
     * Internal methods.
     */

    protected void onOperation() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOperation");
        }

        //
        // Get the group members (each of them, one by one until we are done).
        //
        if (mCurrentGroupMember != null) {
            if ((mState & GET_GROUP_MEMBER) == 0) {
                mState |= GET_GROUP_MEMBER;

                mTwinmeContextImpl.getGroupMember(mCurrentGroupMember.mGroup, mCurrentGroupMember.mMemberTwincodeOutboundId,
                        (ErrorCode errorCode, GroupMember groupMember) -> {
                            if (errorCode == ErrorCode.SUCCESS && groupMember != null) {
                                Map<UUID, String> list = mGroupMembers.get(mCurrentGroupMember.mGroup);
                                if (list == null) {
                                    list = new HashMap<>();
                                    mGroupMembers.put(mCurrentGroupMember.mGroup, list);
                                }
                                list.put(groupMember.getPeerTwincodeOutboundId(), groupMember.getName());
                            }
                            if (!mGroupMemberList.isEmpty()) {
                                mState &= ~GET_GROUP_MEMBER;
                                mCurrentGroupMember = mGroupMemberList.remove(mGroupMemberList.size() - 1);
                            } else {
                                mState |= GET_GROUP_MEMBER_DONE;
                            }
                            onOperation();
                        });
                return;
            }
            if ((mState & GET_GROUP_MEMBER_DONE) == 0) {
                return;
            }
        }

        // We must get the space object.
        if ((mWork & GET_SPACE) != 0) {
            if ((mState & GET_SPACE) == 0) {
                mState |= GET_SPACE;
                if (mSpaceId != null) {
                    mTwinmeContextImpl.getSpace(mSpaceId, (ErrorCode errorCode, Space space) -> {
                        mState |= GET_SPACE_DONE;
                        mSpace = space;
                        onOperation();
                    });
                } else {
                    mTwinmeContextImpl.getCurrentSpace((ErrorCode errorCode, Space space) -> {
                        mState |= GET_SPACE_DONE;
                        mSpace = space;
                        onOperation();
                    });
                }
                return;
            }
            if ((mState & GET_SPACE_DONE) == 0) {
                return;
            }
        }

        // Optional step: after getting the space, get the list of conversations.
        if ((mWork & GET_CONVERSATIONS) != 0 && mConversations != null) {
            if ((mState & GET_CONVERSATIONS) == 0) {
                mState |= GET_CONVERSATIONS;

                final Filter<Conversation> filter = new Filter<>(this.mSpace);
                final List<Conversation> list = mTwinmeContextImpl.getConversationService().listConversations(filter);
                mConversations.addAll(list);
            }
        }

        // We must get the group object.
        if ((mWork & GET_GROUP) != 0) {
            if (mGroupId != null && (mState & GET_GROUP) == 0) {
                mState |= GET_GROUP;
                mTwinmeContextImpl.getGroup(mGroupId, (ErrorCode errorCode, Group group) -> {
                    mState |= GET_GROUP_DONE;
                    if (group != null) {
                        mGroups = Collections.singletonList(group);
                    }
                    onOperation();
                });
                return;
            }
            if ((mState & GET_GROUP_DONE) == 0) {
                return;
            }
        }

        // We must get the contact.
        if ((mWork & GET_CONTACT) != 0) {
            if (mContactId != null && (mState & GET_CONTACT) == 0) {
                mState |= GET_CONTACT;
                mTwinmeContextImpl.getContact(mContactId, (ErrorCode errorCode, Contact contact) -> {
                    mState |= GET_CONTACT_DONE;
                    mContacts = Collections.singletonList(contact);
                    onOperation();
                });
                return;
            }
            if ((mState & GET_CONTACT_DONE) == 0) {
                return;
            }
        }

        //
        // Optional step, get the list of contacts.
        //
        if ((mWork & GET_CONTACTS) != 0) {
            if ((mState & GET_CONTACTS) == 0) {
                mState |= GET_CONTACTS;

                final Filter<RepositoryObject> filter = new Filter<>(mSpace);
                mTwinmeContextImpl.findContacts(filter, (List<Contact> contacts) -> {
                    if (mContacts == null) {
                        mContacts = contacts;
                    } else {
                        mContacts.addAll(contacts);
                    }
                    mState |= GET_CONTACTS_DONE;
                    onOperation();
                });
                return;
            }
            if ((mState & GET_CONTACTS_DONE) == 0) {
                return;
            }
        }

        //
        // Optional step, get the list of groups.
        //
        if ((mWork & GET_GROUPS) != 0) {
            if ((mState & GET_GROUPS) == 0) {
                mState |= GET_GROUPS;

                final Filter<RepositoryObject> filter = new Filter<>(mSpace);
                mTwinmeContextImpl.findGroups(filter, (List<Group> groups) -> {
                    if (mGroups == null) {
                        mGroups = groups;
                    } else {
                        mGroups.addAll(groups);
                    }
                    mState |= GET_GROUPS_DONE;
                    onOperation();
                });
                return;
            }
            if ((mState & GET_GROUPS_DONE) == 0) {
                return;
            }
        }

        //
        // Optional step, get the list of group and twinroom members.
        //
        if ((mWork & LIST_GROUP_MEMBER) != 0) {
            if ((mState & LIST_GROUP_MEMBER) == 0) {
                mState |= LIST_GROUP_MEMBER;

                listGroupMembers();
                onOperation();
                return;
            }
        }

        //
        // Optional step, get the list of spaces (excluding the secret spaces).
        //
        if ((mWork & GET_SPACES) != 0) {
            if ((mState & GET_SPACES) == 0) {
                mState |= GET_SPACES;
                mTwinmeContextImpl.findSpaces((Space space) -> !space.isSecret(), (ErrorCode errorCode, List<Space> spaces) -> {
                    mState |= GET_SPACES_DONE;
                    mWork |= GET_CONTACTS | GET_GROUPS | LIST_GROUP_MEMBER;
                    mListSpaces = spaces;
                    mAddSpacePrefix = spaces != null && spaces.size() > 1;
                    onOperation();
                });
                return;
            }
            if ((mState & GET_SPACES_DONE) == 0) {
                return;
            }

            // Scan the space until our list becomes empty.
            if (mListSpaces != null && !mListSpaces.isEmpty()) {
                mSpace = mListSpaces.remove(0);
                mState &= ~(GET_CONTACTS | GET_CONTACTS_DONE | GET_GROUPS | GET_GROUPS_DONE | LIST_GROUP_MEMBER | GET_GROUP_MEMBER | GET_GROUP_MEMBER_DONE| EXPORT_PHASE_1 | EXPORT_PHASE_1_DONE);
                onOperation();
                return;
            }
        }

        //
        // Phase 1: scan the conversation.
        //
        if ((mWork & EXPORT_PHASE_1) != 0) {
            if ((mState & EXPORT_PHASE_1) == 0) {

                mState |= EXPORT_PHASE_1;

                if (mExporter == null) {
                    mExporter = new Exporter(mTwinmeContextImpl, mObserver, mBeforeDate, mExportTypes, mStatAll);
                }
                mExporter.setDateFilter(mBeforeDate);
                mExporter.setState(ExportState.EXPORT_SCANNING);
                mExporter.setAddSpacePrefix(mAddSpacePrefix);
                if (mExecutor == null) {
                    mExecutor = Executors.newSingleThreadExecutor();
                }
                mExecutor.execute(this::executeExport);
            }
            if ((mState & EXPORT_PHASE_1_DONE) == 0) {
                return;
            }
        }

        //
        // Phase 2: export the conversation.
        //
        if ((mWork & EXPORT_PHASE_2) != 0 && mExporter != null && mExecutor != null) {
            if ((mState & EXPORT_PHASE_2) == 0) {

                mState |= EXPORT_PHASE_2;
                mExporter.setTypeFilter(mExportTypes);
                mExporter.setDateFilter(mBeforeDate);
                mExporter.setExportZIP(mExportZIP);
                mExporter.setState(ExportState.EXPORT_EXPORTING);
                mExporter.setAddSpacePrefix(mAddSpacePrefix);

                mExecutor.execute(this::executeExport);
            }
            if ((mState & EXPORT_PHASE_2_DONE) == 0) {
                return;
            }

            synchronized (this) {
                if (mExecutor != null) {
                    mExecutor.shutdown();
                    mExecutor = null;
                }
                mExporter = null;
            }
        }
    }


    private void listGroupMembers() {
        if (DEBUG) {
            Log.d(LOG_TAG, "listGroupMembers");
        }

        final ConversationService conversationService = mTwinmeContextImpl.getConversationService();
        if (mContacts != null) {
            for (final Contact contact : mContacts) {
                if (!contact.isTwinroom()) {

                    continue;
                }

                final UUID twincodeOutboundId = contact.getTwincodeOutboundId();
                final UUID peerTwincodeOutboundId = contact.getPeerTwincodeOutboundId();
                final UUID twincodeInboundId = contact.getTwincodeInboundId();
                if (twincodeInboundId == null || twincodeOutboundId == null || peerTwincodeOutboundId == null) {

                    continue;
                }

                final ConversationService.Conversation conversation = conversationService.getConversation(contact);
                final Set<UUID> twincodes = conversationService.getConversationTwincodes(conversation, null, mBeforeDate);
                if (twincodes == null) {

                    continue;
                }

                for (UUID twincode : twincodes) {
                    mGroupMemberList.add(new GroupMemberQuery(contact, twincode));
                }
            }
        }

        if (mGroups != null) {
            for (final Group group : mGroups) {
                final Conversation conversation = conversationService.getConversation(group);
                if (!(conversation instanceof GroupConversation)) {
                    continue;
                }

                final GroupConversation groupConversation = (GroupConversation) conversation;
                final List<GroupMemberConversation> members = groupConversation.getGroupMembers(MemberFilter.JOINED_MEMBERS);
                for (final GroupMemberConversation member : members) {
                    mGroupMemberList.add(new GroupMemberQuery(group, member.getMemberTwincodeOutboundId()));
                }
            }
        }

        if (mGroupMemberList.isEmpty()) {
            mState |= GET_GROUP_MEMBER | GET_GROUP_MEMBER_DONE;
        } else {
            mState &= ~(GET_GROUP_MEMBER | GET_GROUP_MEMBER_DONE);
            mCurrentGroupMember = mGroupMemberList.remove(mGroupMemberList.size() - 1);
        }
    }

    private synchronized Exporter getExporter() {

        return mExporter;
    }

    private void executeExport() {
        if (DEBUG) {
            Log.d(LOG_TAG, "executeExport");
        }

        if (mContacts != null) {
            final Exporter exporter = getExporter();
            if (exporter != null) {
                exporter.exportContacts(mContacts, mGroupMembers);
            }
        }

        if (mGroups != null) {
            final Exporter exporter = getExporter();
            if (exporter == null) {

                return;
            }

            exporter.exportGroups(mGroups, mGroupMembers);
        }

        final Exporter exporter = getExporter();
        if (exporter == null) {

            return;
        }
        if ((mState & EXPORT_PHASE_2) == 0) {
            mTwinmeContextImpl.execute(() -> {
                exporter.setState(ExportState.EXPORT_WAIT);
                mState |= EXPORT_PHASE_1_DONE;
                onOperation();
            });
        } else {
            mTwinmeContextImpl.execute(() -> {
                exporter.setState(ExportState.EXPORT_DONE);
                mState |= EXPORT_PHASE_2_DONE;
                onOperation();
            });
        }
    }

    protected void stop() {
        if (DEBUG) {
            Log.d(LOG_TAG, "stop");
        }

        super.stop();

        // At this step, we must not have an exporter.  If it is running, stop it by
        // setting the state to EXPORT_ERROR.
        Exporter exporter = getExporter();
        if (exporter != null) {
            exporter.setState(ExportState.EXPORT_ERROR);
        }

        if (mExecutor != null) {
            mExecutor.shutdown();
            mExecutor = null;
        }
        mExporter = null;
    }

    private static final class GroupMemberQuery {
        final Originator mGroup;
        final UUID mMemberTwincodeOutboundId;

        GroupMemberQuery(Originator group, UUID memberTwincodeOutboundId) {
            mGroup = group;
            mMemberTwincodeOutboundId = memberTwincodeOutboundId;
        }
    }
}