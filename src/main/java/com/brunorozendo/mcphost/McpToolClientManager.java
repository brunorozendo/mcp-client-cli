package com.brunorozendo.mcphost;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.time.Duration;
import java.util.ArrayList;
// import java.util.HashMap; // Not used
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpToolClientManager {
    private static final Logger logger = LoggerFactory.getLogger(McpToolClientManager.class);
    private final Map<String, McpAsyncClient> clients = new ConcurrentHashMap<>();
    private final Map<String, String> toolToServerMapping = new ConcurrentHashMap<>();

    public void initializeClients(McpConfig mcpConfig) {
        if (mcpConfig.getMcpServers() == null || mcpConfig.getMcpServers().isEmpty()) {
            logger.warn("No MCP servers defined in config. Cannot initialize tool clients.");
            return;
        }
        mcpConfig.getMcpServers().forEach((serverName, entry) -> {
            StdioClientTransport transport = null; // Declare transport outside try for potential cleanup
            try {
                ServerParameters.Builder serverParamsBuilder = ServerParameters.builder(entry.getCommand())
                        .args(entry.getArgs() != null ? entry.getArgs() : List.of());
                if (entry.getEnv() != null) {
                    serverParamsBuilder.env(entry.getEnv());
                }
                ServerParameters serverParams = serverParamsBuilder.build();

                transport = new StdioClientTransport(serverParams);

                McpAsyncClient client = McpClient.async(transport)
                        .requestTimeout(Duration.ofSeconds(30))
                        .initializationTimeout(Duration.ofSeconds(60))
                        .clientInfo(new McpSchema.Implementation("mcphost-tool-connector", "0.1"))
                        .build();

                logger.info("Initializing MCP Client for server: {} with command: {}", serverName, entry.getCommand());
                client.initialize().block(Duration.ofSeconds(60));

                if (client.isInitialized()) {
                    clients.put(serverName, client);
                    logger.info("MCP Client for server '{}' initialized successfully.", serverName);

                    McpSchema.ListToolsResult toolsResult = client.listTools().block(Duration.ofSeconds(30));
                    if (toolsResult != null && toolsResult.tools() != null) {
                        for (McpSchema.Tool tool : toolsResult.tools()) {
                            if (toolToServerMapping.containsKey(tool.name())) {
                                logger.warn("Duplicate tool name '{}' found. Previous mapping from server '{}' will be overwritten by server '{}'.",
                                        tool.name(), toolToServerMapping.get(tool.name()), serverName);
                            }
                            toolToServerMapping.put(tool.name(), serverName);
                            logger.info("  Discovered tool: {} (from server: {})", tool.name(), serverName);
                        }
                    } else {
                        logger.info("  No tools discovered for server: {}", serverName);
                    }
                } else {
                    logger.error("Failed to initialize MCP Client for server: {}", serverName);
                    if (transport != null) { // Check if transport was initialized
                        transport.closeGracefully().block(Duration.ofSeconds(5));
                    }
                }
            } catch (Exception e) {
                logger.error("Error initializing MCP Client for server '{}': {}", serverName, e.getMessage(), e);
                if (transport != null) { // Attempt to clean up transport if it was created
                    try {
                        transport.closeGracefully().block(Duration.ofSeconds(5));
                    } catch (Exception ce) {
                        logger.error("Error closing transport for failed server '{}': {}", serverName, ce.getMessage());
                    }
                }
            }
        });
    }

    public List<McpSchema.Tool> getAllTools() {
        List<McpSchema.Tool> allTools = new ArrayList<>();
        for (String toolName : toolToServerMapping.keySet()) {
            String serverName = toolToServerMapping.get(toolName);
            McpAsyncClient client = clients.get(serverName);
            if (client != null && client.isInitialized()) {
                try {
                    McpSchema.ListToolsResult toolsResult = client.listTools().block(Duration.ofSeconds(10));
                    if (toolsResult != null && toolsResult.tools() != null) {
                        toolsResult.tools().stream()
                                .filter(t -> t.name().equals(toolName))
                                .findFirst()
                                .ifPresent(allTools::add);
                    }
                } catch (Exception e) {
                    logger.error("Error listing tool '{}' from server '{}': {}", toolName, serverName, e.getMessage());
                }
            }
        }
        return allTools.stream().distinct().collect(Collectors.toList());
    }

    public McpSchema.CallToolResult callTool(String toolName, Map<String, Object> arguments) {
        String serverName = toolToServerMapping.get(toolName);
        if (serverName == null) {
            logger.error("Tool '{}' not found or server mapping missing.", toolName);
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("Error: Tool '" + toolName + "' not found.")), true);
        }
        McpAsyncClient client = clients.get(serverName);
        if (client == null || !client.isInitialized()) {
            logger.error("Client for server '{}' not found or not initialized for tool '{}'.", serverName, toolName);
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("Error: Client for tool '" + toolName + "' is not available.")), true);
        }

        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(toolName, arguments);
        try {
            logger.info("Calling tool '{}' on server '{}' with args: {}", toolName, serverName, arguments);
            return client.callTool(request).block(Duration.ofSeconds(60));
        } catch (Exception e) { // Catching generic Exception as block() can throw RuntimeException
            logger.error("Error calling tool '{}': {}", toolName, e.getMessage(), e);
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("Error calling tool '" + toolName + "': " + e.getMessage())), true);
        }
    }

    public void closeAllClients() {
        logger.info("Closing all MCP clients...");
        clients.forEach((serverName, client) -> {
            try {
                if (client.isInitialized()) {
                    logger.debug("Closing client for server '{}'", serverName);
                    client.closeGracefully().block(Duration.ofSeconds(10));
                } else {
                    logger.debug("Client for server '{}' was not initialized or already closed, attempting to ensure transport is closed.", serverName);
                    // McpAsyncClient doesn't directly expose its transport for external closure after construction.
                    // The StdioClientTransport itself should handle its resources if initialize() failed or was never fully completed.
                    // If the client object exists but isn't initialized, its internal transport might still be active if initialize() failed mid-way.
                    // This part is tricky without direct access to the transport from McpAsyncClient.
                    // For now, we rely on the client's own closeGracefully or the StdioClientTransport's internal logic.
                }
            } catch (Exception e) {
                logger.error("Error closing MCP client for server '{}': {}", serverName, e.getMessage());
            }
        });
        clients.clear();
        toolToServerMapping.clear();
        logger.info("All MCP clients closed.");
    }
}