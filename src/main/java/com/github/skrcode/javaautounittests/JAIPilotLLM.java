package com.github.skrcode.javaautounittests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.JavaParser;
import com.github.skrcode.javaautounittests.DTOs.Prompt;
import com.github.skrcode.javaautounittests.DTOs.PromptResponseOutput;
import com.github.skrcode.javaautounittests.settings.AISettings;
import com.github.skrcode.javaautounittests.settings.Telemetry;
import com.google.genai.Client;
import com.google.genai.errors.ClientException;
import com.google.genai.types.*;
import com.intellij.openapi.progress.ProgressIndicator;

import javax.swing.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Simple blocking (non-streaming) façade with elapsed-time indicator. */
public final class JAIPilotLLM {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    final static int MAX_RETRIES = 20;

    public static Content getSystemInstructionContent(Prompt promptPlaceholder) {
        String systemInstructionPrompt = promptPlaceholder.getSystemInstructionsPlaceholder();
        Content systemInstructionContent = Content.builder()
                .role("user")
                .parts(Part.builder().text(systemInstructionPrompt).build())
                .build();
        return systemInstructionContent;
    }

    public static Content getSystemInstructionContextContent(Prompt promptPlaceholder) {
        String systemInstructionPrompt = promptPlaceholder.getSystemInstructionsContextPlaceholder();
        Content systemInstructionContent = Content.builder()
                .role("user")
                .parts(Part.builder().text(systemInstructionPrompt).build())
                .build();
        return systemInstructionContent;
    }

    public static Content getInputClassContent(Prompt promptPlaceholder, String inputClass) {
        String inputPrompt = promptPlaceholder.getInputContextPlaceholder()
                .replace("{{inputclass}}", inputClass == null ? "" : inputClass);
        Content inputContent = Content.builder().role("user")
                .parts(Part.builder().text(inputPrompt).build()).build();
        return inputContent;
    }

    public static Content getErrorOutputContent(Prompt promptPlaceholder, String errorOutput) {
        String errorOutputPrompt = promptPlaceholder.getErrorOutputPlaceholder()
                .replace("{{erroroutput}}", errorOutput == null ? "" : errorOutput);
        Content errorOutputContent = Content.builder().role("user")
                .parts(Part.builder().text(errorOutputPrompt).build()).build();
        return errorOutputContent;
    }

    public static Content getClassContextPathContent(List<String> contextClasses) {
        Content contextClass = Content.builder().role("model")
                .parts(Part.builder().text(joinLines(contextClasses)).build()).build();
        return contextClass;

    }

    public static Content getClassContextPathSourceContent(Prompt promptPlaceholder, List<String> contextClassesSources) {
        String contextClasses = promptPlaceholder.getContextClassesSourcePlaceholder()
                .replace("{{contextclasses}}", joinLines(contextClassesSources));
        Content contextClassSource = Content.builder().role("user")
                .parts(Part.builder().text(contextClasses).build()).build();
        return contextClassSource;
    }

    public static Content getGenerateMoreTestsContent(Prompt promptPlaceholder, String testClassName) {
        String generateMorePrompt = promptPlaceholder.getGenerateMorePlaceholder()
                .replace("{{testclassname}}", testClassName == null ? "" : testClassName);
        Content generateMoreContent = Content.builder().role("user")
                .parts(Part.builder().text(generateMorePrompt).build())
                .build();
        return generateMoreContent;
    }

    public static Content getExistingTestClassContent(Prompt promptPlaceholder, String existingTestClass, String role) {
        String existingTestClassPrompt = promptPlaceholder.getExistingTestClassPlaceholder()
                .replace("{{testclass}}", existingTestClass == null ? "" : existingTestClass);
        Content existingTestClassContent = Content.builder().role(role)
                .parts(Part.builder().text(existingTestClassPrompt).build()).build();
        return existingTestClassContent;
    }

//    system instruction - junit
//    user - input class
//    model - test--------------------
//    user - error output
//      user - give me class context
//      model - class context path
//      user = class context source
//    user - generate tests---------------------------
//    model - test
//    user - error output
//      user - give me class context
//      model - class context path
//      user = class context source
//    user - generate tests
//    model - test

