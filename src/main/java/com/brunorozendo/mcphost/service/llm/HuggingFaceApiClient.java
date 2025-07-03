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
 * A client for interacting with Hugging Face Text Generation Inference (TGI) API.
 * Supports OpenAI-compatible endpoints (v1.4.0+).
 */
public class HuggingFaceApiClient implements LlmApiClient {
    private static final Logger logger = LoggerFactory.getLogger(HuggingFaceApiClient.class);
    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HuggingFaceApiClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        this.objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    @Override
    public OllamaApi.ChatResponse chat(OllamaApi.ChatRequest request) throws Exception {
        // Convert Ollama format to OpenAI format for TGI
        ObjectNode openAiRequest = convertToOpenAiFormat(request);
        
        String requestBody = objectMapper.writeValueAsString(openAiRequest);
        logger.debug("HuggingFace TGI Request to {}: {}", baseUrl + "/v1/chat/completions", requestBody);

        HttpRequest.Builder httpRequestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));
        
        // Add authorization header if API key is provided
        if (apiKey != null && !apiKey.isEmpty()) {
            httpRequestBuilder.header("Authorization", "Bearer " + apiKey);
        }
        
        HttpRequest httpRequest = httpRequestBuilder.build();
        HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        // Log response
        logResponse(httpResponse);

        if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
            return convertFromOpenAiFormat(httpResponse.body(), request.model());
        } else {
            String errorMessage = "HuggingFace TGI API request failed with status " + httpResponse.statusCode() +
                    ": " + httpResponse.body();
            logger.error(errorMessage);
            throw new RuntimeException(errorMessage);
        }
    }

    @Override
    public String getProviderName() {
        return "huggingface";
    }

    private ObjectNode convertToOpenAiFormat(OllamaApi.ChatRequest request) throws Exception {
        ObjectNode openAiRequest = objectMapper.createObjectNode();
        
        // Model name - TGI uses "tgi" as the model identifier
        openAiRequest.put("model", "tgi");
        
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
        
        // Convert tools if present
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
        
        // Set streaming to false (matching Ollama behavior)
        openAiRequest.put("stream", request.stream());
        
        // Add common parameters
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
        if (schema.properties() != null) {
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
                    
                    // Parse arguments - they might be a string or object
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
        
        // Create response - TGI doesn't provide all the timing information that Ollama does
        return new OllamaApi.ChatResponse(
            modelName,
            response.get("created").asText(),
            ollamaMessage,
            true, // done
            null, // totalDuration
            null, // loadDuration
            null, // promptEvalCount
            null, // promptEvalDuration
            null, // evalCount
            null, // evalDuration
            finishReason
        );
    }

    private void logResponse(HttpResponse<String> httpResponse) {
        int statusCode = httpResponse.statusCode();
        String body = httpResponse.body();

        if (statusCode >= 200 && statusCode < 300) {
            logger.debug("HuggingFace TGI Response Status: {}", statusCode);
            logger.trace("HuggingFace TGI Response Body: {}", body);
        } else {
            logger.error("HuggingFace TGI Error Response (Status {}): {}", statusCode, body);
        }
    }
}
