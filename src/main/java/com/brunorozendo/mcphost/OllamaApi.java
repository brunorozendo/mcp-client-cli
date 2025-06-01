package com.brunorozendo.mcphost;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class OllamaApi {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(
            @JsonProperty("role") String role,
            @JsonProperty("content") String content,
            @JsonProperty("images") List<String> images,
            @JsonProperty("tool_calls") List<ToolCall> tool_calls
    ) {
        public Message(String role, String content, List<String> images) {
            this(role, content, images, null);
        }
        public Message(String role, String content) {
            this(role, content, null, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolCall(
            @JsonProperty("function") FunctionCall function
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FunctionCall(
            @JsonProperty("name") String name,
            // CHANGE HERE: Expect a Map directly, not a JSON string
            @JsonProperty("arguments") Map<String, Object> arguments
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Tool(
            @JsonProperty("type") String type,
            @JsonProperty("function") OllamaFunction function
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OllamaFunction(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("parameters") JsonSchema parameters
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JsonSchema(
            @JsonProperty("type") String type,
            @JsonProperty("description") String description,
            @JsonProperty("properties") Map<String, JsonSchema> properties,
            @JsonProperty("items") JsonSchema items,
            @JsonProperty("required") List<String> required,
            @JsonProperty("enum") List<Object> enumValues,
            @JsonProperty("format") String format
    ) {
        public JsonSchema(String type, String description) {
            this(type, description, null, null, null, null, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatRequest(
            @JsonProperty("model") String model,
            @JsonProperty("messages") List<Message> messages,
            @JsonProperty("stream") boolean stream,
            @JsonProperty("tools") List<Tool> tools,
            @JsonProperty("format") Object format,
            @JsonProperty("options") Map<String, Object> options,
            @JsonProperty("keep_alive") String keep_alive
    ) {
        public ChatRequest(String model, List<Message> messages, boolean stream, List<Tool> tools) {
            this(model, messages, stream, tools, null, null, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatResponse(
            @JsonProperty("model") String model,
            @JsonProperty("created_at") String createdAt,
            @JsonProperty("message") Message message,
            @JsonProperty("done") boolean done,
            @JsonProperty("total_duration") Long totalDuration,
            @JsonProperty("load_duration") Long loadDuration,
            @JsonProperty("prompt_eval_count") Integer promptEvalCount,
            @JsonProperty("prompt_eval_duration") Long promptEvalDuration,
            @JsonProperty("eval_count") Integer evalCount,
            @JsonProperty("eval_duration") Long evalDuration,
            @JsonProperty("done_reason") String done_reason
    ) {}
}