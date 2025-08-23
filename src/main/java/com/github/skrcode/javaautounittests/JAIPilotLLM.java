package com.github.skrcode.javaautounittests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.skrcode.javaautounittests.DTOs.Prompt;
import com.github.skrcode.javaautounittests.DTOs.PromptResponseOutput;
import com.github.skrcode.javaautounittests.DTOs.ResponseOutput;
import com.github.skrcode.javaautounittests.settings.AISettings;
import com.google.genai.Client;
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

    /**
     * FREE: Blocking call to Gemini with JSON schema; expects:
     * {
     *   "outputTestClass": "...",
     *   "outputRequiredClassContextPaths": ["..."]
     * }
     * Shows elapsed time in the ProgressIndicator while the request runs.
     */
    public static PromptResponseOutput getAllSingleTest(
            Prompt promptPlaceholder,
            String testClassName,              // kept for signature compatibility (unused)
            String inputClass,
            String existingTestClass,
            String errorOutput,
            List<String> contextClassesSources,
            int attempt,                       // kept for signature compatibility (unused)
            ProgressIndicator indicator
    ) throws Exception {
        ElapsedTicker ticker = new ElapsedTicker(indicator, "Attempt #"+attempt+": Running model…");
        ticker.start();
        try {
            // System instructions - user
            String systemInstructionPrompt = promptPlaceholder.getSystemInstructionsPlaceholder();
            Content systemInstructionContent = Content.builder().role("user").parts(Part.builder().text(systemInstructionPrompt).build()).build();
            // Input class - user - 1
            // Context Classes - user - 2
            // Test classname - user - 3
            String inputPrompt = promptPlaceholder.getInputPlaceholder().replace("{{inputclass}}",inputClass == null ? "" : inputClass).replace("{{contextclasses}}",joinLines(contextClassesSources)).replace("{{testclassname}}",testClassName == null ? "" : testClassName);
            Content inputContent = Content.builder().role("user").parts(Part.builder().text(inputPrompt).build()).build();
            // Existing Test class - model
            String existingTestClassPrompt = promptPlaceholder.getExistingTestClassPlaceholder().replace("{{testclass}}",existingTestClass == null ? "" : existingTestClass);
            Content existingTestClassContent = Content.builder().role("model").parts(Part.builder().text(existingTestClassPrompt).build()).build();
            // Error output - user - 1
            String errorOutputPrompt = promptPlaceholder.getErrorOutputPlaceholder().replace("{{erroroutput}}", errorOutput == null ? "" : errorOutput);
            Content errorOutputContent = Content.builder().role("user").parts(Part.builder().text(errorOutputPrompt).build()).build();

            // Gemini client (blocking)
            String apiKey = AISettings.getInstance().getOpenAiKey();
            Client client = Client.builder().apiKey(apiKey).build();

            // JSON schema
            Schema schema = Schema.builder()
                    .type(Type.Known.OBJECT)
                    .properties(Map.of(
                            "outputTestClass", Schema.builder().type(Type.Known.STRING).build(),
                            "outputRequiredClassContextPaths", Schema.builder()
                                    .type(Type.Known.ARRAY)
                                    .items(Schema.builder().type(Type.Known.STRING).build())
                                    .build()
                    ))
                    .build();

            GenerateContentConfig cfg = GenerateContentConfig.builder()
                    .responseMimeType("application/json")
                    .candidateCount(1)
                    .maxOutputTokens(1000000)
                    .systemInstruction(systemInstructionContent)
                    .thinkingConfig(ThinkingConfig.builder().includeThoughts(false).thinkingBudget(-1).build())
                    .responseSchema(schema)
                    .build();

            List<Content> contents = Arrays.asList(inputContent,existingTestClassContent,errorOutputContent);

            GenerateContentResponse resp =
                    client.models.generateContent(AISettings.getInstance().getModel(), contents, cfg);

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

            // Parse to DTO
            ResponseOutput parsed = MAPPER.readValue(sanitized, ResponseOutput.class);

            String testClass = parsed.outputTestClass == null ? "" : parsed.outputTestClass;
            List<String> ctx = parsed.outputRequiredClassContextPaths != null
                    ? parsed.outputRequiredClassContextPaths
                    : new ArrayList<>();

            PromptResponseOutput out = new PromptResponseOutput();
            out.setTestClassCode(testClass);
            out.setContextClasses(ctx);

            ticker.stopWithMessage("Done");
            return out;

        } catch (Throwable t) {
            ticker.stopWithMessage("Failed");
            throw new Exception(t.getMessage());
        }
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

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                ticker.stopWithMessage("Failed");
                String errorBody = new String(resp.body().getBytes(), StandardCharsets.UTF_8);
                throw new IOException(errorBody);
            }

            JsonNode node = MAPPER.readTree(resp.body());

            String testClass = node.has("outputTestClass") ? node.get("outputTestClass").asText("") : "";
            List<String> ctx = new ArrayList<>();
            if (node.has("outputRequiredClassContextPaths") && node.get("outputRequiredClassContextPaths").isArray()) {
                for (JsonNode c : node.get("outputRequiredClassContextPaths")) ctx.add(c.asText());
            }

            PromptResponseOutput out = new PromptResponseOutput();
            out.setTestClassCode(testClass);
            out.setContextClasses(ctx);

            ticker.stopWithMessage("Done");
            return out;

        } catch (Throwable t) {
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
