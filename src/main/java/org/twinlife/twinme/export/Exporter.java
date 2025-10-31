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

import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.Descriptor;
import org.twinlife.twinlife.ConversationService.Descriptor.Type;
import org.twinlife.twinlife.ConversationService.FileDescriptor;
import org.twinlife.twinlife.ConversationService.ImageDescriptor;
import org.twinlife.twinlife.ConversationService.AudioDescriptor;
import org.twinlife.twinlife.ConversationService.VideoDescriptor;
import org.twinlife.twinlife.ConversationService.NamedFileDescriptor;
import org.twinlife.twinlife.ConversationService.ObjectDescriptor;
import org.twinlife.twinlife.ConversationService.Conversation;
import org.twinlife.twinlife.DisplayCallsMode;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.util.Utf8;
import org.twinlife.twinme.TwinmeContext;
import org.twinlife.twinme.models.Contact;
import org.twinlife.twinme.models.Group;
import org.twinlife.twinme.models.Originator;
import org.twinlife.twinme.models.Space;

import java.io.File;
import java.io.FileInputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.lingala.zip4j.io.outputstream.ZipOutputStream;
import net.lingala.zip4j.model.ZipParameters;

/**
 * Export conversations:
 *
 * - the exporter is created by the ExportExecutor when we start scanning the conversations to export.
 *   it is released immediately after the export has finished.
 * - the export() methods are called two times for a same contact/group.  A first time during a
 *   scanning pass where we collect media sizes and identify the names used to export contacts/groups
 *   and sender prefixes.
 * - the export() methods must be called from a dedicated export thread because the export process
 *   is a long running process and we must not block neither the UI thread nor the Twinlife execution thread.
 */
class Exporter {
    private static final String LOG_TAG = "Exporter";
    private static final boolean DEBUG = false;

    private static final int MAX_DESCRIPTORS = 50;

    static abstract class ObjectInfo implements Comparable<ObjectInfo> {
        final long date;
        @NonNull
        final String sender;

        ObjectInfo(long date, @NonNull String sender) {
            this.date = date;
            this.sender = sender;
        }

        @Override
        public int compareTo(ObjectInfo second) {

            return date < second.date ? -1 : 1;
        }

        @NonNull
        abstract String format(@NonNull DateFormat dateFormat);
    }

    static final class MessageInfo extends ObjectInfo {
        @NonNull
        final String text;

        MessageInfo(long date, @NonNull String sender, @NonNull String text) {
            super(date, sender);
            this.text = text;
        }

        @Override
        @NonNull
        String format(@NonNull DateFormat dateFormat) {
            /* [01/10/2019 20:39:34] Guillaume: Ok */
            final Date creationDate = new Date(date);
            return "[" + dateFormat.format(creationDate) + "] " + sender + ": " + text + "\r\n";
        }
    }

    static final class FileInfo extends ObjectInfo {
        @NonNull
        final String filename;

        FileInfo(long date, @NonNull String sender, @NonNull String filename) {
            super(date, sender);
            this.filename = filename;
        }

        @Override
        @NonNull
        String format(@NonNull DateFormat dateFormat) {
            /* [01/10/2019 20:39:34] Guillaume: Ok */
            final Date creationDate = new Date(date);
            return "[" + dateFormat.format(creationDate) + "] " + sender + ": File <" + filename + ">\r\n";
        }
    }

    private final Map<UUID, String> mDirNames;
    private final Set<String> mDirUsedNames;
    private final ConversationService mConversationService;
    @NonNull
    private Type[] mExportTypes;
    private long mBeforeDate;
    private final File mFilesDir;
    private final ExportObserver mObserver;
    private final boolean mStatAll;
    @NonNull
    private ExportStats mStats;
    @NonNull
    private volatile ExportState mExportState;
    private boolean mExportEnabled;
    private boolean mAddSpacePrefix;
    @Nullable
    private String mDirName;
    @NonNull
    private final DateFormat mDateFormat;
    @Nullable
    private List<ObjectInfo> mMessages;
    @Nullable
    private ZipOutputStream mZip;

