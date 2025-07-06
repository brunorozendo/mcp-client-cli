package com.brunorozendo.mcphost.service.llm;

import com.brunorozendo.mcphost.model.OllamaApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * A client for interacting with llama.cpp server API.
 * The llama.cpp server provides an OpenAI-compatible API.
 */
public class LlamaServerApiClient implements LlmApiClient {
    private static final Logger logger = LoggerFactory.getLogger(LlamaServerApiClient.class);
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LlamaServerApiClient(String baseUrl) {
        this.baseUrl = baseUrl;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .version(HttpClient.Version.HTTP_1_1) // Force HTTP/1.1 to avoid HTTP/2 issues
                .build();

        this.objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    @Override
    public OllamaApi.ChatResponse chat(OllamaApi.ChatRequest request) throws Exception {
        // Convert Ollama format to OpenAI format for llama.cpp server
        ObjectNode openAiRequest = convertToOpenAiFormat(request);
        
        String requestBody = objectMapper.writeValueAsString(openAiRequest);
        logger.debug("Llama Server Request to {}: {}", baseUrl + "/v1/chat/completions", requestBody);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        
        HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        // Log response
        logResponse(httpResponse);

        if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
            return convertFromOpenAiFormat(httpResponse.body(), request.model());
        } else {
            String errorMessage = "Llama Server API request failed with status " + httpResponse.statusCode() +
                    ": " + httpResponse.body();
            logger.error(errorMessage);
            throw new RuntimeException(errorMessage);
        }
    }

    @Override
    public String getProviderName() {
        return "llama-server";
    }

    private ObjectNode convertToOpenAiFormat(OllamaApi.ChatRequest request) throws Exception {
        ObjectNode openAiRequest = objectMapper.createObjectNode();
        
        // llama.cpp server doesn't use model name in the request
        // as it serves a single model loaded at startup
        
        // Convert messages
        ArrayNode messages = objectMapper.createArrayNode();
        for (OllamaApi.Message msg : request.messages()) {
            ObjectNode message = objectMapper.createObjectNode();
            message.put("role", msg.role());
            
            if (msg.content() != null) {
                message.put("content", msg.content());
            }
            
            // Convert tool calls if present
            if (msg.tool_calls() != null && !msg.tool_calls().isEmpty()) {
                ArrayNode toolCalls = objectMapper.createArrayNode();
                for (OllamaApi.ToolCall toolCall : msg.tool_calls()) {
                    ObjectNode tc = objectMapper.createObjectNode();
                    tc.put("type", "function");
                    ObjectNode function = objectMapper.createObjectNode();
                    function.put("name", toolCall.function().name());
                    
                    // Convert arguments to JSON string as expected by OpenAI format
                    String argsJson = objectMapper.writeValueAsString(toolCall.function().arguments());
                    function.put("arguments", argsJson);
                    
                    tc.set("function", function);
                    toolCalls.add(tc);
                }
                message.set("tool_calls", toolCalls);
            }
            
            messages.add(message);
        }
        openAiRequest.set("messages", messages);
        
        // Convert tools if present (llama.cpp server may support function calling)
        if (request.tools() != null && !request.tools().isEmpty()) {
            ArrayNode tools = objectMapper.createArrayNode();
            for (OllamaApi.Tool tool : request.tools()) {
                ObjectNode t = objectMapper.createObjectNode();
                t.put("type", tool.type());
                ObjectNode function = objectMapper.createObjectNode();
                function.put("name", tool.function().name());
                function.put("description", tool.function().description());
                
                // Convert parameters
                if (tool.function().parameters() != null) {
                    function.set("parameters", convertJsonSchema(tool.function().parameters()));
                }
                
                t.set("function", function);
                tools.add(t);
            }
            openAiRequest.set("tools", tools);
        }
        
        // Set streaming to false
        openAiRequest.put("stream", request.stream());
        
        // Add common parameters from options
        if (request.options() != null) {
            Map<String, Object> options = request.options();
            if (options.containsKey("temperature")) {
                openAiRequest.put("temperature", (Double) options.get("temperature"));
            }
            if (options.containsKey("max_tokens")) {
                openAiRequest.put("max_tokens", (Integer) options.get("max_tokens"));
            }
            if (options.containsKey("top_p")) {
                openAiRequest.put("top_p", (Double) options.get("top_p"));
            }
            if (options.containsKey("top_k")) {
                openAiRequest.put("top_k", (Integer) options.get("top_k"));
            }
            if (options.containsKey("repeat_penalty")) {
                openAiRequest.put("repeat_penalty", (Double) options.get("repeat_penalty"));
            }
        }
        
        return openAiRequest;
    }

