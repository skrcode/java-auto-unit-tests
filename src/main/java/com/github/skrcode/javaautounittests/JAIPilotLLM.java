package com.github.skrcode.javaautounittests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.skrcode.javaautounittests.DTOs.InitialResponseOutput;
import com.github.skrcode.javaautounittests.DTOs.PromptInitialResponseOutput;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Convenience façade so we can switch out or mock in tests. */
public final class JAIPilotLLM {
//    public static String invokeAI(String prompt) {
//        try {
//            OpenAIClient client = OpenAIOkHttpClient.builder().apiKey(AISettings.getInstance().getOpenAiKey()).build();
//
//            StructuredResponseCreateParams<ResponseOutput> params = ResponseCreateParams.builder()
//                    .input(prompt)
//                    .text(ResponseOutput.class)
//                    .model(AISettings.getInstance().getModel())
//                    .build();
//
//            return client.responses().create(params).output().stream()
//                    .flatMap(item -> item.message().stream())
//                    .flatMap(message -> message.content().stream())
//                    .flatMap(content -> content.outputText().stream())
//                    .map(responseTestClass -> responseTestClass.outputTestClass).collect(Collectors.joining());
//        } catch (Throwable t) {
//            t.printStackTrace();
//            Messages.showErrorDialog("AI Error: " + t.getClass().getName() + "\n" + t.getMessage(), "LLM Error");
//            return "ERROR: " + t.getMessage();
//        }
//    }

    public static Schema getSchema() {
        Schema schema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(ImmutableMap.of(
                        "outputTestClassUnifiedDiffFormat", Schema.builder()
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
        return schema;
    }

    public static Schema getInitialSchema() {
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
        return schema;
    }

    public static String getAllSingleTest(String promptPlaceholder, String testClassName, String inputClass, String existingTestClass, String errorOutput, List<String> contextClassesSources, ProgressIndicator indicator, String model ,Schema schema) {

        Client client = Client.builder().apiKey(AISettings.getInstance().getOpenAiKey()).build();
        GenerateContentConfig generateContentConfig = GenerateContentConfig.builder().responseMimeType("application/json").candidateCount(1).responseSchema(schema).build();

        Map<String, String> placeholders = Map.of(
                "{{inputclass}}", inputClass,
                "{{testclass}}", existingTestClass,
                "{{erroroutput}}", errorOutput,
                "{{testclassname}}", testClassName,
                "{{contextclasses}}", contextClassesSources.toString()
        );

        String prompt = promptPlaceholder;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            prompt = prompt.replace(entry.getKey(), entry.getValue());
        }
        String finalPrompt = prompt;

//        writeToTempDirectory("/prompt-logs","Prompt"+testClassName+"-"+attempt+".txt",finalPrompt);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        AtomicBoolean hasStartedStreaming = new AtomicBoolean(false);
        
        String[] thinkingMessages = new String[]{
                "🤖 Thinking...",
                "📚 Analyzing your class structure...",
                "🧠 Planning test strategy...",
                "🧪 Preparing mocks and verifications...",
                "🔍 Looking for method dependencies...",
                "🧾 Reading annotations and contracts...",
                "📦 Mapping repository interactions...",
                "🧱 Breaking down private method logic...",
                "📐 Measuring test coverage gaps...",
                "🗂️ Scanning for edge cases...",
                "🔧 Matching setup for mock behavior...",
                "📈 Identifying likely branches and conditions...",
                "🚧 Guarding against nulls and edge values...",
                "🔄 Building test data scenarios...",
                "🧬 Understanding domain models...",
                "🔦 Walking through method call chains...",
                "⚖️ Weighing test case priorities...",
                "📌 Pinning test names to methods...",
                "⏳ Calibrating constructor arguments...",
                "✅ Getting ready to generate test code..."
        };
        final int[] idx = {0};
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            if (!hasStartedStreaming.get()) {
                String msg = thinkingMessages[idx[0] % thinkingMessages.length];
                idx[0]++;
                SwingUtilities.invokeLater(() -> indicator.setText(msg));
            }
        }, 0, 5, TimeUnit.SECONDS); // update every 2 seconds

