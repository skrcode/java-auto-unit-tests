package com.github.skrcode.javaautounittests.service;

public interface GenerationJobHandle {
    String runId();

    void cancel();

    boolean isFinished();
}
