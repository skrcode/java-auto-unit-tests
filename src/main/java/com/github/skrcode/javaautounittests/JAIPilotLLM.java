package com.github.skrcode.javaautounittests;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.skrcode.javaautounittests.DTOs.PromptResponseOutput;
import com.github.skrcode.javaautounittests.DTOs.ResponseOutput;
import com.github.skrcode.javaautounittests.settings.AISettings;
import com.google.common.collect.ImmutableMap;
import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Convenience façade so we can switch out or mock in tests. */
public final class JAIPilotLLM {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // --------------------------------------------------------------------
    // 1) GEMINI STREAMING IMPLEMENTATION (com.google.genai client)
    //    - "thinking" goes to ProgressIndicator (when includeThoughts=true)
    //    - Non-thought parts are accumulated and parsed once at the end
    //    - Expects final JSON with fields: outputTestClass, outputRequiredClassContextPaths
    // --------------------------------------------------------------------
    public static PromptResponseOutput getAllSingleTest(
            String promptPlaceholder,
            String testClassName,
            String inputClass,
            String existingTestClass,
            String errorOutput,
            List<String> contextClassesSources,
            int attempt,
            ProgressIndicator indicator
    ) throws JsonProcessingException {
        // Build prompt by replacing placeholders
        Map<String, String> placeholders = Map.of(
                "{{inputclass}}", safe(inputClass),
                "{{testclass}}", safe(existingTestClass),
                "{{erroroutput}}", safe(errorOutput),
                "{{testclassname}}", safe(testClassName),
                "{{contextclasses}}", joinLines(contextClassesSources)
        );

        String prompt = promptPlaceholder;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            prompt = prompt.replace(entry.getKey(), entry.getValue());
        }
        final String finalPrompt = prompt;

        ThinkingTicker ticker = new ThinkingTicker(indicator);
        ticker.start();

