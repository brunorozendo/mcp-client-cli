package com.brunorozendo.mcphost;

import com.brunorozendo.mcphost.control.ChatController;
import com.brunorozendo.mcphost.control.McpConnectionManager;
import com.brunorozendo.mcphost.control.SystemPromptBuilder;
import com.brunorozendo.mcphost.model.McpConfig;
import com.brunorozendo.mcphost.model.OllamaApi;
import com.brunorozendo.mcphost.service.McpConfigLoader;
import com.brunorozendo.mcphost.service.llm.LlmApiClient;
import com.brunorozendo.mcphost.service.llm.LlmApiClientFactory;
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

@Command(name = "mcphost", mixinStandardHelpOptions = true, version = "mcphost 1.0",
        description = "A host that connects Large Language Models with MCP-compliant servers (tools, resources, etc.).")
public class Main implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    @Option(names = {"-m", "--model"}, required = true, 
            description = "LLM model name. Formats:\n" +
                         "  - ollama:model-name (e.g., 'ollama:qwen2.5-coder:32b')\n" +
                         "  - huggingface:model-name or hf:model-name (e.g., 'hf:meta-llama/Llama-3.1-8B-Instruct')\n" +
                         "  - llama-server:model-name (e.g., 'llama-server:qwen2.5-coder:32b')\n" +
                         "  - model-name (defaults to Ollama for backward compatibility)")
    private String llmModelFullName;

    @Option(names = {"--config"}, required = true, description = "Path to the mcp.json configuration file")
    private File mcpConfigFile;

    @Option(names = {"--base-url"}, description = "Base URL for the LLM API. Defaults:\n" +
                                                  "  - Ollama: http://localhost:11434\n" +
                                                  "  - HuggingFace/llama-server: http://localhost:8080")
    private String baseUrl;

    @Option(names = {"--api-key"}, description = "API key for authentication (required for HuggingFace with auth)")
    private String apiKey;

    @Option(names = {"--hf-token"}, description = "HuggingFace token (alias for --api-key)")
    private String hfToken;

    // Deprecated option for backward compatibility
    @Option(names = {"--ollama-base-url"}, description = "Base URL for the Ollama API (deprecated, use --base-url)", 
            hidden = true)
    private String ollamaBaseUrl;

    @Override
    public Integer call() throws Exception {
        PrintWriter consoleWriter = new PrintWriter(System.out, true);
        LoadingAnimator animator = new LoadingAnimator(consoleWriter);

        logger.info("mcphost is starting...");
        logger.info("LLM Model: {}", llmModelFullName);

        // Handle deprecated option
        if (ollamaBaseUrl != null && baseUrl == null) {
            logger.warn("--ollama-base-url is deprecated. Please use --base-url instead.");
            baseUrl = ollamaBaseUrl;
        }

        // Handle HF token alias
        if (hfToken != null && apiKey == null) {
            apiKey = hfToken;
        }

        // 1. Load Configuration
        McpConfig mcpConfig = loadConfiguration(mcpConfigFile);
        if (mcpConfig == null) {
            return 1; // Indicate error
        }

        // 2. Initialize MCP Connection Manager
        McpConnectionManager mcpConnectionManager = new McpConnectionManager();
        mcpConnectionManager.initializeClients(mcpConfig);

        // 3. Initialize LLM API Client
        LlmApiClient llmApiClient;
        String modelName;
        try {
            llmApiClient = LlmApiClientFactory.createClient(llmModelFullName, baseUrl, apiKey);
            modelName = LlmApiClientFactory.extractModelName(llmModelFullName);
            logger.info("{} API Client initialized", llmApiClient.getProviderName());
            logger.info("Target model: {}", modelName);
            if (baseUrl != null) {
                logger.info("API URL: {}", baseUrl);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid model specification: {}", e.getMessage());
            return 1;
        }

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
                modelName,
                llmApiClient,
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
