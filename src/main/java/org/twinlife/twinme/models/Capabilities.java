/*
 *  Copyright (c) 2021-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinme.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.util.Utils;
import org.twinlife.twinme.TwinmeContext;
import org.twinlife.twinme.models.schedule.Schedule;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Twinme capabilities.
 * <p>
 * The Capabilities describes the features which are supported by a twincode.  The UI can use it to know
 * which operations are supported so that it hides the operations not available.  Capabilities are also
 * enforced either on the Openfire server or on the receiving side (ie, the twincode owner).
 * <p>
 * The Capabilities comes within the `capabilities` twincode attributes in the form of a multi-line string.
 * Each line describes a single capability.
 */
public class Capabilities {
    // Name of non-toggleable capabilities.
    private static final String CAP_NAME_CLASS = "class";
    private static final String CAP_NAME_SCHEDULE = "schedule";
    private static final String CAP_TRUSTED = "trusted";

    // The default capabilities.
    private static final long CAP_DEFAULT = ToggleableCap.PARSED.value |
            ToggleableCap.DATA.value |
            ToggleableCap.AUDIO.value |
            ToggleableCap.VIDEO.value |
            ToggleableCap.ACCEPT_AUDIO.value |
            ToggleableCap.ACCEPT_VIDEO.value |
            ToggleableCap.VISIBILITY.value |
            ToggleableCap.INVITE.value;

    private static final long CAP_NO_CALL =
            ~(ToggleableCap.AUDIO.value |
            ToggleableCap.VIDEO.value |
            ToggleableCap.ACCEPT_AUDIO.value |
            ToggleableCap.ACCEPT_VIDEO.value);

    private static final Map<TwincodeKind, Long> overrideCaps = new EnumMap<>(TwincodeKind.class);

    static {
        overrideCaps.put(TwincodeKind.GROUP, CAP_NO_CALL);
        overrideCaps.put(TwincodeKind.GROUP_MEMBER, CAP_NO_CALL);
        overrideCaps.put(TwincodeKind.ACCOUNT_MIGRATION, CAP_NO_CALL);
        overrideCaps.put(TwincodeKind.SPACE, CAP_NO_CALL);
        overrideCaps.put(TwincodeKind.INVITATION, CAP_NO_CALL & ~ToggleableCap.DATA.value);
    }

    @Nullable
    private String mCapabilities;
    private long mFlags;
    @Nullable
    private TwincodeKind mKind;

    @Nullable
    private Schedule mSchedule;
    @Nullable
    private String mTrusted;

    public Capabilities() {

        mCapabilities = null;
        mFlags = 0;
    }

    public Capabilities(@NonNull String capabilities) {

        mCapabilities = capabilities;
        mFlags = 0;
    }

    public Capabilities(@NonNull TwincodeKind kind, boolean isAdmin) {

        mCapabilities = null;
        mFlags = CAP_DEFAULT;
        if (isAdmin) {
            mFlags |= ToggleableCap.ADMIN.value;
        }
        mKind = kind;
        update();
    }

    /**
     * Returns the type of twincode.
     *
     * @return the type of twincode.
     */
    @NonNull
    public TwincodeKind getKind() {

        parse();
        return mKind == null ? TwincodeKind.CONTACT : mKind;
    }

    /**
     * Returns true if the owner is admin
     *
     * @return true if admin is enabled.
     */
    public boolean hasAdmin() {
        return hasCap(ToggleableCap.ADMIN);
    }

    /**
     * Returns true if the owner can moderate chats.
     *
     * @return true if moderate capability is enabled.
     */
    public boolean hasModerate() {
        return hasCap(ToggleableCap.MODERATE);
    }

    /**
     * Returns true if an audio call is possible.
     *
     * @return true if audio call is possible.
     */
    public boolean hasAudio() {
        return hasCap(ToggleableCap.AUDIO);
    }

    /**
     * Returns true if the target can receive audio stream.
     *
     * @return true if the target can receive audio stream.
     */
    public boolean hasAudioReceiver() {
        return hasCap(ToggleableCap.ACCEPT_AUDIO);
    }

    /**
     * Returns true if a video call is possible.
     *
     * @return true if video call is possible.
     */
    public boolean hasVideo() {
        return hasCap(ToggleableCap.VIDEO);
    }

    /**
     * Returns true if the target can receive video stream.
     *
     * @return true if the target can receive video stream.
     */
    public boolean hasVideoReceiver() {
        return hasCap(ToggleableCap.ACCEPT_VIDEO);
    }

    /**
     * Returns true if opening data channel is possible.
     *
     * @return true if opening data channel is possible.
     */
    public boolean hasData() {
        return hasCap(ToggleableCap.DATA);
    }

    /**
     * Returns true if the owner is visible (in twinroom list members by non-admin).
     *
     * @return true if the owner is visible.
     */
    public boolean hasVisibility() {
        return hasCap(ToggleableCap.VISIBILITY);
    }

