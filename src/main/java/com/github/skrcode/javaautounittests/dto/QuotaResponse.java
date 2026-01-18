/*
 * Copyright © 2026 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.skrcode.javaautounittests.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties
public class QuotaResponse {
    public Integer quotaUsed;
    public Integer quotaTotal;
    public Integer quotaRemaining;
    public String message;
    public String error;

    public QuotaResponse() {
        this.quotaUsed = 0;
        this.quotaRemaining = 0;
        this.quotaTotal = 0;
    }
}
