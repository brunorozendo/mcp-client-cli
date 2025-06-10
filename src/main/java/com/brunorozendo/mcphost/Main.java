package com.brunorozendo.mcphost;

import com.brunorozendo.mcphost.control.ChatController;
import com.brunorozendo.mcphost.control.McpConnectionManager;
import com.brunorozendo.mcphost.control.SystemPromptBuilder;
import com.brunorozendo.mcphost.model.McpConfig;
import com.brunorozendo.mcphost.model.OllamaApi;
import com.brunorozendo.mcphost.service.McpConfigLoader;
import com.brunorozendo.mcphost.service.OllamaApiClient;
import com.brunorozendo.mcphost.util.LoadingAnimator;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(name = "mcphost", mixinStandardHelpOptions = true, version = "mcphost 1.0",
        description = "A host that connects Large Language Models with MCP-compliant servers (tools, resources, etc.).")
public class Main implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    @Option(names = {"-m", "--model"}, required = true, description = "LLM model name (e.g., 'ollama:qwen:7b' or just 'qwen:7b' for Ollama)")
    private String llmModelFullName;

    @Option(names = {"--config"}, required = true, description = "Path to the mcp.json configuration file")
    private File mcpConfigFile;

    @Option(names = {"--ollama-base-url"}, description = "Base URL for the Ollama API", defaultValue = "http://localhost:11434")
    private String ollamaBaseUrl;

    @Override
    public Integer call() throws Exception {
        PrintWriter consoleWriter = new PrintWriter(System.out, true);
        LoadingAnimator animator = new LoadingAnimator(consoleWriter);

        logger.info("mcphost is starting...");
        logger.info("LLM Model: {}", llmModelFullName);
        logger.info("Ollama API URL: {}", ollamaBaseUrl);
        logger.info("MCP Config: {}", mcpConfigFile.getAbsolutePath());

        // 1. Load Configuration
        McpConfig mcpConfig = loadConfiguration(mcpConfigFile);
        if (mcpConfig == null) {
            return 1; // Indicate error
        }

        // 2. Initialize MCP Connection Manager
        McpConnectionManager mcpConnectionManager = new McpConnectionManager();
        mcpConnectionManager.initializeClients(mcpConfig);

        // 3. Initialize Ollama API Client
        String ollamaModelName = parseOllamaModelName(llmModelFullName);
        OllamaApiClient ollamaApiClient = new OllamaApiClient(ollamaBaseUrl);
        logger.info("Ollama API Client is targeting model: {}", ollamaModelName);

        // 4. Register a shutdown hook to clean up resources
        registerShutdownHook(animator, mcpConnectionManager);

        // 5. Fetch all capabilities from MCP servers
        List<McpSchema.Tool> allMcpTools = mcpConnectionManager.getAllTools();
        List<McpSchema.Resource> allMcpResources = mcpConnectionManager.getAllResources();
        List<McpSchema.Prompt> allMcpPrompts = mcpConnectionManager.getAllPrompts();

        // 6. Prepare for the LLM: Convert MCP tools to Ollama format and build a system prompt
        List<OllamaApi.Tool> ollamaTools = SchemaConverter.convertMcpToolsToOllamaTools(allMcpTools);
        String systemPrompt = SystemPromptBuilder.build(allMcpTools, allMcpResources, allMcpPrompts);

        // 7. Start the interactive chat
        ChatController chatController = new ChatController(
                ollamaModelName,
                ollamaApiClient,
                mcpConnectionManager,
                animator,
                systemPrompt,
                ollamaTools
        );

        chatController.startInteractiveSession();

        return 0;
    }

    private McpConfig loadConfiguration(File configFile) {
        McpConfigLoader configLoader = new McpConfigLoader();
        try {
            return configLoader.load(configFile);
        } catch (Exception e) {
            logger.error("Fatal: Failed to load MCP configuration from {}: {}", configFile.getAbsolutePath(), e.getMessage());
            return null;
        }
    }

    private String parseOllamaModelName(String llmModelString) {
        if (llmModelString.startsWith("ollama:")) {
            return llmModelString.substring("ollama:".length());
        }
        return llmModelString;
    }

    private void registerShutdownHook(LoadingAnimator animator, McpConnectionManager mcpConnectionManager) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Initiating mcphost shutdown sequence...");
            if (animator != null) animator.stop();
            if (mcpConnectionManager != null) mcpConnectionManager.closeAllClients();
            logger.info("mcphost shutdown complete. Goodbye!");
        }));
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
