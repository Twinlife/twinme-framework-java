package org.twinlife.twinme.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * These capabilities are toggleable (ON/OFF)
 */
public enum ToggleableCap {
    PARSED("parsed", 1, false),
    ADMIN("admin", 1 << 1, false),
    DATA("data", 1 << 2),
    AUDIO("audio", 1 << 3),
    VIDEO("video", 1 << 4),
    ACCEPT_AUDIO("accept-audio", 1 << 5),
    ACCEPT_VIDEO("accept-video", 1 << 6),
    VISIBILITY("visibility", 1 << 7),
    OWNER("owner", 1 << 8, false),
    MODERATE("moderate", 1 << 9, false),
    INVITE("invite", 1 << 10),
    TRANSFER("transfer", 1 << 11, false),
    /**
     * Indicates whether to accept multiple incoming calls from this Originator.
     * Only applicable to call receivers for now.
     */
    GROUP_CALL("group-call", 1 << 12, false),
    AUTO_ANSWER_CALL("auto-answer-call", 1 << 13, false),
    /**
     * When enabled, the contact's identity will not be displayed in notifications.
     */
    DISCREET("discreet", 1 << 14, false),
    /**
     * When enabled, allow the peer to control the camera zoom without asking during a video call.
     */
    ZOOMABLE("zoomable", 1 << 15, false),
    /**
     * When enabled, strictly do not allow the peer to take control of the zoom during a video call.
     * ZOOMABLE and NOT_ZOOMABLE are used to build the Zoomable enum value.
     */
    NOT_ZOOMABLE("not-zoomable", 1 << 16, false);

    /**
     * Name of the capability.
     */
    @NonNull
    public final String label;

    /**
     * Internal representation of the capability.
     * This can change between versions.
     */
    public final long value;

    public final boolean enabledByDefault;

    ToggleableCap(@NonNull String label, long value) {
        this(label, value, true);
    }

    ToggleableCap(@NonNull String label, long value, boolean enabledByDefault) {
        this.label = label;
        this.value = value;
        this.enabledByDefault = enabledByDefault;
    }

    @Nullable
    static ToggleableCap getByLabel(@NonNull String label) {
        for (ToggleableCap capType : ToggleableCap.values()) {
            if (capType.label.equals(label)) {
                return capType;
            }
        }
        return null;
    }
}