        try (ResponseStream<GenerateContentResponse> response = invokeGeminiApi(finalPrompt, AISettings.getInstance().getModel())) {

            StringBuilder jsonBuffer = new StringBuilder();
            AtomicBoolean started = new AtomicBoolean(false);

            for (GenerateContentResponse chunk : response) {
                if (chunk == null) continue;

                chunk.candidates().ifPresent(candidates -> {
                    if (candidates.isEmpty()) return;

                    candidates.get(0).content().ifPresent(content -> {
                        content.parts().ifPresent(parts -> {
                            for (Part part : parts) {
                                final boolean isThought = part.thought().orElse(false);
                                part.text().ifPresent(text -> {
                                    if (started.compareAndSet(false, true)) {
                                        ticker.markStreamStarted(); // mark only once on first token
                                    }
                                    if (isThought) {
                                        final String oneLine = oneLine(text);
                                        SwingUtilities.invokeLater(() -> {
                                            indicator.setText("Thinking…");
                                            indicator.setText2(oneLine);
                                        });
                                    } else {
                                        // accumulate only non-thought (JSON) text for final parse
                                        jsonBuffer.append(text);
                                    }
                                });
                            }
                        });
                    });
                });
            }

            ticker.stop();

            String sanitized = jsonBuffer.toString()
                    .replaceAll("```json\\s*", "")
                    .replaceAll("```\\s*$", "");
            ResponseOutput parsed = MAPPER.readValue(sanitized, ResponseOutput.class);

            PromptResponseOutput out = new PromptResponseOutput();
            out.setTestClassCode(parsed.outputTestClass);
            out.setContextClasses(parsed.outputRequiredClassContextPaths != null
                    ? parsed.outputRequiredClassContextPaths
                    : new ArrayList<>());
            return out;

        } catch (Throwable t) {
            ticker.stop();
            throw t;
        }
    }

    private static ResponseStream<GenerateContentResponse> invokeGeminiApi(String prompt, String model) {
        Schema schema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(ImmutableMap.of(
                        "outputTestClass", Schema.builder()
                                .type(Type.Known.STRING)
                                .description("Output Test Class")
                                .build(),
                        "outputRequiredClassContextPaths", Schema.builder()
                                .type(Type.Known.ARRAY)
                                .items(Schema.builder().type(Type.Known.STRING).build())
                                .description("Output Required Classes Paths for Additional Context")
                                .build()
                ))
                .build();

        // Use Gemini API key (fallback to env)
        String geminiKey = nullIfBlank(AISettings.getInstance().getOpenAiKey());
        if (geminiKey == null) {
            geminiKey = nullIfBlank(System.getenv("GEMINI_KEY"));
        }
        if (geminiKey == null) {
            throw new IllegalStateException("Gemini API key missing: set in settings or GEMINI_KEY env");
        }

        Client client = Client.builder()
                .apiKey(geminiKey)
                .build();

        GenerateContentConfig cfg = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .candidateCount(1)
                .responseSchema(schema)
                .thinkingConfig(ThinkingConfig.builder().includeThoughts(true).build())
                .build();

        return client.models.generateContentStream(
                model,
                prompt,
                cfg
        );
        // NOTE: close() is handled by try-with-resources in the caller
    }

    // --------------------------------------------------------------------
    // 2) SSE EDGE FUNCTION IMPLEMENTATION (your Supabase Edge API)
    //    Signature required:
    //    getAllSingleTestPro(testFileName, cutClass, existingIndividualTestClass, errorOutput, contextClassesSource, indicator)
    //
    //    - Builds request JSON your function expects (includes attemptNumber for model choice)
    //    - Reads SSE frames with events:
    //        response.thinking.*, response.output_text.*, response.completed, response.final, response.error
    // --------------------------------------------------------------------

    /** Overload with attemptNumber: 0 -> flash, >0 -> pro (handled server-side). */
    public static PromptResponseOutput getAllSingleTestPro(
            String testFileName,
            String cutClass,
            String existingIndividualTestClass,
            String errorOutput,
            List<String> contextClassesSource,
            ProgressIndicator indicator,
            int attemptNumber
    ) throws IOException, InterruptedException {
        ThinkingTicker ticker = new ThinkingTicker(indicator);
        ticker.start();

        // SSE endpoint (Gemini-style)
        final String sseUrl = "https://otxfylhjrlaesjagfhfi.supabase.co/functions/v1/invoke-junit-llm";
        if (sseUrl == null || sseUrl.isBlank()) {
            ticker.stop();
            return errorOut("Stream URL is not configured.");
        }

        // Build request body expected by the Edge Function (now includes attemptNumber)
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("testclass", safe(existingIndividualTestClass));
        body.put("inputclass", safe(cutClass));
        body.put("testclassname", safe(testFileName));
        body.put("erroroutput", safe(errorOutput));
        body.put("contextclasses", contextClassesSource != null ? contextClassesSource : List.of());
        body.put("attemptNumber", attemptNumber);

        final String requestBody;
        try {
            requestBody = MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            ticker.stop();
            return errorOut("Failed to encode request JSON: " + e.getMessage());
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(sseUrl))
                .timeout(Duration.ofMinutes(10))
                .header("Accept", "text/event-stream")
                .header("Content-Type", "application/json")
                .header("Cache-Control", "no-cache");
        rb.header("Authorization", "Bearer " + AISettings.getInstance().getProKey());
        HttpRequest req = rb.POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();

        try {
            HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() / 100 != 2) {
                String errorBody = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new IOException(errorBody);
            }

            // Accumulators
            StringBuilder outputJsonBuffer = new StringBuilder(16_384); // raw JSON text deltas
            String[] finalTestClassHolder = new String[1];              // set if `response.final` arrives
            List<String> ctx = new ArrayList<>();

            // Minimal SSE parser state
            String currentEvent = null;
            StringBuilder currentData = new StringBuilder();
            boolean sawAnyData = false;

            try (BufferedReader br = new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    // Heartbeats are comment lines starting with ":" → ignore
                    if (line.startsWith(":")) {
                        continue;
                    }
                    // Blank line => dispatch current event (if any)
                    if (line.isEmpty()) {
                        if (currentEvent != null) {
                            if (!sawAnyData) {
                                sawAnyData = true;
                                ticker.markStreamStarted();
                            }
                            handleSseEvent(currentEvent, currentData.toString(), indicator,
                                    outputJsonBuffer, finalTestClassHolder, ctx);
                        }
                        // reset event frame
                        currentEvent = null;
                        currentData.setLength(0);
                        continue;
                    }

                    // Parse "event:" and "data:" lines per SSE spec
                    if (line.startsWith("event:")) {
                        currentEvent = line.substring("event:".length()).trim();
                    } else if (line.startsWith("data:")) {
                        if (currentData.length() > 0) currentData.append('\n'); // multi-line data support
                        currentData.append(line.substring("data:".length()).trim());
                    }
                    // Any other fields (id:, retry:) can be ignored for this use-case
                }

                // flush last frame if stream ended without trailing blank line
                if (currentEvent != null && currentData.length() > 0) {
                    handleSseEvent(currentEvent, currentData.toString(), indicator,
                            outputJsonBuffer, finalTestClassHolder, ctx);
                }
            }

            ticker.stop();

            // Prefer `response.final` payload when present
            String testClassOut = finalTestClassHolder[0];
            if (testClassOut == null) {
                // Fallback: parse accumulated output_text JSON
                String sanitized = outputJsonBuffer.toString()
                        .replaceAll("```json\\s*", "")
                        .replaceAll("```\\s*$", "");
                try {
                    JsonNode parsed = MAPPER.readTree(sanitized);
                    testClassOut = parsed.path("testClass").asText("");
                    // If context classes only came in final, leave ctx as-is
                    if (ctx.isEmpty() && parsed.has("contextClasses") && parsed.get("contextClasses").isArray()) {
                        for (JsonNode c : parsed.get("contextClasses")) ctx.add(c.asText());
                    }
                } catch (Exception ignore) {
                    // leave empty; error already surfaced via response.error if any
                }
            }

            PromptResponseOutput out = new PromptResponseOutput();
            out.setTestClassCode(testClassOut != null ? testClassOut : "");
            out.setContextClasses(ctx);
            return out;

        } catch (Throwable t) {
            ticker.stop();
            throw t;
        }
    }

    /** Handle a single SSE event frame from the server. */
    private static void handleSseEvent(
            String eventName,
            String dataJson,
            ProgressIndicator indicator,
            StringBuilder outputJsonBuffer,
            String[] finalTestClassHolder,
            List<String> ctxOut
    ) {
        try {
            JsonNode node = MAPPER.readTree(dataJson);

            switch (eventName) {
                case "response.error": {
                    String msg = node.path("message").asText("Unknown error");
                    throw new RuntimeException("Server error: " + msg);
                }
                case "response.thinking.start": {
                    SwingUtilities.invokeLater(() -> {
                        indicator.setText("Thinking…");
                        indicator.setText2("");
                    });
                    break;
                }
                case "response.thinking.delta": {
                    String text = node.path("text").asText("");
                    if (!text.isEmpty()) {
                        final String oneLine = oneLine(text);
                        SwingUtilities.invokeLater(() -> {
                            indicator.setText("Thinking…");
                            indicator.setText2(oneLine);
                        });
                    }
                    break;
                }
                case "response.thinking.done": {
                    // Optional: clear/lock thinking message
                    break;
                }
                case "response.output_text.delta": {
                    // Append raw JSON text (will be parsed at end or replaced by response.final)
                    String text = node.path("text").asText("");
                    if (!text.isEmpty()) outputJsonBuffer.append(text);
                    break;
                }
                case "response.output_text.done": {
                    // nothing to do; wait for final/completed
                    break;
                }
                case "response.completed": {
                    // contextClasses (if provided here)
                    JsonNode cc = node.path("contextClasses");
                    if (cc.isArray()) {
                        ctxOut.clear();
                        for (JsonNode c : cc) ctxOut.add(c.asText());
                    }
                    break;
                }
                case "response.final": {
                    // final parsed artifact (preferred)
                    String testClass = node.path("testClass").asText(null);
                    if (testClass != null) finalTestClassHolder[0] = testClass;
                    break;
                }
                case "response.start":
                    // optional: can log selected model/attempt
                    break;
                default:
                    // ignore unknown events
            }
        } catch (RuntimeException re) {
            throw re; // bubble up to caller
        } catch (Exception e) {
            // Malformed data for this event — ignore safely
        }
    }

    // ---------- helpers ----------
    private static String oneLine(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String joinLines(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (s == null) continue;
            if (sb.length() > 0) sb.append("\n");
            sb.append(s);
        }
        return sb.toString();
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static PromptResponseOutput errorOut(String msg) {
        ApplicationManager.getApplication().invokeLater(() ->
                Messages.showErrorDialog(msg, "Error")
        );
        PromptResponseOutput out = new PromptResponseOutput();
        out.setTestClassCode("ERROR: " + msg);
        out.setContextClasses(new ArrayList<>());
        return out;
    }

    private static void showErrorAsync(String msg) {
        ApplicationManager.getApplication().invokeLater(() ->
                Messages.showErrorDialog(msg, "Error")
        );
    }

    private static final class ThinkingTicker {
        ThinkingTicker(ProgressIndicator i) {}
        void start() {}
        void markStreamStarted() {}
        void stop() {}
    }

}
