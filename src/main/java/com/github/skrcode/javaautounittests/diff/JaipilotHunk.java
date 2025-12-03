/*
 * Copyright © 2025 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.skrcode.javaautounittests.diff;

public class JaipilotHunk {
    /** Character offsets into originalSource */
    public final int startOffset;
    public final int endOffset;

    /** Original and replacement text for this region */
    public final String beforeText;
    public final String afterText;

    /** Human explanation of why this change was made */
    public final String reason;

    public JaipilotHunk(int startOffset,
                        int endOffset,
                        String beforeText,
                        String afterText,
                        String reason) {
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.beforeText = beforeText;
        this.afterText = afterText;
        this.reason = reason;
    }
}
