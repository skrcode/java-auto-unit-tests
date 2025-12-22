package com.github.skrcode.javaautounittests.report;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores last run status keyed by test FQN.
 * You can persist this later if you want.
 */
public final class JaipilotReportState {
    public static final class TestStatus {
        public volatile TestRunStatus status = TestRunStatus.NEVER_RUN;
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
