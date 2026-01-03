//// Copyright © 2025 Suraj Rajan / JAIPilot
//// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
//// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
//
//package com.github.skrcode.javaautounittests.util.auth;
//
//import java.io.UnsupportedEncodingException;
//import java.net.URI;
//import java.net.URLDecoder;
//import java.net.URLEncoder;
//import java.nio.charset.StandardCharsets;
//import java.util.LinkedHashMap;
//import java.util.Map;
//
//public final class Urls {
//    private Urls() {}
//
//    public static String urlEncode(String v) {
//        try {
//            return URLEncoder.encode(v, StandardCharsets.UTF_8.toString());
//        } catch (UnsupportedEncodingException e) {
//            return v;
//        }
//    }
//
//    public static Map<String, String> splitQuery(URI uri) {
//        Map<String, String> queryPairs = new LinkedHashMap<>();
//        String query = uri.getRawQuery();
//        if (query == null || query.isEmpty()) {
//            return queryPairs;
//        }
//        String[] pairs = query.split("&");
//        for (String pair : pairs) {
//            int idx = pair.indexOf("=");
//            if (idx < 0) continue;
//            String key = decode(pair.substring(0, idx));
//            String value = decode(pair.substring(idx + 1));
//            queryPairs.put(key, value);
//        }
//        return queryPairs;
//    }
//
//    private static String decode(String v) {
//        try {
//            return URLDecoder.decode(v, StandardCharsets.UTF_8.toString());
//        } catch (Exception e) {
//            return v;
//        }
//    }
//}
