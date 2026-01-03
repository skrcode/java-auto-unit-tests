// Copyright © 2025 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.skrcode.javaautounittests.constants.GenerationType;
import com.github.skrcode.javaautounittests.dto.Message;
import com.github.skrcode.javaautounittests.dto.PromptResponseOutput;
import com.github.skrcode.javaautounittests.state.AISettings;
import com.github.skrcode.javaautounittests.util.ConsolePrinter;
import com.github.skrcode.javaautounittests.util.Telemetry;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.progress.ProgressIndicator;
import org.apache.commons.collections.CollectionUtils;
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
    private static final String GENERATE_URL = API_HOST+"invoke-junit-llm";
    private static final String PLAN_URL = API_HOST+"invoke-junit-llm-fetch-plan";
    private static final int POLL_SLEEP_MILLIS = 2000;
    private static final int MAX_POLLING_TIME_MILLIS = 450000;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private static final ExecutorService BULK_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    public static final String USER_ROLE = "user", MODEL_ROLE = "assistant";

    private GenerateTestsLLMService() {}

    // guarantees all the output is correct, if not throw exception
    public static List<Message> generate(String combinedClassName, List<List<Message>> requests, ConsoleView myConsole, int attempt, ProgressIndicator indicator, GenerationType generationType) throws Exception {
        indicator.checkCanceled();
        Telemetry.bulkStart(requests.size());
        long bulkStart = System.nanoTime();
        List<CompletableFuture<PromptResponseOutput>> futures = requests.stream()
                .map(request -> CompletableFuture.supplyAsync(() -> executeRequest(combinedClassName, request, myConsole, attempt, indicator, generationType), BULK_EXECUTOR))
                .toList();
        List<PromptResponseOutput> outputs = new ArrayList<>(requests.size());
        int ok = 0;
        try {
            for (CompletableFuture<PromptResponseOutput> future : futures) {
                outputs.add(future.join());
                ok++;
            }
            return outputs;
        } catch (CompletionException ce) {
            futures.forEach(future -> future.cancel(true));
            Throwable cause = ce.getCause();
            if (cause instanceof Exception e) throw e;
            throw new Exception(cause);
        } finally {
            Telemetry.bulkDone(ok, (System.nanoTime() - bulkStart) / 1_000_000);
        }
    }
    private static PromptResponseOutput executeRequest(String combinedClassName, List<Message> messages, ConsoleView myConsole, int attempt, ProgressIndicator indicator, GenerationType generationType) throws CompletionException {
        long start = System.nanoTime();
        Telemetry.genStarted(combinedClassName, String.valueOf(attempt));
        int retries = 0;
        long backoffMillis = 1000; // start with 1s
        List<Message> safeMessages = messages == null ? Collections.emptyList() : messages;
        while (true) {
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("attemptNumber", attempt);
                body.put("messages", safeMessages);
                String requestJson = MAPPER.writeValueAsString(body);
                String headerValue = "Bearer " + AISettings.getInstance().getProKey();
                indicator.checkCanceled();
                HttpRequest createJobReq = buildInitialRequest(requestJson, headerValue, generationType);
                HttpResponse<String> createJobResp = HTTP_CLIENT.send(createJobReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (createJobResp.statusCode() / 100 == 4) {
                    PromptResponseOutput promptResponseOutput = new PromptResponseOutput();
                    promptResponseOutput.setErrorCode(createJobResp.statusCode());
                    promptResponseOutput.setErrorBody(createJobResp.body());
                    return promptResponseOutput;
                }
                if (createJobResp.statusCode() / 100 != 2) throw new Exception("Error : " + createJobResp.statusCode() + " " + createJobResp.body());
                PromptResponseOutput result;
                if(generationType != null) result = handleResponse(createJobResp, headerValue, indicator);
                else {
                    JsonNode jsonNode = MAPPER.readTree(createJobResp.body());
                    result = MAPPER.treeToValue(jsonNode, PromptResponseOutput.class);
                    if(result == null || result.getMessages() == null || CollectionUtils.isEmpty(result.getMessages())) throw new Exception("Error in generating plan");
                }
                Telemetry.genCompleted(combinedClassName, String.valueOf(attempt), (System.nanoTime() - start) / 1_000_000);
                ConsolePrinter.success(myConsole, "Received model output");
                return result;
            } catch (Throwable t) {
                indicator.checkCanceled();
                retries++;
                if (retries > MAX_RETRIES) {
                    Telemetry.genFailed(combinedClassName, String.valueOf(attempt), t.getMessage());
                    ConsolePrinter.error(myConsole,
                            "Request failed after " + retries + " retries: " + t.getMessage());
                    throw new CompletionException("Max retries reached: " + t.getMessage(), t);
                }
                long sleepMillis = backoffMillis + (long) (Math.random() * 250); // jitter
                ConsolePrinter.warn(myConsole, "Retrying request (" + retries + "/" + MAX_RETRIES + ") after " + sleepMillis + "ms: " + t.getMessage());
                try {Thread.sleep(sleepMillis);}
                catch (Exception e) {throw new CompletionException(e);}
                backoffMillis = Math.min(backoffMillis * 2, 30_000); // cap at 30s
            }
        }
    }

    private static HttpRequest buildInitialRequest(String requestJson, String headerValue, GenerationType generationType) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", headerValue == null ? "" : headerValue)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8));
        if(generationType != null) return builder.uri(URI.create(GENERATE_URL + "?type=" + generationType)).build();
        return builder.uri(URI.create(PLAN_URL)).build();
    }

    private static PromptResponseOutput handleResponse(HttpResponse<String> createJobResp, String headerValue, ProgressIndicator indicator) throws Exception {
        JsonNode createJobJson = MAPPER.readTree(createJobResp.body());
        String jobId = createJobJson.get("jobId").asText();
        int pollTime = 0;
        while (true) {
            indicator.checkCanceled();
            if(pollTime > MAX_POLLING_TIME_MILLIS) throw new Exception("Job timed out after " + MAX_POLLING_TIME_MILLIS + "seconds");
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
                if(out.getMessages() == null) throw new Exception("Empty Response from server");
                return out;
            } else if ("error".equalsIgnoreCase(status)) throw new Exception("Job failed: " + pollJson.get("output").asText());
            pollTime += POLL_SLEEP_MILLIS;
            Thread.sleep(POLL_SLEEP_MILLIS);
        }
    }

}
