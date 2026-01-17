//// Copyright © 2026 Suraj Rajan / JAIPilot
//// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
//// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
//
//package com.github.skrcode.javaautounittests.util.auth;
//
//import com.github.skrcode.javaautounittests.state.AISettings;
//import org.jetbrains.annotations.NotNull;
//
//import java.net.http.HttpRequest;
//
///**
// * Small helper to attach the Supabase OAuth token (stored from the JAIPilot website login)
// * to outgoing requests so Edge Functions can validate Authorization headers.
// */
//public final class SupabaseAuth {
//    private SupabaseAuth() {}
//
//    public static String getBearerToken() {
//        SupabaseAuthManager.ensureFreshAccessToken();
//        AISettings settings = AISettings.getInstance();
//        String token = settings.getAccessToken();
//        if (token.isEmpty()) {
//            token = settings.getProKey(); // fallback for legacy keys
//        }
//        return token == null ? "" : token.trim();
//    }
//
//    public static HttpRequest.Builder withAuth(@NotNull HttpRequest.Builder builder) {
//        String token = getBearerToken();
//        if (!token.isEmpty()) {
//            builder.header("Authorization", "Bearer " + token);
//        }
//        return builder;
//    }
//}
