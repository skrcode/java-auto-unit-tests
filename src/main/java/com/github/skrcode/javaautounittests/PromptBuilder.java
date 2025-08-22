package com.github.skrcode.javaautounittests;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class PromptBuilder {

    public static String getPromptPlaceholder(String fileName) {
        String promptUrl = "https://raw.githubusercontent.com/skrcode/java-auto-unit-tests/refs/heads/feature/0.0.13/src/main/resources/" + fileName;

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(promptUrl))
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                throw new IOException("Unexpected response code: " + response.statusCode());
            }

            try (InputStream in = response.body()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt(); // restore interrupt status if interrupted
            throw new RuntimeException("Failed to load prompt from: " + promptUrl, e);
        }
    }


    public static Map<String, List<String>> getModels() {
        String promptUrl = "https://raw.githubusercontent.com/skrcode/java-auto-unit-tests/refs/heads/main/src/main/resources/models.json";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(promptUrl))
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                throw new IOException("Unexpected response code: " + response.statusCode());
            }

            try (InputStream in = response.body()) {
                ObjectMapper mapper = new ObjectMapper();
                return mapper.readValue(in, new TypeReference<Map<String, List<String>>>() {});
            }

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt(); // in case of interruption
            throw new RuntimeException("Failed to load models from: " + promptUrl, e);
        }
    }

}
