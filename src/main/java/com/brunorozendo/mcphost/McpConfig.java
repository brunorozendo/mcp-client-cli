package com.brunorozendo.mcphost;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class McpConfig {
    @JsonProperty("mcpServers")
    private Map<String, McpServerEntry> mcpServers;

    public Map<String, McpServerEntry> getMcpServers() {
        return mcpServers;
    }

    public void setMcpServers(Map<String, McpServerEntry> mcpServers) {
        this.mcpServers = mcpServers;
    }

    public static class McpServerEntry {
        @JsonProperty("command")
        private String command;
        @JsonProperty("args")
        private List<String> args;
        // Optional: environment variables if needed by MCP servers
        @JsonProperty("env")
        private Map<String, String> env;


        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
        public List<String> getArgs() { return args; }
        public void setArgs(List<String> args) { this.args = args; }
        public Map<String, String> getEnv() { return env; }
        public void setEnv(Map<String, String> env) { this.env = env; }
    }
}