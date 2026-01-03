/*
 * Copyright © 2025 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.skrcode.javaautounittests.dto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class CacheDTO {

    /** Fully qualified class name (stable key) */
    public String classFqn;

    public String createdAt;
    public String updatedAt;

    /** Relative paths / FQNs of files fetched via get_file */
    public List<String> files = new ArrayList<>();

    public CacheDTO() {}

    public CacheDTO(String classFqn) {
        this.classFqn = classFqn;
        this.createdAt = now();
        this.updatedAt = this.createdAt;
    }

    public void touch() {
        this.updatedAt = now();
    }

    private static String now() {
        return OffsetDateTime.now().toString();
    }
}
