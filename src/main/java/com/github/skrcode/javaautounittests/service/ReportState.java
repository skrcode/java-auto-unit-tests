package com.github.skrcode.javaautounittests.service;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores last run status keyed by test FQN.
 * You can persist this later if you want.
 */

public final class ReportState {
    public static final class TestStatus {
        public volatile int failureCount = 0;
    }

    private final Map<String, TestStatus> statusByTestFqn = new ConcurrentHashMap<>();

    public TestStatus getOrCreate(String testFqn) {
        return statusByTestFqn.computeIfAbsent(testFqn, k -> new TestStatus());
    }

    public TestStatus get(String testFqn) {
        return statusByTestFqn.get(testFqn);
    }
}
