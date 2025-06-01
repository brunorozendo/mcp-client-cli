package com.brunorozendo.mcphost;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.modelcontextprotocol.spec.McpSchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Command(name = "mcphost", mixinStandardHelpOptions = true, version = "mcphost 0.1",
        description = "MCP host to connect LLMs with MCP tools.")
public class Main implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    // Specific logger for CLI output (user-facing messages like prompts and LLM responses)
    private static final Logger cliLogger = LoggerFactory.getLogger(Main.class.getName() + ".CLI");

    private static final Pattern THINK_TAG_PATTERN = Pattern.compile("<think>(.*?)</think>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Option(names = {"-m", "--model"}, required = true, description = "LLM model name (e.g., ollama:qwen:8b or just qwen:8b for Ollama)")
    private String llmModelFullName;

    @Option(names = {"--config"}, required = true, description = "Path to mcp.json configuration file")
    private File mcpConfigFile;

    @Option(names = {"--ollama-base-url"}, description = "Base URL for Ollama API", defaultValue = "http://localhost:11434")
    private String ollamaBaseUrl;

    private McpToolClientManager mcpToolClientManager;
    private OllamaApiClient ollamaApiClient;
    private final ObjectMapper generalObjectMapper = new ObjectMapper()
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    private LoadingAnimator animator;


    @Override
    public Integer call() throws Exception {
        PrintWriter consoleWriter = new PrintWriter(System.out, true); // For animator and direct "You: " prompt
        animator = new LoadingAnimator(consoleWriter);

        logger.info("mcphost starting...");
        logger.info("LLM Model: {}", llmModelFullName);
        logger.info("Ollama API URL: {}", ollamaBaseUrl);
        logger.info("MCP Config: {}", mcpConfigFile.getAbsolutePath());

        McpConfigLoader configLoader = new McpConfigLoader();
        McpConfig mcpConfig = null;
        try {
            mcpConfig = configLoader.load(mcpConfigFile);
        } catch (Exception e) {
            logger.error("Failed to load MCP configuration from {}: {}", mcpConfigFile.getAbsolutePath(), e.getMessage());
            return 1; // Indicate error
        }


        mcpToolClientManager = new McpToolClientManager();
        mcpToolClientManager.initializeClients(mcpConfig);

        String ollamaModelName = parseOllamaModelName(llmModelFullName);
        ollamaApiClient = new OllamaApiClient(ollamaBaseUrl);
        logger.info("Ollama API Client targeting model: {}", ollamaModelName);

        List<McpSchema.Tool> allMcpTools = mcpToolClientManager.getAllTools();
        List<OllamaApi.Tool> ollamaTools = convertMcpToolsToOllamaTools(allMcpTools);

        if (ollamaTools.isEmpty()) {
            logger.warn("No MCP tools available or successfully mapped for Ollama. Proceeding without tool capabilities for LLM.");
        } else {
            logger.info("Successfully mapped {} MCP tool(s) for Ollama: {}",
                    ollamaTools.size(),
                    ollamaTools.stream().map(t -> t.function().name()).collect(Collectors.toList()));
        }

        cliLogger.info("\nInteractive chat started. Type 'exit' to quit.");
        cliLogger.info("================================================");

        java.io.BufferedReader consoleReader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
        String userInput;
        List<OllamaApi.Message> conversationHistory = new ArrayList<>();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Use logger for shutdown messages, not cliLogger, as console might be in weird state
            logger.info("Initiating mcphost shutdown sequence...");
            if (animator != null) animator.stop();
            if (mcpToolClientManager != null) mcpToolClientManager.closeAllClients();
            logger.info("mcphost shutdown complete.");
        }));

        while (true) {
            consoleWriter.print("\nYou: ");
            consoleWriter.flush();
            userInput = consoleReader.readLine();
            if (userInput == null || "exit".equalsIgnoreCase(userInput.trim())) {
                break;
            }

            conversationHistory.add(new OllamaApi.Message("user", userInput, null));

            boolean requiresFollowUp;
            do {
                requiresFollowUp = false;
                OllamaApi.ChatRequest chatRequest = new OllamaApi.ChatRequest(
                        ollamaModelName,
                        new ArrayList<>(conversationHistory),
                        false,
                        ollamaTools.isEmpty() ? null : ollamaTools
                );

                String thinkingMessage = "LLM is thinking...";
                Matcher userInputMatcher = THINK_TAG_PATTERN.matcher(userInput);
                if (userInputMatcher.find()) {
                    String thinkContent = userInputMatcher.group(1).trim();
                    if (!thinkContent.isEmpty()) {
                        thinkingMessage = "LLM: " + thinkContent;
                    }
                }

                animator.start(thinkingMessage);
                OllamaApi.ChatResponse chatResponse = null;
                try {
                    chatResponse = ollamaApiClient.chat(chatRequest);
                } catch (Exception e) {
                    logger.error("Error communicating with Ollama API: {}", e.getMessage(), e);
                    cliLogger.error("LLM: (Error communicating with API: {})", e.getMessage());
                    // Potentially break or allow user to retry, for now, continue to next user input
                    break;
                } finally {
                    animator.stop();
                }

                if (chatResponse == null) { // Should be caught by catch block, but defensive
                    cliLogger.error("LLM: (No response received due to an earlier error)");
                    break;
                }

                OllamaApi.Message assistantMessage = chatResponse.message();

                if (assistantMessage != null) {
                    String assistantContent = assistantMessage.content() != null ? assistantMessage.content() : "";
                    String displayContent = assistantContent;

                    Matcher assistantThinkMatcher = THINK_TAG_PATTERN.matcher(assistantContent);
                    boolean assistantIsThinking = assistantThinkMatcher.find();
                    String assistantThinkContent = "";

                    if (assistantIsThinking) {
                        assistantThinkContent = assistantThinkMatcher.group(1).trim();
                        displayContent = assistantThinkMatcher.replaceAll("").trim();
                    }

                    conversationHistory.add(assistantMessage);

                    if (!displayContent.isEmpty()) {
                        cliLogger.info("LLM: " + displayContent);
                    } else if (assistantIsThinking && (assistantMessage.tool_calls() == null || assistantMessage.tool_calls().isEmpty())) {
                        if (!assistantThinkContent.isEmpty()) {
                            cliLogger.info("LLM (thinking): " + assistantThinkContent);
                        }
                    }


                    if (assistantMessage.tool_calls() != null && !assistantMessage.tool_calls().isEmpty()) {
                        for (OllamaApi.ToolCall toolCall : assistantMessage.tool_calls()) {
                            cliLogger.info("LLM -> Tool Call: {} | Args: {}", toolCall.function().name(), toolCall.function().arguments());

                            Map<String, Object> toolArgsMap = toolCall.function().arguments();
                            if (toolArgsMap == null) { // Should not happen if Ollama sends valid tool calls
                                logger.error("Tool call for {} received null arguments.", toolCall.function().name());
                                conversationHistory.add(new OllamaApi.Message("tool", "Error: Tool " + toolCall.function().name() + " called with no arguments."));
                                requiresFollowUp = true;
                                continue;
                            }

                            String toolThinkingMessage = "Executing tool " + toolCall.function().name() + "...";

                            if (assistantIsThinking && !assistantThinkContent.isEmpty() && assistantMessage.tool_calls().size() == 1) {
                                toolThinkingMessage = "LLM (preparing " + toolCall.function().name() + "): " + assistantThinkContent;
                            }

                            animator.start(toolThinkingMessage);
                            McpSchema.CallToolResult mcpToolResult = null;
                            try {
                                mcpToolResult = mcpToolClientManager.callTool(
                                        toolCall.function().name(), toolArgsMap);
                            } catch (Exception e) {
                                logger.error("Error executing MCP tool {}: {}", toolCall.function().name(), e.getMessage(), e);
                                // Construct an error result to send back to LLM
                                mcpToolResult = new McpSchema.CallToolResult(
                                        List.of(new McpSchema.TextContent("Error during tool execution: " + e.getMessage())), true);
                            }
                            finally {
                                animator.stop();
                            }

                            String toolResultString;
                            if (mcpToolResult.isError() != null && mcpToolResult.isError()) {
                                toolResultString = "Error from tool " + toolCall.function().name() + ": " +
                                        mcpToolResult.content().stream()
                                                .filter(c -> c instanceof McpSchema.TextContent)
                                                .map(c -> ((McpSchema.TextContent)c).text())
                                                .collect(Collectors.joining("\n"));
                                if (toolResultString.isEmpty() && !mcpToolResult.content().isEmpty()) {
                                    toolResultString = "Error from tool " + toolCall.function().name() + ": " + mcpToolResult.content().get(0).toString();
                                }
                            } else {
                                toolResultString = mcpToolResult.content().stream()
                                        .filter(c -> c instanceof McpSchema.TextContent)
                                        .map(c -> ((McpSchema.TextContent)c).text())
                                        .collect(Collectors.joining("\n"));
                                if (toolResultString.isEmpty() && !mcpToolResult.content().isEmpty()) {
                                    toolResultString = mcpToolResult.content().get(0).toString();
                                } else if (toolResultString.isEmpty() && mcpToolResult.content().isEmpty()){
                                    toolResultString = "Tool " + toolCall.function().name() + " executed with no output.";
                                }
                            }
                            cliLogger.info("Tool -> Result: {}", toolResultString);

                            conversationHistory.add(new OllamaApi.Message("tool", toolResultString));
                            requiresFollowUp = true;
                        }
                    }
                } else {
                    logger.error("No message received from LLM.");
                    cliLogger.info("LLM: (No response received)");
                }
            } while (requiresFollowUp);
        }

        cliLogger.info("\n================================================");
        cliLogger.info("mcphost exiting. Goodbye!");
        return 0;
    }

    private String parseOllamaModelName(String llmModelString) {
        if (llmModelString.startsWith("ollama:")) {
            return llmModelString.substring("ollama:".length());
        }
        return llmModelString;
    }

    private List<OllamaApi.Tool> convertMcpToolsToOllamaTools(List<McpSchema.Tool> mcpTools) {
        if (mcpTools == null) return java.util.Collections.emptyList();
        return mcpTools.stream()
                .map(mcpTool -> {
                    OllamaApi.OllamaFunction ollamaFunction = new OllamaApi.OllamaFunction(
                            mcpTool.name(),
                            mcpTool.description(),
                            convertMcpInputSchemaToOllamaParamsSchema(mcpTool.inputSchema())
                    );
                    return new OllamaApi.Tool("function", ollamaFunction);
                })
                .collect(Collectors.toList());
    }

    private OllamaApi.JsonSchema convertMcpInputSchemaToOllamaParamsSchema(McpSchema.JsonSchema mcpInputSchema) {
        if (mcpInputSchema == null) {
            return new OllamaApi.JsonSchema("object", "No parameters", new HashMap<>(), null, new ArrayList<>(), null, null);
        }
        return convertMcpSchemaRecursive(mcpInputSchema);
    }

    private OllamaApi.JsonSchema convertMcpSchemaRecursive(McpSchema.JsonSchema mcpSchema) {
        if (mcpSchema == null) {
            return new OllamaApi.JsonSchema("string", "Undefined schema");
        }

        String type = mcpSchema.type();
        String description = null;
        List<Object> enumValues = null;
        String format = null;

        Map<String, OllamaApi.JsonSchema> ollamaProperties = null;
        OllamaApi.JsonSchema ollamaItemsSchema = null;
        List<String> required = mcpSchema.required() != null ? new ArrayList<>(mcpSchema.required()) : null;

        if ("object".equals(type) && mcpSchema.properties() != null) {
            ollamaProperties = new HashMap<>();
            for (Map.Entry<String, Object> entry : mcpSchema.properties().entrySet()) {
                String propKey = entry.getKey();
                Object propValue = entry.getValue();

                McpSchema.JsonSchema propertyMcpSchema;
                if (propValue instanceof Map) {
                    try {
                        propertyMcpSchema = generalObjectMapper.convertValue(propValue, McpSchema.JsonSchema.class);
                    } catch (IllegalArgumentException e) {
                        logger.warn("Could not convert property '{}' schema (Map) to McpSchema.JsonSchema. Defaulting to string. Error: {}", propKey, e.getMessage());
                        propertyMcpSchema = new McpSchema.JsonSchema("string", null, null, null, null, null);
                    }
                } else if (propValue instanceof McpSchema.JsonSchema) {
                    propertyMcpSchema = (McpSchema.JsonSchema) propValue;
                } else {
                    logger.warn("Property '{}' has unexpected schema type: {}. Defaulting to string.", propKey, (propValue != null ? propValue.getClass().getName() : "null"));
                    propertyMcpSchema = new McpSchema.JsonSchema("string", null, null, null, null, null);
                }
                ollamaProperties.put(propKey, convertMcpSchemaRecursive(propertyMcpSchema));
            }
        } else if ("array".equals(type)) {
            if (mcpSchema.properties() != null && mcpSchema.properties().get("items") != null) {
                Object itemsValue = mcpSchema.properties().get("items");
                McpSchema.JsonSchema itemsMcpSchema;
                if (itemsValue instanceof Map) {
                    try {
                        itemsMcpSchema = generalObjectMapper.convertValue(itemsValue, McpSchema.JsonSchema.class);
                    } catch (IllegalArgumentException e) {
                        logger.warn("Could not convert array 'items' schema (Map) to McpSchema.JsonSchema. Defaulting to string items. Error: {}", e.getMessage());
                        itemsMcpSchema = new McpSchema.JsonSchema("string", null, null, null, null, null);
                    }
                } else if (itemsValue instanceof McpSchema.JsonSchema) {
                    itemsMcpSchema = (McpSchema.JsonSchema) itemsValue;
                } else {
                    logger.warn("Array 'items' has unexpected schema type: {}. Defaulting to string items.", (itemsValue != null ? itemsValue.getClass().getName() : "null"));
                    itemsMcpSchema = new McpSchema.JsonSchema("string", null, null, null, null, null);
                }
                ollamaItemsSchema = convertMcpSchemaRecursive(itemsMcpSchema);
            } else {
                logger.warn("Array type schema for '{}' does not have a parsable 'items' definition. Defaulting to array of strings.", type);
                ollamaItemsSchema = new OllamaApi.JsonSchema("string", "Array item");
            }
        }
        return new OllamaApi.JsonSchema(type, description, ollamaProperties, ollamaItemsSchema, required, enumValues, format);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}