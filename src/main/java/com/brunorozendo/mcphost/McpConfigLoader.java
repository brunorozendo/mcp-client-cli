package com.brunorozendo.mcphost;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class McpConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(McpConfigLoader.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public McpConfig load(File configFile) throws IOException {
        if (!configFile.exists()) {
            // Log here as well, though Main will also log the caught exception
            logger.error("MCP config file not found: {}", configFile.getAbsolutePath());
            throw new IOException("MCP config file not found: " + configFile.getAbsolutePath());
        }
        logger.debug("Loading MCP config from: {}", configFile.getAbsolutePath());
        return objectMapper.readValue(configFile, McpConfig.class);
    }
}