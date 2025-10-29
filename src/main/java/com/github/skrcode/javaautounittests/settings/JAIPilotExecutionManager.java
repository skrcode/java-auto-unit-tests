// Copyright © 2025 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.settings;

public final class JAIPilotExecutionManager {
    private static volatile boolean cancelled = false;

    public static void cancel() {
        cancelled = true;
    }

    public static boolean isCancelled() {
        return cancelled;
    }

    public static void reset() {
        cancelled = false;
    }
}
