package com.brunorozendo.mcphost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class OllamaApiClient {
    private static final Logger logger = LoggerFactory.getLogger(OllamaApiClient.class);
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    public OllamaApi.ChatResponse chat(OllamaApi.ChatRequest request) throws Exception {
        String requestBody = objectMapper.writeValueAsString(request);
        logger.debug("Ollama Request to {}: {}", baseUrl + "/api/chat", requestBody);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(300))
                .build();

        HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        logger.debug("Ollama Response Status: {}", httpResponse.statusCode());
        // Log only a snippet of the body for brevity, or if it's an error
        if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
            logger.trace("Ollama Response Body: {}", httpResponse.body()); // Trace for successful large bodies
        } else {
            logger.error("Ollama Error Response Body (Status {}): {}", httpResponse.statusCode(), httpResponse.body());
        }


        if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
            return objectMapper.readValue(httpResponse.body(), OllamaApi.ChatResponse.class);
        } else {
            String errorMessage = "Ollama API request failed with status " + httpResponse.statusCode() +
                    ": " + httpResponse.body();
            logger.error(errorMessage);
            throw new RuntimeException(errorMessage);
        }
    }
}