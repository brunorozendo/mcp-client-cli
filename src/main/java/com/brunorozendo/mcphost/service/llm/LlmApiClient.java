package com.brunorozendo.mcphost.service.llm;

import com.brunorozendo.mcphost.model.OllamaApi;

/**
 * Common interface for LLM API clients.
 */
public interface LlmApiClient {
    /**
     * Sends a chat request to the LLM API and returns the response.
     *
     * @param request The chat request object.
     * @return The chat response from the API.
     * @throws Exception if the request fails or the response cannot be parsed.
     */
    OllamaApi.ChatResponse chat(OllamaApi.ChatRequest request) throws Exception;
    
    /**
     * Gets the name of the LLM provider.
     *
     * @return The provider name (e.g., "ollama", "huggingface", "llama-server")
     */
    String getProviderName();
}
