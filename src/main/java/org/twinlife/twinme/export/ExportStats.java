/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.export;

import androidx.annotation.NonNull;

import java.io.Serializable;

/**
 * Statistics about the export process.
 */
public final class ExportStats implements Serializable {
    public long conversationCount;
    public long imageCount;
    public long imageSize;
    public long videoCount;
    public long videoSize;
    public long fileCount;
    public long fileSize;
    public long audioCount;
    public long audioSize;
    public long msgCount;
    public long totalSize;

    @Override
    @NonNull
    public String toString() {

        StringBuilder sb = new StringBuilder();
        sb.append("stats:{conversationCount:");
        sb.append(conversationCount);
        sb.append(", totSize:");
        sb.append(totalSize);
        sb.append(", msgCount:");
        sb.append(msgCount);
        if (fileCount > 0) {
            sb.append(", fileCount:");
            sb.append(fileCount);
            sb.append(", fileSize:");
            sb.append(fileSize);
        }
        if (imageCount > 0) {
            sb.append(", imageCount:");
            sb.append(imageCount);
            sb.append(", imageSize:");
            sb.append(imageSize);
        }
        if (audioCount > 0) {
            sb.append(", audioCount:");
            sb.append(audioCount);
            sb.append(", audioSize:");
            sb.append(audioSize);
        }
        if (videoCount > 0) {
            sb.append(", videoCount:");
            sb.append(videoCount);
            sb.append(", videoSize:");
            sb.append(videoSize);
        }
        sb.append("}");
        return sb.toString();
    }
}