    /**
     * Returns true if the owner accepts contact invitations.
     *
     * @return true if the owner accepts contact invitations.
     */
    public boolean hasAcceptInvitation() {
        return hasCap(ToggleableCap.INVITE);
    }

    /**
     * Returns true if transferring calls to the owner is allowed.
     *
     * @return true if transferring calls to the owner is allowed.
     */
    public boolean hasTransfer() {
        return hasCap(ToggleableCap.TRANSFER);
    }

    public boolean hasGroupCall() {
        return hasCap(ToggleableCap.GROUP_CALL);
    }

    public boolean hasAutoAnswerCall() {
        return hasCap(ToggleableCap.AUTO_ANSWER_CALL);
    }

    public boolean hasDiscreet() {
        return hasCap(ToggleableCap.DISCREET);
    }

    /**
     * Returns the Zoomable enum telling if the peer can take control of the zoom during a video call.
     * @return NEVER if the peer is not allowed (no need to ask), ALLOW if the peer is allowed without
     * asking and ASK if a confirmation is required (the default).
     */
    @NonNull
    public Zoomable getZoomable() {
        parse();
        if (hasCap(ToggleableCap.NOT_ZOOMABLE)) {
            return Zoomable.NEVER;
        }
        if (hasCap(ToggleableCap.ZOOMABLE)) {
            return Zoomable.ALLOW;
        }
        return Zoomable.ASK;
    }

    private boolean hasCap(@NonNull ToggleableCap cap) {

        parse();
        return (mFlags & cap.value) != 0;
    }

    @Nullable
    public String toAttributeValue() {

        return mCapabilities;
    }

    /**
     * Set or clear the admin capability.
     *
     * @param value the new admin capability.
     */
    public void setCapAdmin(boolean value) {

        parse();
        changeCapability(ToggleableCap.ADMIN, !value);
        update();
    }

    /**
     * Set or clear the moderate capability.
     *
     * @param value the new moderate capability.
     */
    public void setCapModerate(boolean value) {

        parse();
        changeCapability(ToggleableCap.MODERATE, !value);
        update();
    }

    /**
     * Set or clear the audio capability.
     *
     * @param value the new audio capability.
     */
    public void setCapAudio(boolean value) {

        parse();
        changeCapability(ToggleableCap.AUDIO, !value);
        update();
    }

    /**
     * Set or clear the video capability.
     *
     * @param value the new video capability.
     */
    public void setCapVideo(boolean value) {

        parse();
        changeCapability(ToggleableCap.VIDEO, !value);
        update();
    }

    /**
     * Set or clear the data capability.
     *
     * @param value the new data capability.
     */
    public void setCapData(boolean value) {

        parse();
        changeCapability(ToggleableCap.DATA, !value);
        update();
    }

    /**
     * Set or clear the visibility capability.
     *
     * @param value the new visibility capability.
     */
    public void setCapVisibility(boolean value) {

        parse();
        changeCapability(ToggleableCap.VISIBILITY, !value);
        update();
    }

    /**
     * Set or clear the accept contact invitation capability.
     *
     * @param value the new accept contact invitation capability.
     */
    public void setCapAcceptInvitation(boolean value) {

        parse();
        changeCapability(ToggleableCap.INVITE, !value);
        update();
    }

    /**
     * Set or clear the transfer capability.
     *
     * @param value the new transfer capability.
     */
    public void setCapTransfer(boolean value) {

        parse();
        changeCapability(ToggleableCap.TRANSFER, !value);
        update();
    }

    /**
     * Set or clear the group-call capability.
     *
     * @param value the new group-call capability.
     */
    public void setCapGroupCall(boolean value) {
        parse();
        changeCapability(ToggleableCap.GROUP_CALL, !value);
        update();
    }

    public void setCapAutoAnswerCall(boolean value) {
        parse();
        changeCapability(ToggleableCap.AUTO_ANSWER_CALL, !value);
        update();
    }

    public void setCapDiscreet(boolean value) {
        parse();
        changeCapability(ToggleableCap.DISCREET, !value);
        update();
    }

    public void setZoomable(@NonNull Zoomable zoomable) {
        parse();
        switch (zoomable) {
            case NEVER:
                changeCapability(ToggleableCap.NOT_ZOOMABLE, false);
                changeCapability(ToggleableCap.ZOOMABLE, true);
                break;

            case ALLOW:
                changeCapability(ToggleableCap.NOT_ZOOMABLE, true);
                changeCapability(ToggleableCap.ZOOMABLE, false);
                break;

            default:
                changeCapability(ToggleableCap.NOT_ZOOMABLE, true);
                changeCapability(ToggleableCap.ZOOMABLE, true);
                break;
        }
        update();
    }

    /**
     * Set the new twincode class.
     *
     * @param kind the twincode class.
     */
    public void setKind(@NonNull TwincodeKind kind) {

        parse();
        mKind = kind;
        update();
    }