    private JsonNode convertJsonSchema(OllamaApi.JsonSchema schema) {
        ObjectNode node = objectMapper.createObjectNode();
        
        if (schema.type() != null) {
            node.put("type", schema.type());
        }
        if (schema.description() != null) {
            node.put("description", schema.description());
        }

        // For object types, always include properties field (even if empty)
        if ("object".equals(schema.type())) {
            ObjectNode properties = objectMapper.createObjectNode();
            if (schema.properties() != null) {
                for (Map.Entry<String, OllamaApi.JsonSchema> entry : schema.properties().entrySet()) {
                    properties.set(entry.getKey(), convertJsonSchema(entry.getValue()));
                }
            }
            node.set("properties", properties);
        } else if (schema.properties() != null) {
            // For non-object types, only add properties if they exist
            ObjectNode properties = objectMapper.createObjectNode();
            for (Map.Entry<String, OllamaApi.JsonSchema> entry : schema.properties().entrySet()) {
                properties.set(entry.getKey(), convertJsonSchema(entry.getValue()));
            }
            node.set("properties", properties);
        }
        if (schema.items() != null) {
            node.set("items", convertJsonSchema(schema.items()));
        }
        if (schema.required() != null) {
            ArrayNode required = objectMapper.createArrayNode();
            for (String req : schema.required()) {
                required.add(req);
            }
            node.set("required", required);
        }
        if (schema.enumValues() != null) {
            ArrayNode enumValues = objectMapper.createArrayNode();
            for (Object val : schema.enumValues()) {
                enumValues.add(objectMapper.valueToTree(val));
            }
            node.set("enum", enumValues);
        }
        if (schema.format() != null) {
            node.put("format", schema.format());
        }
        
        return node;
    }

    private OllamaApi.ChatResponse convertFromOpenAiFormat(String responseBody, String modelName) throws Exception {
        JsonNode response = objectMapper.readTree(responseBody);
        
        // Extract the message from the first choice
        JsonNode choice = response.get("choices").get(0);
        JsonNode message = choice.get("message");
        
        // Convert message
        String role = message.get("role").asText();
        String content = message.has("content") && !message.get("content").isNull() 
            ? message.get("content").asText() 
            : "";
        
        // Convert tool calls if present
        List<OllamaApi.ToolCall> toolCalls = null;
        if (message.has("tool_calls") && message.get("tool_calls").isArray()) {
            toolCalls = new ArrayList<>();
            for (JsonNode tc : message.get("tool_calls")) {
                if (tc.has("function")) {
                    JsonNode func = tc.get("function");
                    String name = func.get("name").asText();
                    
                    // Parse arguments
                    Map<String, Object> arguments = new HashMap<>();
                    if (func.has("arguments")) {
                        JsonNode argsNode = func.get("arguments");
                        if (argsNode.isTextual()) {
                            // Arguments are JSON string
                            arguments = objectMapper.readValue(argsNode.asText(), Map.class);
                        } else {
                            // Arguments are already an object
                            arguments = objectMapper.convertValue(argsNode, Map.class);
                        }
                    }
                    
                    OllamaApi.FunctionCall functionCall = new OllamaApi.FunctionCall(name, arguments);
                    toolCalls.add(new OllamaApi.ToolCall(functionCall));
                }
            }
        }
        
        OllamaApi.Message ollamaMessage = new OllamaApi.Message(role, content, null, toolCalls);
        
        // Extract finish reason
        String finishReason = choice.has("finish_reason") && !choice.get("finish_reason").isNull()
            ? choice.get("finish_reason").asText()
            : null;
        
        // Extract usage information if available
        Long totalDuration = null;
        Integer promptTokens = null;
        Integer completionTokens = null;
        
        if (response.has("usage")) {
            JsonNode usage = response.get("usage");
            if (usage.has("prompt_tokens")) {
                promptTokens = usage.get("prompt_tokens").asInt();
            }
            if (usage.has("completion_tokens")) {
                completionTokens = usage.get("completion_tokens").asInt();
            }
        }
        
        // Create response
        return new OllamaApi.ChatResponse(
            modelName,
            response.has("created") ? response.get("created").asText() : String.valueOf(System.currentTimeMillis()),
            ollamaMessage,
            true, // done
            totalDuration,
            null, // loadDuration
            promptTokens,
            null, // promptEvalDuration
            completionTokens,
            null, // evalDuration
            finishReason
        );
    }

    private void logResponse(HttpResponse<String> httpResponse) {
        int statusCode = httpResponse.statusCode();
        String body = httpResponse.body();

        if (statusCode >= 200 && statusCode < 300) {
            logger.debug("Llama Server Response Status: {}", statusCode);
            logger.trace("Llama Server Response Body: {}", body);
        } else {
            logger.error("Llama Server Error Response (Status {}): {}", statusCode, body);
        }
    }
}
