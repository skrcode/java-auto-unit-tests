/*
 * Copyright © 2025 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.skrcode.javaautounittests.DTOs;

public class TestWriteResult {
    public final String error;      // null if no error
    public final String source;     // null if error or nothing written

    public TestWriteResult(String error, String source) {
        this.error = error;
        this.source = source;
    }

    public static TestWriteResult success(String source) {
        return new TestWriteResult(null, source);
    }

    public static TestWriteResult error(String msg) {
        return new TestWriteResult(msg, null);
    }
}