    @Nullable
    public Schedule getSchedule() {
        parse();
        return mSchedule;
    }

    /**
     * Updates the schedule and regenerates the serialized capabilities.
     * <p>
     * Does nothing if no schedule is defined.
     * @param updater applied to the current schedule.
     */
    public void updateSchedule(TwinmeContext.Consumer<Schedule> updater){
        if(mSchedule != null) {
            updater.accept(mSchedule);
            update();
        }
    }

    public void setSchedule(@Nullable Schedule schedule) {
        parse();
        mSchedule = schedule;
        update();
    }

    /**
     * Get the peer twincode that is trusted.
     *
     * @return the trusted peer twincode or null.
     */
    @Nullable
    public UUID getTrusted() {

        parse();
        return Utils.toUUID(mTrusted);
    }

    /**
     * Check if the given UUID is trusted by the peer.
     *
     * @param uuid the twincode uuid to check.
     * @return true if the twincode is trusted.
     */
    public boolean isTrusted(@NonNull UUID uuid) {

        return uuid.equals(getTrusted());
    }

    /**
     * Set the trusted twincode.
     *
     * @param peerTwincodeId the peer twincode we trust.
     */
    public void setTrusted(@Nullable UUID peerTwincodeId) {

        parse();
        mTrusted = peerTwincodeId == null ? null : peerTwincodeId.toString();
        update();
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        return "Capabilities:\n" +
                " capabilities=" + mCapabilities + "\n";
    }

    @Override
    public boolean equals(Object object) {

        if (this == object) {
            return true;
        }

        if (!(object instanceof Capabilities)) {
            return false;
        }

        Capabilities second = (Capabilities) object;
        if (mCapabilities == null) {
            return second.mCapabilities == null;
        }

        return mCapabilities.equals(second.mCapabilities);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mCapabilities);
    }

    private void parse() {

        if ((mFlags & ToggleableCap.PARSED.value) != 0) {
            return;
        }

        mFlags |= CAP_DEFAULT;
        if (mCapabilities == null) {
            return;
        }

        String[] lines = mCapabilities.split("\n");
        for (String line : lines) {
            int pos = line.indexOf('=');
            String capName;
            String capValue;
            if (pos > 0) {
                capName = line.substring(0, pos);
                capValue = line.substring(pos + 1);
            } else {
                capName = line;
                capValue = null;
            }

            if (capName.equals(CAP_NAME_CLASS)) {
                if (capValue != null) {
                    mKind = TwincodeKind.getByValue(capValue);
                    Long override = overrideCaps.get(mKind);
                    if (override != null) {
                        mFlags &= override;
                    }
                }
            } else if (capName.equals(CAP_NAME_SCHEDULE)){
                if(capValue != null) {
                    mSchedule = Schedule.ofCapability(capValue);
                }
            } else if (capName.equals(CAP_TRUSTED)){
                if (capValue != null) {
                    mTrusted = capValue;
                }
            } else {
                boolean removeMode = false;

                if (capName.startsWith("!")) {
                    removeMode = true;
                    capName = capName.substring(1);
                }

                ToggleableCap toggleableCap = ToggleableCap.getByLabel(capName);
                if (toggleableCap != null) {
                    changeCapability(toggleableCap, removeMode);
                }
            }
        }
    }


    private void changeCapability(ToggleableCap cap, boolean remove) {

        if (remove) {
            mFlags &= ~cap.value;
        } else {
            mFlags |= cap.value;
        }
    }

    private void update() {

        if (mFlags == CAP_DEFAULT && (mKind == TwincodeKind.CONTACT) && mSchedule == null && mTrusted == null) {
            mCapabilities = null;
            return;
        }

        StringBuilder builder = new StringBuilder();
        if (mKind != null && mKind != TwincodeKind.CONTACT) {
            builder.append(CAP_NAME_CLASS);
            builder.append('=');
            builder.append(mKind.value);
        }

        for (ToggleableCap cap : ToggleableCap.values()) {
            if (cap == ToggleableCap.PARSED) {
                //Internal marker, should not be serialized.
                continue;
            }
            if (cap.enabledByDefault) {
                if ((mFlags & cap.value) == 0) {
                    if (builder.length() > 0) {
                        builder.append("\n");
                    }
                    builder.append('!');
                    builder.append(cap.label);
                }
            } else {
                if ((mFlags & cap.value) != 0) {
                    if (builder.length() > 0) {
                        builder.append("\n");
                    }
                    builder.append(cap.label);
                }
            }
        }

        if (mSchedule != null) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(CAP_NAME_SCHEDULE);
            builder.append('=');
            builder.append(mSchedule.toCapability());
        }
        if (mTrusted != null) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(CAP_TRUSTED);
            builder.append('=');
            builder.append(mTrusted);
        }

        mCapabilities = builder.toString();
    }
}
