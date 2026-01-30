// Copyright © 2026 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.skrcode.javaautounittests.constants.GenerationType;
import com.github.skrcode.javaautounittests.dto.Message;
import com.github.skrcode.javaautounittests.dto.PromptResponseOutput;
import com.github.skrcode.javaautounittests.state.AISettings;
import com.github.skrcode.javaautounittests.util.ConsolePrinter;
import com.github.skrcode.javaautounittests.util.Telemetry;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.progress.ProgressIndicator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GenerateTestsLLMService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String API_HOST = "https://otxfylhjrlaesjagfhfi.supabase.co/functions/v1/";
    private static final int MAX_RETRIES = 10;
    private static final int DEFAULT_BATCH_SIZE = 5;
    private static final String GENERATE_URL = API_HOST+"invoke-junit-llm";
//    private static final String PLAN_URL = API_HOST+"invoke-junit-llm-fetch-plan";
    private static final int POLL_SLEEP_MILLIS = 2000;
    private static final int MAX_POLLING_TIME_MILLIS = 450000;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private static final ExecutorService BULK_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    public static final String USER_ROLE = "user", MODEL_ROLE = "assistant";

    private GenerateTestsLLMService() {}

    public static List<Message> generate(String combinedClassName, List<List<Message>> requests, ConsoleView myConsole, int attempt, ProgressIndicator indicator, List<GenerationType> generationTypeDuringGeneration) throws Exception {
        Telemetry.bulkStart(requests.size());
        long bulkStart = System.nanoTime();
        indicator.checkCanceled();
        List<Message> responses = new ArrayList<>(Collections.nCopies(requests.size(), null));
        List<String> failures = new ArrayList<>();
        List<CompletableFuture<BatchResult>> futures = new ArrayList<>();
        for (int start = 0; start < requests.size(); start += DEFAULT_BATCH_SIZE) {
            int from = start;
            int to = Math.min(start + DEFAULT_BATCH_SIZE, requests.size());
            List<List<Message>> batch = requests.subList(from, to);
            futures.add(CompletableFuture.supplyAsync(() -> processBatch(combinedClassName, batch, from, myConsole, attempt, indicator, generationTypeDuringGeneration), BULK_EXECUTOR));
        }
        try {
            for (CompletableFuture<BatchResult> future : futures) {
                BatchResult result = future.join();
                result.responses.forEach(responses::set);
                failures.addAll(result.failures);
            }
            if (!failures.isEmpty()) throw new Exception("Failed to generate for entities - " + String.join("; ", failures));
            return responses;
        } finally {
            Telemetry.bulkDone(requests.size() - failures.size(), (System.nanoTime() - bulkStart) / 1_000_000);
        }
    }

    private static BatchResult processBatch(String combinedClassName, List<List<Message>> batch, int startIndex, ConsoleView myConsole, int attempt, ProgressIndicator indicator, List<GenerationType> generationTypeDuringGeneration) {
        Map<Integer, Message> batchResponses = new HashMap<>();
        List<String> failures = new ArrayList<>();

        List<Integer> payloadPositions = new ArrayList<>();
        List<List<Message>> payload = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            List<Message> request = batch.get(i);
            if (request == null) {
                batchResponses.put(startIndex + i, null);
                continue;
            }
            payloadPositions.add(i);
            payload.add(request);
        }
        if (payload.isEmpty()) return new BatchResult(batchResponses, failures);

        List<GenerationType> batchGenerationTypes = null;
        if (generationTypeDuringGeneration != null) {
            batchGenerationTypes = new ArrayList<>(payload.size());
            for (int position : payloadPositions) {
                int globalIndex = startIndex + position;
                if (globalIndex >= generationTypeDuringGeneration.size()) {
                    throw new IllegalArgumentException("generationTypeDuringGeneration size mismatch with requests");
                }
                batchGenerationTypes.add(generationTypeDuringGeneration.get(globalIndex));
            }
        }

        long start = System.nanoTime();
        Telemetry.genStarted(combinedClassName, String.valueOf(attempt));
        long backoffMillis = 1000;
        for (int retries = 0;; retries++) {
            try {
                PromptResponseOutput output = sendRequest(attempt, indicator, payload, batchGenerationTypes);
                Telemetry.genCompleted(combinedClassName, String.valueOf(attempt), (System.nanoTime() - start) / 1_000_000);
                if (output == null) throw new Exception("Empty response from server");

                int errorCategory = output.getErrorCode() / 100;
                List<Message> outputMessages = output.getMessages();
                if (errorCategory == 4) {
                    String body = output.getErrorBody() == null ? "Client error" : output.getErrorBody();
                    for (int pos : payloadPositions) failures.add("entity " + (startIndex + pos) + ": " + body);
                    return new BatchResult(batchResponses, failures);
                }
                if (outputMessages == null || outputMessages.size() != payload.size())
                    throw new Exception("Mismatched response count");

                for (int j = 0; j < outputMessages.size(); j++) {
                    int entityIndex = startIndex + payloadPositions.get(j);
                    Message msg = outputMessages.get(j);
                    if (msg == null) {
                        failures.add("entity " + entityIndex + ": Empty response from server");
                        batchResponses.put(entityIndex, null);
                    } else {
                        batchResponses.put(entityIndex, msg);
                    }
                }
                return new BatchResult(batchResponses, failures);
            } catch (Throwable t) {
                indicator.checkCanceled();
                if (retries >= MAX_RETRIES) {
                    Telemetry.genFailed(combinedClassName, String.valueOf(attempt), t.getMessage());
                    String errorMsg = "Request failed after " + retries + " retries: " + t.getMessage();
                    ConsolePrinter.error(myConsole, errorMsg);
                    for (int pos : payloadPositions) failures.add("entity " + (startIndex + pos) + ": " + errorMsg);
                    return new BatchResult(batchResponses, failures);
                }
                long sleepMillis = backoffMillis + (long) (Math.random() * 250); // jitter
                ConsolePrinter.warn(myConsole, "Retrying request (" + retries + "/" + MAX_RETRIES + ") after " + sleepMillis + "ms: " + t.getMessage());
                try {
                    Thread.sleep(sleepMillis);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
                backoffMillis = Math.min(backoffMillis * 2, 30_000); // cap at 30s
            }
        }
    }

    private static PromptResponseOutput sendRequest(int attempt, ProgressIndicator indicator, List<List<Message>> messages, List<GenerationType> generationTypeDuringGeneration) throws Exception {
        Map<String, Object> body = Map.of("attemptNumber", attempt, "requests", messages, "generationTypeDuringGeneration", generationTypeDuringGeneration);
        String requestJson = MAPPER.writeValueAsString(body);
        String headerValue = "Bearer " + AISettings.getInstance().getProKey();
        indicator.checkCanceled();

        HttpRequest createJobReq = buildInitialRequest(requestJson, headerValue);
        HttpResponse<String> createJobResp = HTTP_CLIENT.send(createJobReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (createJobResp.statusCode() / 100 == 4) {
            PromptResponseOutput promptResponseOutput = new PromptResponseOutput();
            promptResponseOutput.setErrorCode(createJobResp.statusCode());
            promptResponseOutput.setErrorBody(createJobResp.body());
            return promptResponseOutput;
        }
        if (createJobResp.statusCode() / 100 != 2) throw new Exception("Error : " + createJobResp.statusCode() + " " + createJobResp.body());

        if (generationTypeDuringGeneration != null) return handleResponse(createJobResp, headerValue, indicator);

        JsonNode jsonNode = MAPPER.readTree(createJobResp.body());
        PromptResponseOutput result = MAPPER.treeToValue(jsonNode, PromptResponseOutput.class);
        if (result == null || result.getMessages() == null || result.getMessages().isEmpty()) throw new Exception("Error in generating plan");
        return result;
    }

    private static HttpRequest buildInitialRequest(String requestJson, String headerValue) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", headerValue == null ? "" : headerValue)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8));
        return builder.uri(URI.create(GENERATE_URL)).build();
    }

    private static PromptResponseOutput handleResponse(HttpResponse<String> createJobResp, String headerValue, ProgressIndicator indicator) throws Exception {
        JsonNode createJobJson = MAPPER.readTree(createJobResp.body());
        String jobId = createJobJson.get("jobId").asText();
        for (int pollTime = 0; pollTime <= MAX_POLLING_TIME_MILLIS; pollTime += POLL_SLEEP_MILLIS) {
            indicator.checkCanceled();
            HttpRequest pollReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://otxfylhjrlaesjagfhfi.supabase.co/functions/v1/fetch-job?id=" + jobId))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .header("Authorization", headerValue == null ? "" : headerValue)
                    .GET()
                    .build();
            HttpResponse<String> pollResp = HTTP_CLIENT.send(pollReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (pollResp.statusCode() / 100 != 2) throw new Exception("API error (job-status): " + pollResp.statusCode() + " " + pollResp.body());
            JsonNode pollJson = MAPPER.readTree(pollResp.body());
            String status = pollJson.get("status").asText();
            if ("done".equalsIgnoreCase(status)) {
                String output = pollJson.get("output").asText();
                PromptResponseOutput out = MAPPER.readValue(output, PromptResponseOutput.class);
                if (out.getMessages() == null || out.getMessages().isEmpty()) throw new Exception("Empty Response from server");
                return out;
            } else if ("error".equalsIgnoreCase(status)) throw new Exception("Job failed: " + pollJson.get("output").asText());
            Thread.sleep(POLL_SLEEP_MILLIS);
        }
        throw new Exception("Job timed out after " + MAX_POLLING_TIME_MILLIS + "seconds");
    }

    private record BatchResult(Map<Integer, Message> responses, List<String> failures) {}
}