    Exporter(@NonNull TwinmeContext twinmeContext, @NonNull ExportObserver observer, long beforeDate,
             @NonNull Type[] exportTypes, boolean statAll) {
        if (DEBUG) {
            Log.d(LOG_TAG, "ExportExecutor");
        }

        mDirNames = new HashMap<>();
        mConversationService = twinmeContext.getConversationService();
        mBeforeDate = beforeDate;
        mExportTypes = exportTypes;
        mStats = new ExportStats();
        mDirUsedNames = new HashSet<>();
        mExportEnabled = false;
        mFilesDir = twinmeContext.getFilesDir();
        mObserver = observer;
        mExportState = ExportState.EXPORT_READY;
        mStatAll = statAll;
        mDateFormat = DateFormat.getDateTimeInstance();
        mAddSpacePrefix = false;
    }

    /**
     * Set a new state to the exporter.
     *
     * @param state the new state.
     */
    void setState(@NonNull ExportState state) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setState state=" + state);
        }

        mExportState = state;
        if (state == ExportState.EXPORT_SCANNING) {
            mStats = new ExportStats();
        } else if (state == ExportState.EXPORT_EXPORTING) {
            mExportEnabled = true;
            mStats = new ExportStats();
        }
        reportProgress();
    }

    /**
     * Set a descriptor type filter to only export the specified types.
     *
     * @param types the list of descriptor types to export.
     */
    void setTypeFilter(@NonNull Type[] types) {
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
    void setDateFilter(long beforeDate) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setDateFilter beforeDate=" + beforeDate);
        }

        mBeforeDate = beforeDate;
    }

    /**
     * Set the ZIP output stream where files are exported.
     *
     * @param outputStream the ZIP output stream.
     */
    void setExportZIP(@NonNull ZipOutputStream outputStream) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setExportZIP outputStream=" + outputStream);
        }

        mZip = outputStream;
    }

    /**
     * When enabled, add the space name prefix in path names.
     * @param spacePrefix true to add the space name prefix in path names.
     */
    void setAddSpacePrefix(boolean spacePrefix) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setAddSpacePrefix spacePrefix=" + spacePrefix);
        }

        mAddSpacePrefix = spacePrefix;
    }

    /**
     * Export the contact conversation according to the selected filters.
     *
     * @param contacts the list of contacts to export.
     */
    void exportContacts(@NonNull List<Contact> contacts, @NonNull Map<Originator, Map<UUID, String>> members) {
        if (DEBUG) {
            Log.d(LOG_TAG, "exportContacts contacts=" + contacts);
        }

        for (final Contact contact : contacts) {
            final UUID twincodeOutboundId = contact.getTwincodeOutboundId();
            final String name = contact.getIdentityName();
            if (name == null || twincodeOutboundId == null) {

                continue;
            }

            final UUID peerTwincodeOutboundId = contact.getPeerTwincodeOutboundId();
            final String contactName = contact.getName();
            if (contactName == null || peerTwincodeOutboundId == null) {

                continue;
            }

            if (!mExportEnabled) {
                mDirNames.put(peerTwincodeOutboundId, getPrefix(contact) + buildName(contactName, mDirUsedNames));
            }

            // Export tree is:
            //  <space-name>/<contact-name>/<sender-name>_<sequence>.<ext>
            //  <contact-name>/<sender-name>_<sequence>.<ext>
            final String dirName = mDirNames.get(peerTwincodeOutboundId);
            if (dirName == null) {

                continue;
            }

            final Map<UUID, String> localNames = new HashMap<>();
            final Set<String> usedNames = new HashSet<>();
            final Map<UUID, String> roomMembers = members.get(contact);
            localNames.put(twincodeOutboundId, buildName(name, usedNames));
            localNames.put(peerTwincodeOutboundId, buildName(contactName, usedNames));
            export(dirName, contact, localNames, usedNames, roomMembers);
        }
    }

    /**
     * Export the group conversation according to the selected filters.
     *
     * @param groups the list of groups to export.
     * @param members a mapping of group members with their names.
     */
    void exportGroups(@NonNull List<Group> groups, @NonNull Map<Originator, Map<UUID, String>> members) {
        if (DEBUG) {
            Log.d(LOG_TAG, "exportGroups groups=" + groups);
        }

        for (Group group : groups) {
            final UUID twincodeOutboundId = group.getMemberTwincodeOutboundId();
            final String name = group.getIdentityName();
            if (name == null) {

                continue;
            }

            final UUID groupId = group.getGroupTwincodeOutboundId();
            final String groupName = group.getName();
            if (groupName == null || groupId == null) {

                continue;
            }

            if (!mExportEnabled) {
                mDirNames.put(groupId, getPrefix(group) + buildName(groupName, mDirUsedNames));
            }

            // Export tree is:
            //  <group-name>/<sender-name>_<sequence>.<ext>
            final String dirName = mDirNames.get(groupId);
            if (dirName == null) {

                continue;
            }

            final Map<UUID, String> localNames = new HashMap<>();
            final Set<String> usedNames = new HashSet<>();
            final Map<UUID, String> groupMembers = members.get(group);
            localNames.put(twincodeOutboundId, buildName(name, usedNames));
            export(dirName, group, localNames, usedNames, groupMembers);
        }
    }

    /**
     * Get a prefix that must be used to export the subject.
     * @param subject the subject (contact, group) to export.
     * @return the prefix.
     */
    @NonNull
    private String getPrefix(@NonNull Originator subject) {

        if (mAddSpacePrefix) {
            Space space = subject.getSpace();
            if (space != null) {
                return ExportExecutor.exportName(space.getName()) + "/";
            }
        }
        return "";
    }

    /**
     * Build a name to associate with a twincode and make sure names are valid to build file and directory
     * names and that they are unique.  Special characters are removed and duplicate names have a counter
     * added at the end.
     *
     * @param name the name to add.
     * @param usedNames a set of names which are already used in the current directory.
     * @return the name to use (unique and with special characters removed).
     */
    @NonNull
    private String buildName(@NonNull String name, @NonNull Set<String> usedNames) {
        if (DEBUG) {
            Log.d(LOG_TAG, "buildName name=" + name);
        }

        name = ExportExecutor.exportName(name);
        if (usedNames.contains(name)) {
            int count = 1;
            String newName;
            do {
                count++;
                newName = name + "_" + count;
            } while (usedNames.contains(newName));
            name = newName;
        }

        usedNames.add(name);
        return name;
    }

    /**
     * Export the conversation associated with our identity twincode.
     *
     * @param name the directory base name to export this conversation.
     * @param subject the repository object identifying the conversation to export.
     * @param names a mapping of twincodes to local prefix names to be used for file export.
     */
    private void export(@NonNull String name, @NonNull RepositoryObject subject, @NonNull Map<UUID, String> names,
                        @NonNull Set<String> usedNames, @Nullable Map<UUID, String> members) {
        if (DEBUG) {
            Log.d(LOG_TAG, "export subject=" + subject);
        }

        final Conversation conversation = mConversationService.getConversation(subject);
        if (conversation == null) {

            return;
        }
        UUID twincodeOutboundId = subject.getTwincodeOutbound().getId();

        if (members != null) {
            for (final Map.Entry<UUID, String> member : members.entrySet()) {
                final String memberName = member.getValue();
                if (memberName != null) {
                    names.put(member.getKey(), buildName(memberName, usedNames));
                }
            }
        }
        mStats.conversationCount++;
        reportProgress();
        mDirName = name;

        // Setup the mMessages array only when we export the messages.
        if (mExportEnabled ) {
            for (Type type : mExportTypes) {
                if (type == Type.OBJECT_DESCRIPTOR) {
                    mMessages = new ArrayList<>(100);
                    break;
                }
            }
        }

        final boolean checkCopy = mExportEnabled || !mStatAll;
        for (Type type : mExportTypes) {
            long beforeTimestamp = mBeforeDate;
            Type[] t = new Type[] { type };
            while (true) {
                final List<Descriptor> descriptors = mConversationService.getConversationTypeDescriptors(conversation, t,
                        DisplayCallsMode.ALL, beforeTimestamp, MAX_DESCRIPTORS);
                if (descriptors == null || descriptors.isEmpty()) {

                    break;
                }

                if (type == Type.OBJECT_DESCRIPTOR) {
                    for (final Descriptor descriptor : descriptors) {
                        if (mExportState == ExportState.EXPORT_ERROR) {
                            return;
                        }

                        if (checkCopy && (descriptor.isExpired() || descriptor.getDeletedTimestamp() > 0 || descriptor.getExpireTimeout() > 0)) {
                            continue;
                        }

                        // Make sure we have a name for this descriptor.
                        final UUID twincodeId = descriptor.getTwincodeOutboundId();
                        final String senderName = names.get(twincodeId);
                        if (checkCopy && senderName == null) {
                            continue;
                        }

                        // Don't export a descriptor that is protected against copies and we are not the owner.
                        final ObjectDescriptor objectDescriptor = (ObjectDescriptor) descriptor;
                        if (checkCopy && !objectDescriptor.isCopyAllowed() && !twincodeOutboundId.equals(twincodeId)) {
                            continue;
                        }

                        exportObject(objectDescriptor, senderName);
                        reportProgress();
                    }
                } else {
                    for (final Descriptor descriptor : descriptors) {
                        if (mExportState == ExportState.EXPORT_ERROR) {
                            return;
                        }

                        if (checkCopy && (descriptor.isExpired() || descriptor.getDeletedTimestamp() > 0 || descriptor.getExpireTimeout() > 0)) {
                            continue;
                        }

                        // Make sure we have a name for this descriptor.
                        final UUID twincodeId = descriptor.getTwincodeOutboundId();
                        final String senderName = names.get(twincodeId);
                        if (checkCopy && senderName == null) {
                            continue;
                        }

                        // Don't export a descriptor that is protected against copies and we are not the owner.
                        final FileDescriptor fileDescriptor = (FileDescriptor) descriptor;
                        if (checkCopy && !fileDescriptor.isCopyAllowed() && !twincodeOutboundId.equals(twincodeId)) {
                            continue;
                        }
                        if (checkCopy && !fileDescriptor.isAvailable()) {
                            continue;
                        }
                        final File path = new File(mFilesDir, fileDescriptor.getPath());
                        switch (type) {
                            case IMAGE_DESCRIPTOR:
                                exportImage(path, (ImageDescriptor) fileDescriptor, senderName);
                                break;

                            case AUDIO_DESCRIPTOR:
                                exportAudio(path, (AudioDescriptor) fileDescriptor, senderName);
                                break;

                            case VIDEO_DESCRIPTOR:
                                exportVideo(path, (VideoDescriptor) fileDescriptor, senderName);
                                break;

                            case NAMED_FILE_DESCRIPTOR:
                                exportNamedFile(path, (NamedFileDescriptor) fileDescriptor, senderName);
                                break;

                            default:
                                break;
                        }
                        reportProgress();
                    }
                }

                beforeTimestamp = descriptors.get(descriptors.size() - 1).getCreatedTimestamp();
            }
        }

        // Export the messages that were collected.  They are first sorted on the date and
        // written in creation date order.
        if (mMessages != null && !mMessages.isEmpty() && mZip != null) {

            Collections.sort(mMessages);

            final ZipParameters entry = new ZipParameters();
            entry.setFileNameInZip(mDirName + "/messages.txt");
            entry.setUnixMode(true);
            try {
                mZip.putNextEntry(entry);

                for (final ObjectInfo m : mMessages) {
                    final String line = m.format(mDateFormat);
                    mZip.write(Utf8.getBytes(line));
                }
                mZip.closeEntry();

            } catch (Exception exception) {
                if (DEBUG) {
                    Log.e(LOG_TAG, "Exception", exception);
                }
                Log.e(LOG_TAG, "Exception", exception);
            }
        }
        mMessages = null;
    }

    protected void exportObject(@NonNull ObjectDescriptor descriptor,
                                @NonNull String senderName) {
        if (DEBUG) {
            Log.d(LOG_TAG, "exportObject descriptor=" + descriptor + " senderName=" + senderName);
        }

        if (mMessages != null) {
            final String content = descriptor.getMessage();

            mMessages.add(new MessageInfo(descriptor.getCreatedTimestamp(), senderName, content));
        }
        mStats.msgCount++;
    }

    protected void exportFile(@NonNull File path, @NonNull FileDescriptor descriptor, @NonNull String senderName, boolean thumbnail) {
        if (DEBUG) {
            Log.d(LOG_TAG, "exportFile path=" + path + " descriptor=" + descriptor + " senderName=" + senderName);
        }

        if (mExportEnabled && mZip != null) {
            final String ext = descriptor.getExtension();
            final String suffix = thumbnail ? "-thumbnail." + ext : "." + ext;
            final String filename = senderName + "_" + descriptor.getSequenceId() + suffix;

            if (mMessages != null) {
                mMessages.add(new FileInfo(descriptor.getCreatedTimestamp(), senderName, filename));
            }

            final ZipParameters entry = new ZipParameters();
            entry.setFileNameInZip(mDirName + "/" + filename);
            entry.setUnixMode(true);
            entry.setLastModifiedFileTime(descriptor.getCreatedTimestamp());
            try (FileInputStream is = new FileInputStream(path)) {
                mZip.putNextEntry(entry);
                byte[] data = new byte[16384];
                int len;

                while ((len = is.read(data)) > 0) {
                    mZip.write(data, 0, len);
                }
                mZip.closeEntry();

            } catch (Exception exception) {
                Log.d(LOG_TAG, "cannot export file " + path);
            }
        }
    }

    protected void exportImage(@NonNull File path, @NonNull ImageDescriptor descriptor, @NonNull String senderName) {
        if (DEBUG) {
            Log.d(LOG_TAG, "exportImage path=" + path + " descriptor=" + descriptor + " senderName=" + senderName);
        }

        final File file;
        if (path.exists()) {
            mStats.imageSize += descriptor.getLength();
            file = path;
        } else {
            file = mConversationService.getDescriptorThumbnailFile(descriptor);
            if (file == null) {
                return;
            }
            mStats.imageSize += file.length();
        }
        mStats.imageCount++;
        exportFile(file, descriptor, senderName, file != path);
    }

    protected void exportVideo(@NonNull File path, @NonNull VideoDescriptor descriptor, @NonNull String senderName) {
        if (DEBUG) {
            Log.d(LOG_TAG, "exportVideo descriptor=" + descriptor + " senderName=" + senderName);
        }

        final File file;
        if (path.exists()) {
            mStats.videoSize += descriptor.getLength();
            file = path;
        } else {
            file = mConversationService.getDescriptorThumbnailFile(descriptor);
            if (file == null) {
                return;
            }
            mStats.videoSize += file.length();
        }
        mStats.videoCount++;
        exportFile(file, descriptor, senderName, file != path);
    }

    protected void exportAudio(@NonNull File path, @NonNull AudioDescriptor descriptor, @NonNull String senderName) {
        if (DEBUG) {
            Log.d(LOG_TAG, "exportAudio descriptor=" + descriptor + " senderName=" + senderName);
        }

        if (!path.exists()) {
            return;
        }
        mStats.audioCount++;
        mStats.audioSize += descriptor.getLength();
        exportFile(path, descriptor, senderName, false);
    }

    protected void exportNamedFile(@NonNull File path, @NonNull NamedFileDescriptor descriptor, @NonNull String senderName) {
        if (DEBUG) {
            Log.d(LOG_TAG, "exportNamedFile descriptor=" + descriptor + " senderName=" + senderName);
        }

        if (!path.exists()) {
            return;
        }
        mStats.fileCount++;
        mStats.fileSize += descriptor.getLength();
        exportFile(path, descriptor, senderName, false);
    }

    private void reportProgress() {
        if (DEBUG) {
            Log.d(LOG_TAG, "reportProgress");
        }

        mStats.totalSize = mStats.audioSize + mStats.imageSize + mStats.videoSize + mStats.fileSize;
        mObserver.onProgress(mExportState, mStats);
    }
}
