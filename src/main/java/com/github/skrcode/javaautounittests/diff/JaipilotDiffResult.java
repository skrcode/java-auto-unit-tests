/*
 * Copyright © 2025 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.skrcode.javaautounittests.diff;

import java.util.List;

public class JaipilotDiffResult {
    public final String originalSource;
    public final String improvedSource;
    public final List<JaipilotHunk> hunks;

    public JaipilotDiffResult(String originalSource,
                              String improvedSource,
                              List<JaipilotHunk> hunks) {
        this.originalSource = originalSource;
        this.improvedSource = improvedSource;
        this.hunks = hunks;
    }
}
