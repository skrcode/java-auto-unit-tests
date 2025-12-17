/*
 * Copyright © 2025 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.skrcode.javaautounittests.DTOs;

import java.util.ArrayList;
import java.util.List;

public class CacheDTO {
    public String className;
    public String createdAt;
    public String updatedAt;
    public List<String> files = new ArrayList<>();

    public CacheDTO() {}

    public CacheDTO(String className) {
        this.className = className;
        this.createdAt = now();
        this.updatedAt = this.createdAt;
    }

    public static String now() {
        return java.time.OffsetDateTime.now().toString();
    }
}