        try {
            // 2. Now start LLM call (this might take a few seconds before it returns)
            ResponseStream<GenerateContentResponse> response = invokeGeminiApiWithModel(finalPrompt, schema, client, generateContentConfig, model);

            StringBuilder fullText = new StringBuilder();
            List<String> generatedTests = new ArrayList<>();
            Pattern methodPattern = Pattern.compile("void\\s+(test\\w+)\\s*\\(");

            // 3. As soon as stream starts, flip flag to stop thinking animation
            for (GenerateContentResponse chunk : response) {
                if (chunk == null) continue;

                hasStartedStreaming.set(true); // 🚨 stop thinking animation

                chunk.candidates().ifPresent(candidates -> {
                    if (candidates.isEmpty()) return;

                    candidates.get(0).content().ifPresent(content -> {
                        content.parts().ifPresent(parts -> {
                            for (Part part : parts) {
                                part.text().ifPresent(text -> {
                                    fullText.append(text);

                                    Matcher matcher = methodPattern.matcher(text);
                                    while (matcher.find()) {
                                        String methodName = matcher.group(1);
                                        String fullTestName = "✅ " + testClassName + "." + methodName;
                                        generatedTests.add(fullTestName);

                                        SwingUtilities.invokeLater(() -> {
                                            indicator.setText("✅ Test #[ " + generatedTests.size() + " ] " + fullTestName);
                                        });
                                    }
                                });
                            }
                        });
                    });
                });
            }

            task.cancel(true); // 🧹 stop thinking animation
            scheduler.shutdownNow();
            return fullText.toString();
        }catch (Throwable t) {
            ApplicationManager.getApplication().invokeLater(() ->
                    Messages.showErrorDialog("Exception: " + t.getClass().getName() + "\n" + t.getMessage(), "Error")
            );
            return "";
        }

    }

    public static PromptResponseOutput parsePromptOutputText(String fullText) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ResponseOutput parsed = mapper.readValue(fullText, ResponseOutput.class);
            PromptResponseOutput output = new PromptResponseOutput();
            output.setTestClassCodeDiff(parsed.outputTestClassUnifiedDiffFormat);
            output.setContextClasses(parsed.outputRequiredClassContextPaths);
            return output;
        }
        catch (Throwable t) {
            ApplicationManager.getApplication().invokeLater(() ->
                    Messages.showErrorDialog("Exception: " + t.getClass().getName() + "\n" + t.getMessage(), "Error")
            );

            t.printStackTrace();
            PromptResponseOutput output = new PromptResponseOutput();
            output.setTestClassCodeDiff("ERROR: " + t.getMessage());
            output.setContextClasses(new ArrayList<>());
            return output;
        }
    }

    public static PromptInitialResponseOutput parseInitialPromptOutputText(String fullText) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InitialResponseOutput parsed = mapper.readValue(fullText, InitialResponseOutput.class);
            PromptInitialResponseOutput output = new PromptInitialResponseOutput();
            output.setTestClassCode(parsed.outputTestClass);
            output.setContextClasses(parsed.outputRequiredClassContextPaths);
            return output;
        }
        catch (Throwable t) {
            ApplicationManager.getApplication().invokeLater(() ->
                    Messages.showErrorDialog("Exception: " + t.getClass().getName() + "\n" + t.getMessage(), "Error")
            );
            t.printStackTrace();
            PromptInitialResponseOutput output = new PromptInitialResponseOutput();
            output.setTestClassCode("ERROR: " + t.getMessage());
            output.setContextClasses(new ArrayList<>());
            return output;
        }
    }

    private static GenerateContentResponse invokeGemini(String prompt, Schema schema) {
        Client client = Client.builder().apiKey(AISettings.getInstance().getOpenAiKey()).build();
        GenerateContentConfig generateContentConfig = GenerateContentConfig.builder().responseMimeType("application/json").candidateCount(1).responseSchema(schema).build();
        GenerateContentResponse response = client.models.generateContent(AISettings.getInstance().getModel(), prompt, generateContentConfig);
        return response;
    }

    private static ResponseStream<GenerateContentResponse> invokeGeminiApi(String prompt, Schema schema, Client client, GenerateContentConfig generateContentConfig) {
        ResponseStream<GenerateContentResponse> response = client.models.generateContentStream(AISettings.getInstance().getModel(), prompt, generateContentConfig);
        return response;
    }

    private static ResponseStream<GenerateContentResponse> invokeGeminiApiWithModel(String prompt, Schema schema, Client client, GenerateContentConfig generateContentConfig, String model) {
        ResponseStream<GenerateContentResponse> response = client.models.generateContentStream(model, prompt, generateContentConfig);
        return response;
    }
}