    public static PromptResponseOutput getAllSingleTestContext(
            List<Content> contents,
            Prompt prompt,
            String testClassName,              // kept for signature compatibility (unused)
            int attempt,                       // kept for signature compatibility (unused)
            ProgressIndicator indicator
    ) {
        ElapsedTicker ticker = new ElapsedTicker(indicator, "Getting Context.. Attempt #" + attempt);
        ticker.start();
        long start = System.nanoTime();
        Telemetry.genStarted(testClassName, String.valueOf(attempt));

        final int MAX_RETRIES = 3; // configurable
        int retries = 0;

        while (retries < MAX_RETRIES) {
            retries++;
            StringBuilder attemptBuf = new StringBuilder();

            try {
                // ==== Gemini client ====
                String apiKey = AISettings.getInstance().getOpenAiKey();
                String model = AISettings.getInstance().getModel();
                Client client = Client.builder().apiKey(apiKey).build();

                GenerateContentConfig cfg = GenerateContentConfig.builder()
                        .responseMimeType("text/plain")
                        .candidateCount(1)
                        .systemInstruction(getSystemInstructionContextContent(prompt))
                        .thinkingConfig(ThinkingConfig.builder().thinkingBudget(0).build())
                        .build();

                // ==== Streaming with line-by-line updates ====
                int lineCounter = 0;
                try (var stream = client.models.generateContentStream(model, contents, cfg)) {
                    for (var chunk : stream) {
                        if (indicator != null && indicator.isCanceled()) {
                            throw new com.intellij.openapi.progress.ProcessCanceledException();
                        }

                        String delta = "";
                        if (!chunk.text().isEmpty()) {
                            delta = chunk.text();
                        } else {
                            delta = chunk.candidates()
                                    .flatMap(cands -> cands.stream().findFirst())
                                    .flatMap(c -> c.content())
                                    .flatMap(ct -> ct.parts())
                                    .map(parts -> {
                                        StringBuilder sb = new StringBuilder();
                                        for (Part p : parts) {
                                            p.text().ifPresent(sb::append);
                                        }
                                        return sb.toString();
                                    })
                                    .orElse("");
                        }

                        if (!delta.isEmpty()) {
                            String clean = delta
                                    .replaceAll("(?s)```java\\b\\s*", "")
                                    .replaceAll("(?s)```", "")
                                    .replaceFirst("(?m)^java\\s*$", "");

                            attemptBuf.append(clean);

                            for (int i = 0; i < clean.length(); i++) {
                                if (clean.charAt(i) == '\n') lineCounter++;
                            }

                            if (indicator != null) {
                                int lastNl = attemptBuf.lastIndexOf("\n");
                                String tail = (lastNl >= 0 ? attemptBuf.substring(lastNl + 1) : attemptBuf.toString()).trim();
                                if (!tail.isEmpty()) {
                                    long lineNo = lineCounter + 1L;
                                    indicator.setText2(lineNo + ": " + tail);
                                }
                            }
                        }
                    }
                }

                // ==== Parse + validate response ====
                String rawText = attemptBuf.toString().trim();
                List<String> ctx = new ArrayList<>();

                try {
                    if (rawText.startsWith("[") && rawText.endsWith("]")) {
                        ctx.addAll(MAPPER.readValue(rawText,
                                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}));
                    }
                } catch (Exception ignored) {
                    // Parsing failed → retry loop will handle
                }

                // ==== Validation: must be a list of class paths ====
                if (!ctx.isEmpty() && ctx.stream().allMatch(s -> s.matches("[a-zA-Z_][\\w$]*(\\.[a-zA-Z_][\\w$]*)*"))) {
                    // Trim to max 10, preserve order
                    if (ctx.size() > 10) {
                        ctx = ctx.subList(0, 10);
                    }

                    PromptResponseOutput out = new PromptResponseOutput();
                    out.setContextClasses(ctx);

                    ticker.stopWithMessage("Done");
                    long end = System.nanoTime();
                    Telemetry.genCompleted(testClassName, String.valueOf(attempt), (end - start) / 1_000_000);
                    return out;
                }

                // If invalid, retry
                if (retries < MAX_RETRIES && indicator != null) {
                    indicator.setText2("Retry " + retries + "/" + MAX_RETRIES +
                            " — invalid response format for context classes");
                }
            } catch (com.intellij.openapi.progress.ProcessCanceledException pce) {
                Telemetry.genFailed(testClassName, String.valueOf(attempt), "Canceled");
                ticker.stopWithMessage("Canceled");
                PromptResponseOutput out = new PromptResponseOutput();
                out.setContextClasses(new ArrayList<>());
                return out;
            } catch (Throwable t) {
                if (retries >= MAX_RETRIES) {
                    Telemetry.genFailed(testClassName, String.valueOf(attempt), t.getMessage());
                    ticker.stopWithMessage("Failed");
                    PromptResponseOutput out = new PromptResponseOutput();
                    out.setContextClasses(new ArrayList<>());
                    return out;
                }
            }
        }

