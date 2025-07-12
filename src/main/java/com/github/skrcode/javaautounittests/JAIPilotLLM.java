package com.github.skrcode.javaautounittests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.skrcode.javaautounittests.DTOs.PromptResponseOutput;
import com.github.skrcode.javaautounittests.DTOs.ResponseOutput;
import com.github.skrcode.javaautounittests.settings.AISettings;
import com.google.common.collect.ImmutableMap;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    public static PromptResponseOutput getAllSingleTest(String promptPlaceholder, String testClassName, String inputClass, String existingTestClass, String errorOutput, List<String> contextClassesSources, int attempt) {
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

        Client client = Client.builder().apiKey(AISettings.getInstance().getOpenAiKey()).build();
        GenerateContentConfig generateContentConfig = GenerateContentConfig.builder().responseMimeType("application/json").candidateCount(1).responseSchema(schema).build();
        ObjectMapper mapper = new ObjectMapper();

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
        try {
            GenerateContentResponse response = invokeGeminiApi(finalPrompt, schema, client, generateContentConfig);
            ResponseOutput parsed = mapper.readValue(response.text(), ResponseOutput.class);
            PromptResponseOutput output = new PromptResponseOutput();
            output.setTestClassCode(parsed.outputTestClass);
            output.setContextClasses(parsed.outputRequiredClassContextPaths);
            return output;
        } catch (Throwable t) {
            ApplicationManager.getApplication().invokeLater(() ->
                    Messages.showErrorDialog("Exception: " + t.getClass().getName() + "\n" + t.getMessage(), "Error")
            );

            t.printStackTrace();
            PromptResponseOutput output = new PromptResponseOutput();
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

    private static GenerateContentResponse invokeGeminiApi(String prompt, Schema schema, Client client, GenerateContentConfig generateContentConfig) {
        GenerateContentResponse response = client.models.generateContent(AISettings.getInstance().getModel(), prompt, generateContentConfig);
        return response;
    }
}