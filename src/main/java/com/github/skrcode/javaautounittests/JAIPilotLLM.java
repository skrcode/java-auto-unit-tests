package com.github.skrcode.javaautounittests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.skrcode.javaautounittests.DTOs.Content;
import com.github.skrcode.javaautounittests.DTOs.PromptResponseOutput;
import com.github.skrcode.javaautounittests.settings.AISettings;
import com.github.skrcode.javaautounittests.settings.ConsolePrinter;
import com.github.skrcode.javaautounittests.settings.telemetry.Telemetry;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;

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
    ) throws Exception {
        long start = System.nanoTime();
        Telemetry.genStarted(testClassName, String.valueOf(attempt));

        int retries = 0;
        long backoffMillis = 1000; // start with 1s

        while (true) {
            try {
                // Build request payload
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("attemptNumber", attempt);
                body.put("contents", contents);
                String requestJson = MAPPER.writeValueAsString(body);

                // Select key based on mode
                String headerName;
                String headerValue;
                if ("BYOK".equalsIgnoreCase(AISettings.getInstance().getMode())) {
                    String key = AISettings.getInstance().getOpenAiKey();
                    headerName = "x-gemini-key";
                    headerValue = key;
                } else {
                    String key = AISettings.getInstance().getProKey();
                    headerName = "Authorization";
                    headerValue = "Bearer " + key;
                }

                HttpClient http = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(30))
                        .build();

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://otxfylhjrlaesjagfhfi.supabase.co/functions/v1/invoke-junit-llm-stream"))
                        .timeout(Duration.ofMinutes(10))
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .header(headerName, headerValue == null ? "" : headerValue)
                        .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                        .build();

                // ✅ Attempt API call
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                if (resp.statusCode() / 100 != 2) {
                    throw new RuntimeException("API error: " + resp.statusCode() + " " + resp.body());
                }

                PromptResponseOutput out = MAPPER.readValue(resp.body(), PromptResponseOutput.class);

                long end = System.nanoTime();

                Telemetry.genCompleted(testClassName, String.valueOf(attempt), (end - start) / 1_000_000);
                ConsolePrinter.success(myConsole, "Generated test class via " + AISettings.getInstance().getMode());
                return out;

            } catch (Throwable t) {
                retries++;
                if (retries > MAX_RETRIES) {
                    Telemetry.genFailed(testClassName, String.valueOf(attempt), t.getMessage());
                    ConsolePrinter.error(myConsole,
                            "Request failed after " + retries + " retries: " + t.getMessage());
                    throw new Exception("Max retries reached: " + t.getMessage(), t);
                }

                long sleepMillis = backoffMillis + (long)(Math.random() * 250); // jitter
                ConsolePrinter.warn(myConsole,
                        "Retrying request (" + retries + "/" + MAX_RETRIES + ") after " + sleepMillis + "ms: " + t.getMessage());

                Thread.sleep(sleepMillis);
                backoffMillis = Math.min(backoffMillis * 2, 30_000); // cap at 30s
            }
        }
    }
}
