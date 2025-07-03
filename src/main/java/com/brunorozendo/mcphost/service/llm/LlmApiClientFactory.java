package com.brunorozendo.mcphost.service.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating LLM API clients based on the model specification.
 */
public class LlmApiClientFactory {
    private static final Logger logger = LoggerFactory.getLogger(LlmApiClientFactory.class);
    
    /**
     * Creates an LLM API client based on the model specification.
     * 
     * @param modelSpec The model specification (e.g., "ollama:qwen2.5-coder:32b", "huggingface:meta-llama/Llama-3.1-8B-Instruct")
     * @param baseUrl The base URL for the API (optional for some providers)
     * @param apiKey The API key (optional for some providers)
     * @return The appropriate LLM API client
     * @throws IllegalArgumentException if the model specification is invalid
     */
    public static LlmApiClient createClient(String modelSpec, String baseUrl, String apiKey) {
        if (modelSpec == null || modelSpec.isEmpty()) {
            throw new IllegalArgumentException("Model specification cannot be null or empty");
        }
        
        String[] parts = modelSpec.split(":", 2);
        String provider = parts[0].toLowerCase();
        String modelName = parts.length > 1 ? parts[1] : "";
        
        logger.info("Creating LLM client for provider: {} with model: {}", provider, modelName);
        
        switch (provider) {
            case "ollama":
                if (baseUrl == null || baseUrl.isEmpty()) {
                    baseUrl = "http://localhost:11434";
                }
                return new OllamaApiClientImpl(baseUrl);
                
            case "huggingface":
            case "hf":
                if (baseUrl == null || baseUrl.isEmpty()) {
                    baseUrl = "http://localhost:8080"; // Default TGI port
                }
                return new HuggingFaceApiClient(baseUrl, apiKey);
                
            case "llama-server":
            case "llamaserver":
            case "llama_server":
                if (baseUrl == null || baseUrl.isEmpty()) {
                    baseUrl = "http://localhost:8080"; // Default llama.cpp server port
                }
                return new LlamaServerApiClient(baseUrl);
                
            default:
                // If no provider specified, assume Ollama for backward compatibility
                if (!modelSpec.contains(":")) {
                    logger.warn("No provider specified in model '{}', defaulting to Ollama", modelSpec);
                    if (baseUrl == null || baseUrl.isEmpty()) {
                        baseUrl = "http://localhost:11434";
                    }
                    return new OllamaApiClientImpl(baseUrl);
                }
                throw new IllegalArgumentException("Unknown LLM provider: " + provider);
        }
    }
    
    /**
     * Extracts the model name from the model specification.
     * 
     * @param modelSpec The model specification (e.g., "ollama:qwen2.5-coder:32b")
     * @return The model name without the provider prefix
     */
    public static String extractModelName(String modelSpec) {
        if (modelSpec == null || modelSpec.isEmpty()) {
            return "";
        }
        
        String[] parts = modelSpec.split(":", 2);
        if (parts.length > 1) {
            return parts[1];
        }
        // If no provider specified, return the whole spec as model name
        return modelSpec;
    }
}