        // ==== Fallback after retries ====
        PromptResponseOutput out = new PromptResponseOutput();
        out.setContextClasses(new ArrayList<>());
        ticker.stopWithMessage("Failed after retries");
        return out;
    }




    /**
     * FREE: Blocking call to Gemini with JSON schema; expects:
     * {
     *   "outputTestClass": "...",
     *   "outputRequiredClassContextPaths": ["..."]
     * }
     * Shows elapsed time in the ProgressIndicator while the request runs.
     */
    public static PromptResponseOutput getAllSingleTest(
            List<Content> contents,
            Prompt prompt,
            String testClassName,
            int attempt,
            ProgressIndicator indicator
    ) throws Exception {

        ElapsedTicker ticker = new ElapsedTicker(indicator, "Attempt #" + attempt + ": Running model…");
        ticker.start();
        long start = System.nanoTime();
        Telemetry.genStarted(testClassName, String.valueOf(attempt));

        try {
            // ==== Gemini client ====
            String apiKey = AISettings.getInstance().getOpenAiKey();
            Client client = Client.builder().apiKey(apiKey).build();

            GenerateContentConfig cfg = GenerateContentConfig.builder()
                    .responseMimeType("text/plain") // Only raw test class
                    .candidateCount(1)
                    .thinkingConfig(ThinkingConfig.builder().thinkingBudget(0).build())
                    .systemInstruction(getSystemInstructionContent(prompt))
                    .build();

            // Stream with retries
            String full = streamWithRetries(
                    client,
                    contents,
                    cfg,
                    indicator,
                    delta -> {
                        // 1) Live progress line-by-line (already handled inside helper via indicator.setText2)
                        // 2) Optional: append to an editor buffer for a “typing” effect
                        //    run later on EDT to avoid thrashing:
                        // ApplicationManager.getApplication().invokeLater(() ->
                        //     WriteCommandAction.runWriteCommandAction(project, () -> { appendToDoc(editor, delta); })
                        // );
                    }
            );

            // ==== Build output ====
            // Build output with ONLY the test class
            PromptResponseOutput out = new PromptResponseOutput();
            out.setTestClassCode(full);

            ticker.stopWithMessage("Done");
            long end = System.nanoTime();
            Telemetry.genCompleted(testClassName, String.valueOf(attempt), (end - start) / 1_000_000);
            return out;

        } catch (Throwable t) {
            Telemetry.genFailed(testClassName, String.valueOf(attempt), t.getMessage());
            ticker.stopWithMessage("Failed");
            throw new Exception(t.getMessage());
        }
    }



    private static JsonNode callProWithRetries(
            HttpClient http , HttpRequest req) throws Exception {
        int retries = 0;
        while (retries < MAX_RETRIES) {
            try {
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() / 100 == 4) {
                    String errorBody = new String(resp.body().getBytes(), StandardCharsets.UTF_8);
                    throw new IllegalArgumentException(errorBody);
                }
                if (resp.statusCode() / 100 != 2) {
                    String errorBody = new String(resp.body().getBytes(), StandardCharsets.UTF_8);
                    throw new IOException(errorBody);
                }

                JsonNode node = MAPPER.readTree(resp.body());
                String outputTestClass = node.get("outputTestClass").asText("");
                JavaParser parser = new JavaParser();
                if (!parser.parse(outputTestClass).isSuccessful()) {
                    throw new Exception("Parser failed");
                }
                return node;
            }
            catch (IllegalArgumentException e) {
                throw e;
            }
            catch (Exception e) {
                retries++;
                if (retries >= MAX_RETRIES) {
                    throw e; // rethrow after last attempt
                }
                System.err.println("Retry " + retries + " failed: " + e.getMessage());
                Thread.sleep(500L * retries); // backoff
            }
        }
        throw new IllegalStateException("Unexpected: reached end of retry loop");
    }

    private static String streamWithRetries(
            Client client,
            List<Content> contents,
            GenerateContentConfig cfg,
            ProgressIndicator indicator,
            java.util.function.Consumer<String> onDelta // may be null
    ) throws Exception {

        int retries = 0;
        final String model = AISettings.getInstance().getModel();

        while (retries < MAX_RETRIES) {
            StringBuilder attemptBuf = new StringBuilder();
            try (var stream = client.models.generateContentStream(model, contents, cfg)) {
                int lineCounter = 0;
                for (var chunk : stream) {
                    if (indicator != null && indicator.isCanceled()) {
                        throw new com.intellij.openapi.progress.ProcessCanceledException();
                    }

                    // Prefer SDK's direct text accessor
                    String delta = "";
                    if (!chunk.text().isEmpty()) {
                        delta = chunk.text();
                    } else {
                        // Defensive fallback: stitch from candidates/parts
                        delta = chunk.candidates()
                                .flatMap(cands -> cands.stream().findFirst())
                                .flatMap(c -> c.content())
                                .flatMap(ct -> ct.parts())
                                .map(parts -> {
                                    StringBuilder sb = new StringBuilder();
                                    for (Part p : parts) {
                                        p.text().ifPresent(sb::append);
                                    }
                                    return sb.toString();
                                })
                                .orElse("");
                    }

                    if (!delta.isEmpty()) {
                        String clean = delta
                                .replaceAll("(?s)```java\\b\\s*", "")  // remove ```java
                                .replaceAll("(?s)```", "")             // remove closing ```
                                .replaceFirst("(?m)^java\\s*$", "");   // remove lone "java" line

                        attemptBuf.append(clean);

                        // ===== Accurate line numbering =====
                        for (int i = 0; i < clean.length(); i++) {
                            if (clean.charAt(i) == '\n') lineCounter++;
                        }

                        if (onDelta != null) onDelta.accept(clean);

                        if (indicator != null) {
                            int lastNl = attemptBuf.lastIndexOf("\n");
                            String tail = (lastNl >= 0 ? attemptBuf.substring(lastNl + 1) : attemptBuf.toString()).trim();
                            if (!tail.isEmpty()) {
                                long lineNo = lineCounter + 1;
                                indicator.setText2(lineNo + ": " + tail);
                            }
                        }
                    }
                }

                String fullOutput = attemptBuf.toString();

                // ===== JavaParser validation =====
                JavaParser parser = new JavaParser();
                if (!parser.parse(fullOutput).isSuccessful()) {
                    throw new Exception("JavaParser validation failed: invalid Java syntax");
                }

                // Success: return validated output
                return fullOutput;
            }
            catch (ClientException ce) {
                throw ce; // invalid key / bad request, don't retry
            }
            catch (com.intellij.openapi.progress.ProcessCanceledException pce) {
                throw pce; // respect user cancel
            }
            catch (Exception e) {
                retries++;
                if (retries >= MAX_RETRIES) {
                    throw e;
                }
                long backoffMs = (long) (500L * Math.pow(2, retries - 1));
                if (indicator != null) {
                    indicator.setText2("Retry " + retries + "/" + MAX_RETRIES +
                            " in " + backoffMs + " ms — " + e.getMessage());
                }
                Thread.sleep(backoffMs);
            }
        }

        throw new IllegalStateException("Unexpected: reached end of retry loop");
    }




    private static <T> T callWithRetries(
            Client client,
            java.util.List<Content> contents,
            GenerateContentConfig cfg, Class<T> type) throws Exception {

        int retries = 0;
        while (retries < MAX_RETRIES) {
            try {
                // Call Gemini
                GenerateContentResponse resp = client.models.generateContent(AISettings.getInstance().getModel(), contents, cfg);

                // Collect JSON text
                StringBuilder json = new StringBuilder();
                resp.candidates().ifPresent(cands -> {
                    if (!cands.isEmpty()) {
                        cands.get(0).content().ifPresent(content ->
                                content.parts().ifPresent(parts -> {
                                    for (Part p : parts) p.text().ifPresent(json::append);
                                })
                        );
                    }
                });

                String sanitized = json.toString()
                        .replaceAll("```json\\s*", "")
                        .replaceAll("```\\s*$", "");

//                JavaParser parser = new JavaParser();
                T responseOutput = MAPPER.readValue(sanitized, type);
//                if(!parser.parse(responseOutput.outputTestClass).isSuccessful()) {
//                    throw new Exception("Parser failed");
//                }
                // Parse JSON to DTO
                return responseOutput;
            }
            catch (ClientException ce) {
                // Invalid key / bad request — no point retrying
                throw ce;
            }
            catch (Exception e) {
                retries++;
                if (retries >= MAX_RETRIES) {
                    throw e; // rethrow after last attempt
                }
                System.err.println("Retry " + retries + " failed: " + e.getMessage());
                Thread.sleep(500L * retries); // backoff
            }
        }
        throw new IllegalStateException("Unexpected: reached end of retry loop");
    }

    /**
     * PRO: Blocking HTTP call to your Supabase Edge Function; expects JSON:
     * { "outputTestClass": "...", "outputRequiredClassContextPaths": ["..."] }
     * Shows elapsed time in the ProgressIndicator while the request runs.
     */
    public static PromptResponseOutput getAllSingleTestPro(
            String testFileName,
            String cutClass,
            String existingIndividualTestClass,
            String errorOutput,
            List<String> contextClassesSource,
            int attempt,
            ProgressIndicator indicator
    ) throws Exception {
        ElapsedTicker ticker = new ElapsedTicker(indicator, "Attempt #"+attempt+": Running model…");
        ticker.start();
        Telemetry.genStarted(testFileName, String.valueOf(attempt));
        long start = System.nanoTime();
        try {
            final String url = "https://otxfylhjrlaesjagfhfi.supabase.co/functions/v1/invoke-junit-llm";

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("testclass", existingIndividualTestClass == null ? "" : existingIndividualTestClass);
            body.put("inputclass", cutClass == null ? "" : cutClass);
            body.put("testclassname", testFileName == null ? "" : testFileName);
            body.put("erroroutput", errorOutput == null ? "" : errorOutput);
            body.put("contextclasses", contextClassesSource == null ? List.of() : contextClassesSource);
            body.put("attemptNumber", attempt);

            String requestJson = MAPPER.writeValueAsString(body);

            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(200000))
                    .build();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(1500))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + (AISettings.getInstance().getProKey() == null ? "" : AISettings.getInstance().getProKey()))
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                    .build();

            JsonNode node = callProWithRetries(http, req);
            String testClass = node.has("outputTestClass") ? node.get("outputTestClass").asText("") : "";
            List<String> ctx = new ArrayList<>();
            if (node.has("outputRequiredClassContextPaths") && node.get("outputRequiredClassContextPaths").isArray()) {
                for (JsonNode c : node.get("outputRequiredClassContextPaths")) ctx.add(c.asText());
            }

            PromptResponseOutput out = new PromptResponseOutput();
            out.setTestClassCode(testClass);
            out.setContextClasses(ctx);

            ticker.stopWithMessage("Done");
            long end = System.nanoTime();
            Telemetry.genCompleted(testFileName,String.valueOf(attempt),(end - start) / 1_000_000);
            return out;

        } catch (Throwable t) {
            Telemetry.genFailed(testFileName,String.valueOf(attempt),t.getMessage());
            ticker.stopWithMessage("Failed");
            throw new Exception(t.getMessage());
        }
    }


    private static String joinLines(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (s == null) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(s);
        }
        return sb.toString();
    }

    // ---- elapsed-time ticker (minimal, per-call) ----
    private static final class ElapsedTicker {
        private final ProgressIndicator indicator;
        private final String prefix;
        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        private final long startNanos = System.nanoTime();
        private ScheduledFuture<?> task;

        ElapsedTicker(ProgressIndicator indicator, String prefix) {
            this.indicator = indicator;
            this.prefix = prefix == null ? "" : prefix;
        }

        void start() {
            if (indicator == null) return;
            task = scheduler.scheduleAtFixedRate(() -> {
                String hms = formatElapsed(System.nanoTime() - startNanos);
                SwingUtilities.invokeLater(() -> indicator.setText(prefix + " (" + hms + ")"));
            }, 0, 1, TimeUnit.SECONDS);
        }

        void stopWithMessage(String finalPrefix) {
            if (task != null) task.cancel(true);
            scheduler.shutdownNow();
            if (indicator != null) {
                String hms = formatElapsed(System.nanoTime() - startNanos);
                SwingUtilities.invokeLater(() -> indicator.setText(finalPrefix + " (" + hms + ")"));
            }
        }

        private static String formatElapsed(long nanos) {
            long totalSec = TimeUnit.NANOSECONDS.toSeconds(nanos);
            long h = totalSec / 3600;
            long m = (totalSec % 3600) / 60;
            long s = totalSec % 60;
            return (h > 0)
                    ? String.format("%d:%02d:%02d", h, m, s)
                    : String.format("%02d:%02d", m, s);
        }
    }
}
