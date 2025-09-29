package com.github.skrcode.javaautounittests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.skrcode.javaautounittests.DTOs.Content;
import com.github.skrcode.javaautounittests.DTOs.PromptResponseOutput;
import com.github.skrcode.javaautounittests.settings.AISettings;
import com.github.skrcode.javaautounittests.settings.ConsolePrinter;
import com.github.skrcode.javaautounittests.settings.telemetry.Telemetry;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified blocking façade over JAIPilot LLM API (Pro & Non-Pro).
 * Uses one API, returns streamed JSON output.
 */
public final class JAIPilotLLM {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_RETRIES = 10;

    private JAIPilotLLM() {}

    public static Content getContextSourceContent(String classSource) {
        // Explicitly wrap the CUT source string (may be used separately from context)
        return new Content(
                "user",
                List.of(new Content.Part(classSource))
        );
    }


    public static Content getExistingTestClassContent(String existingTestSource) {
        return new Content(
                "user",
                List.of(new Content.Part(existingTestSource))
        );
    }
    public static Content getMockitoVersionContent(Project project) {
        return new Content(
                "user",
                List.of(new Content.Part("Mockito version in project: " + CUTUtil.findMockitoVersion(project)))
        );
    }
    public static Content getOutputContent(String output) {
        // Wrap compiler/test runner output or error messages
        return new Content(
                "user",
                List.of(new Content.Part(output == null ? "" : output))
        );
    }

    public static Content getInputClassContent(String classSource) {
        // Explicitly wrap the CUT source string (may be used separately from context)
        return new Content(
                "user",
                List.of(new Content.Part(classSource))
        );
    }

    public static PromptResponseOutput generateContent(
            String testClassName,
            List<Content> contents,
            ConsoleView myConsole,
            int attempt
            , @NotNull ProgressIndicator indicator
    ) throws Exception {
        long start = System.nanoTime();
        Telemetry.genStarted(testClassName, String.valueOf(attempt));

        int retries = 0;
        long backoffMillis = 1000; // start with 1s

        while (true) {
            try {
                // --- 1. Create Job ---
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("attemptNumber", attempt);
                body.put("contents", contents);
                String requestJson = MAPPER.writeValueAsString(body);

                // Select key based on mode
                String headerName;
                String headerValue;
                String key = AISettings.getInstance().getProKey();
                headerName = "Authorization";
                headerValue = "Bearer " + key;

                HttpClient http = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(30))
                        .build();

                indicator.checkCanceled();
                HttpRequest createJobReq = HttpRequest.newBuilder()
                        .uri(URI.create("https://otxfylhjrlaesjagfhfi.supabase.co/functions/v1/invoke-junit-llm-stream"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .header(headerName, headerValue == null ? "" : headerValue)
                        .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> createJobResp =
                        http.send(createJobReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                if (createJobResp.statusCode() / 100 == 4) {
                    PromptResponseOutput promptResponseOutput = new PromptResponseOutput();
                    promptResponseOutput.setErrorCode(createJobResp.statusCode());
                    promptResponseOutput.setErrorBody(createJobResp.body());
                    return promptResponseOutput;
                }
                if (createJobResp.statusCode() / 100 != 2) {
                    throw new RuntimeException("Error : " +
                            createJobResp.statusCode() + " " + createJobResp.body());
                }

                JsonNode createJobJson = MAPPER.readTree(createJobResp.body());
                String jobId = createJobJson.get("jobId").asText();

                // --- 2. Poll until done ---
                int pollTime = 0;
                int sleepTime = 5000; // 5 s
                int maxPollingTime = 450000; // 450 s
                while (true) {
                    indicator.checkCanceled();
                    if(pollTime > maxPollingTime) {
                        throw new RuntimeException("Job timed out after " + maxPollingTime + "seconds");
                    }
                    HttpRequest pollReq = HttpRequest.newBuilder()
                            .uri(URI.create("https://otxfylhjrlaesjagfhfi.supabase.co/functions/v1/fetch-job?id=" + jobId))
                            .timeout(Duration.ofSeconds(30))
                            .header("Accept", "application/json")
                            .header(headerName, headerValue == null ? "" : headerValue)
                            .GET()
                            .build();

                    HttpResponse<String> pollResp =
                            http.send(pollReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                    if (pollResp.statusCode() / 100 != 2) {
                        throw new RuntimeException("API error (job-status): " +
                                pollResp.statusCode() + " " + pollResp.body());
                    }

                    JsonNode pollJson = MAPPER.readTree(pollResp.body());
                    String status = pollJson.get("status").asText();

                    if ("done".equalsIgnoreCase(status)) {
                        String output = pollJson.get("output").asText();
                        PromptResponseOutput out = MAPPER.readValue(output, PromptResponseOutput.class);

                        long end = System.nanoTime();
                        Telemetry.genCompleted(testClassName, String.valueOf(attempt), (end - start) / 1_000_000);
                        ConsolePrinter.success(myConsole,
                                "Generated test class");
                        return out;
                    } else if ("error".equalsIgnoreCase(status)) {
                        throw new RuntimeException("Job failed: " + pollJson.get("output").asText());
                    }

                    // Wait before next poll
                    pollTime += sleepTime;
                    Thread.sleep(sleepTime);
                }

            } catch (Throwable t) {
                indicator.checkCanceled();
                retries++;
                if (retries > MAX_RETRIES) {
                    Telemetry.genFailed(testClassName, String.valueOf(attempt), t.getMessage());
                    ConsolePrinter.error(myConsole,
                            "Request failed after " + retries + " retries: " + t.getMessage());
                    throw new Exception("Max retries reached: " + t.getMessage(), t);
                }

                long sleepMillis = backoffMillis + (long) (Math.random() * 250); // jitter
                ConsolePrinter.warn(myConsole,
                        "Retrying request (" + retries + "/" + MAX_RETRIES + ") after " + sleepMillis + "ms: " + t.getMessage());

                Thread.sleep(sleepMillis);
                backoffMillis = Math.min(backoffMillis * 2, 30_000); // cap at 30s
            }
        }
    }

}
