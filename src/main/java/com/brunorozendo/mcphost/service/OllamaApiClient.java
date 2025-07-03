package com.brunorozendo.mcphost.service;

import com.brunorozendo.mcphost.model.OllamaApi;
import com.brunorozendo.mcphost.service.llm.LlmApiClient;
import com.brunorozendo.mcphost.service.llm.OllamaApiClientImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A client for interacting with the Ollama REST API.
 * This is a wrapper class maintained for backward compatibility.
 * @deprecated Use LlmApiClient and LlmApiClientFactory instead
 */
@Deprecated
public class OllamaApiClient implements LlmApiClient {
    private static final Logger logger = LoggerFactory.getLogger(OllamaApiClient.class);
    private final LlmApiClient delegate;

    public OllamaApiClient(String baseUrl) {
        logger.warn("OllamaApiClient is deprecated. Use LlmApiClientFactory.createClient() instead.");
        this.delegate = new OllamaApiClientImpl(baseUrl);
    }

    /**
     * Sends a chat request to the Ollama API and returns the response.
     *
     * @param request The chat request object.
     * @return The chat response from the API.
     * @throws Exception if the request fails or the response cannot be parsed.
     */
    @Override
    public OllamaApi.ChatResponse chat(OllamaApi.ChatRequest request) throws Exception {
        return delegate.chat(request);
    }

    @Override
    public String getProviderName() {
        return delegate.getProviderName();
    }
}
