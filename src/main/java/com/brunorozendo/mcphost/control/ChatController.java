package com.brunorozendo.mcphost.control;

import com.brunorozendo.mcphost.model.OllamaApi;
import com.brunorozendo.mcphost.service.llm.LlmApiClient;
import com.brunorozendo.mcphost.util.LoadingAnimator;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Manages the interactive chat session between the user, the LLM, and the MCP servers.
 */
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    private static final Logger cliLogger = LoggerFactory.getLogger("CLI");
    private static final Pattern THINK_TAG_PATTERN = Pattern.compile("<think>(.*?)</think>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final String modelName;
    private final LlmApiClient llmApiClient;
    private final McpConnectionManager mcpConnectionManager;
    private final LoadingAnimator animator;
    private final List<OllamaApi.Tool> ollamaTools;
    private final List<OllamaApi.Message> conversationHistory = new ArrayList<>();

    public ChatController(String modelName, LlmApiClient llmApiClient, McpConnectionManager mcpConnectionManager,
                          LoadingAnimator animator, String systemPrompt, List<OllamaApi.Tool> ollamaTools) {
        this.modelName = modelName;
        this.llmApiClient = llmApiClient;
        this.mcpConnectionManager = mcpConnectionManager;
        this.animator = animator;
        this.ollamaTools = ollamaTools;

        // Initialize conversation with the system prompt
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            logger.debug("Initializing with System Prompt:\n{}", systemPrompt);
            this.conversationHistory.add(new OllamaApi.Message("system", systemPrompt));
        }
    }

    /**
     * Starts and manages the main interactive loop with the user.
     */
    public void startInteractiveSession() {
        cliLogger.info("\n✅ Interactive chat started. Type 'exit' or 'quit' to end.");
        cliLogger.info("============================================================");

        try (BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                cliLogger.info(""); // New line for readability
                String userInput = promptUser(consoleReader);

                if (userInput == null || "exit".equalsIgnoreCase(userInput.trim()) || "quit".equalsIgnoreCase(userInput.trim())) {
                    break;
                }

                // Add user message to history
                conversationHistory.add(new OllamaApi.Message("user", userInput));

                // Process the turn, including potential tool calls
                processConversationTurn(userInput);
            }
        } catch (Exception e) {
            logger.error("An unexpected error occurred in the chat loop.", e);
            cliLogger.error("An unexpected error occurred. Please check the logs.");
        }

        cliLogger.info("\n============================================================");
        cliLogger.info("Chat session ended.");
    }

    private String promptUser(BufferedReader reader) throws Exception {
        PrintWriter consoleWriter = new PrintWriter(System.out, true);
        consoleWriter.print("You: ");
        consoleWriter.flush();
        return reader.readLine();
    }

    /**
     * Handles a single turn of the conversation, which may involve multiple calls to the LLM
     * if tool usage is required.
     */
    private void processConversationTurn(String userInput) {
        boolean requiresFollowUp;
        do {
            requiresFollowUp = false;

            // 1. Call the LLM with the current conversation history
            OllamaApi.ChatResponse chatResponse = callLlm(userInput);
            if (chatResponse == null || chatResponse.message() == null) {
                cliLogger.error("LLM: (No response received due to an API error)");
                break; // Exit the loop on API error
            }

            OllamaApi.Message assistantMessage = chatResponse.message();
            conversationHistory.add(assistantMessage); // Add assistant's response to history

            // 2. Display the assistant's thinking and text content
            displayAssistantMessage(assistantMessage);

            // 3. If the assistant requested tool calls, execute them
            if (assistantMessage.tool_calls() != null && !assistantMessage.tool_calls().isEmpty()) {
                executeToolCalls(assistantMessage.tool_calls());
                requiresFollowUp = true; // A tool was called, so we need to send the result back to the LLM
            }

        } while (requiresFollowUp);
    }

    private OllamaApi.ChatResponse callLlm(String userInput) {
        OllamaApi.ChatRequest chatRequest = new OllamaApi.ChatRequest(
                modelName,
                new ArrayList<>(conversationHistory), // Send a copy
                false,
                ollamaTools.isEmpty() ? null : ollamaTools
        );

        String thinkingMessage = extractThinkingMessage(userInput, "LLM is thinking...");
        animator.start(thinkingMessage);
        try {
            return llmApiClient.chat(chatRequest);
        } catch (Exception e) {
            logger.error("Error communicating with {} API: {}", llmApiClient.getProviderName(), e.getMessage(), e);
            cliLogger.error("LLM: (Error communicating with {} API: {})", llmApiClient.getProviderName(), e.getMessage());
            return null;
        } finally {
            animator.stop();
        }
    }

    private void displayAssistantMessage(OllamaApi.Message assistantMessage) {
        String assistantContent = assistantMessage.content() != null ? assistantMessage.content() : "";
        String displayContent = assistantContent;

        Matcher assistantThinkMatcher = THINK_TAG_PATTERN.matcher(assistantContent);
        boolean assistantIsThinking = assistantThinkMatcher.find();
        String assistantThinkContent = "";

        if (assistantIsThinking) {
            assistantThinkContent = assistantThinkMatcher.group(1).trim();
            // Remove the <think> tags for final display
            displayContent = assistantThinkMatcher.replaceAll("").trim();
        }

        // Display the main text content if it exists
        if (!displayContent.isEmpty()) {
            cliLogger.info("LLM: " + displayContent);
        }
        // If there's no text but there was a thought, display the thought
        else if (assistantIsThinking && (assistantMessage.tool_calls() == null || assistantMessage.tool_calls().isEmpty())) {
            if (!assistantThinkContent.isEmpty()) {
                cliLogger.info("LLM (thinking): " + assistantThinkContent);
            }
        }
    }

    private void executeToolCalls(List<OllamaApi.ToolCall> toolCalls) {
        for (OllamaApi.ToolCall toolCall : toolCalls) {
            String toolName = toolCall.function().name();
            Map<String, Object> toolArgs = toolCall.function().arguments();

            cliLogger.info("LLM -> Tool Call: {} | Args: {}", toolName, toolArgs);

            if (toolArgs == null) {
                logger.error("Tool call for '{}' received null arguments.", toolName);
                addToolResultToHistory("Error: Tool " + toolName + " called with no arguments.");
                continue;
            }

            String toolThinkingMessage = "Executing tool " + toolName + "...";
            animator.start(toolThinkingMessage);

            McpSchema.CallToolResult mcpToolResult;
            try {
                mcpToolResult = mcpConnectionManager.callTool(toolName, toolArgs);
            } catch (Exception e) {
                logger.error("Error executing MCP tool '{}': {}", toolName, e.getMessage(), e);
                mcpToolResult = new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error during tool execution: " + e.getMessage())), true);
            } finally {
                animator.stop();
            }

            String toolResultString = formatToolResult(toolName, mcpToolResult);
            cliLogger.info("Tool -> Result: {}", toolResultString);
            addToolResultToHistory(toolResultString);
        }
    }

    private String formatToolResult(String toolName, McpSchema.CallToolResult result) {
        // Join the text content from the result.
        String content = result.content().stream()
                .filter(c -> c instanceof McpSchema.TextContent)
                .map(c -> ((McpSchema.TextContent) c).text())
                .collect(Collectors.joining("\n"));

        if (content.isEmpty() && !result.content().isEmpty()) {
            // Fallback for non-text content, just use toString()
            content = result.content().get(0).toString();
        } else if (content.isEmpty()) {
            content = "Tool " + toolName + " executed with no output.";
        }

        if (result.isError() != null && result.isError()) {
            return "Error from tool " + toolName + ": " + content;
        }
        return content;
    }

    private void addToolResultToHistory(String toolResultString) {
        // The role for tool results is 'tool'
        conversationHistory.add(new OllamaApi.Message("tool", toolResultString));
    }

    private String extractThinkingMessage(String text, String defaultMessage) {
        Matcher matcher = THINK_TAG_PATTERN.matcher(text);
        if (matcher.find()) {
            String thinkContent = matcher.group(1).trim();
            if (!thinkContent.isEmpty()) {
                return "LLM: " + thinkContent;
            }
        }
        return defaultMessage;
    }
}
