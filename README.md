# MCPHost

MCPHost is a Java-based bridge that connects Large Language Models (LLMs) with Model Context Protocol (MCP) servers, enabling LLMs to interact with external tools, resources, and prompts through a standardized protocol.

## Overview

MCPHost acts as an intermediary between:
- **LLMs** (currently supporting Ollama models)
- **MCP Servers** that provide tools, resources, and prompts

This allows you to enhance your LLM interactions with custom functionality provided by MCP-compliant servers, such as filesystem operations, API integrations, or any custom tools you develop.

## Features

- 🔌 **MCP Protocol Support**: Full compatibility with Model Context Protocol specification
- 🤖 **Ollama Integration**: Native support for Ollama-hosted models
- 🛠️ **Tool Execution**: Execute MCP tools and return results to the LLM
- 📚 **Resource Access**: Query and retrieve MCP resources
- 💬 **Interactive Chat**: Built-in interactive chat interface
- ⚡ **Concurrent Connections**: Support for multiple MCP servers simultaneously
- 🔧 **Flexible Configuration**: JSON-based configuration for easy setup
- 📝 **Comprehensive Logging**: Built-in logging with Logback

## Requirements

- Java 21 or higher
- Ollama installed and running (for LLM inference)
- One or more MCP-compliant servers

## Installation

### Building from Source

1. Clone the repository:
```bash
git clone <repository-url>
cd MCPHost
```

2. Build the project using Gradle:
```bash
./gradlew build
```

3. The executable JAR will be created in `build/libs/`

### Using Pre-built Distributions

After building, you can find distribution packages in `build/distributions/`:
- `MCPHost-0.1.0-SNAPSHOT.zip`
- `MCPHost-0.1.0-SNAPSHOT.tar`

Extract either package and use the scripts in the `bin/` directory.

## Configuration

Create an `mcp.json` configuration file to define your MCP servers:

```json
{
  "mcpServers": {
    "filesystem-server": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/filesystem-mcp-server.jar",
        "/path/to/allowed/directory"
      ]
    },
    "another-server": {
      "command": "python",
      "args": [
        "/path/to/mcp_server.py"
      ],
      "env": {
        "API_KEY": "your-api-key"
      }
    }
  },
  "globalSettings": {
    "defaultTimeout": 30000,
    "enableDebugLogging": false,
    "maxConcurrentConnections": 10
  }
}
```

### Configuration Options

- **mcpServers**: Map of MCP server configurations
  - **command**: The executable command to run the server
  - **args**: Command line arguments for the server
  - **env**: Environment variables (optional)
- **globalSettings**: Optional global configuration
  - **defaultTimeout**: Timeout for MCP operations in milliseconds
  - **enableDebugLogging**: Enable verbose debug logging
  - **maxConcurrentConnections**: Maximum number of concurrent MCP connections

## Usage

### Basic Usage

Run MCPHost with the required parameters:

```bash
java -jar MCPHost-0.1.0-SNAPSHOT.jar \
  --model "qwen:7b" \
  --config /path/to/mcp.json
```

### Command Line Options

- `-m, --model`: LLM model name (required)
  - Format: `model:tag` for Ollama (e.g., `qwen:7b`, `llama3:8b`)
  - Can include `ollama:` prefix (e.g., `ollama:qwen:7b`)
- `--config`: Path to the mcp.json configuration file (required)
- `--ollama-base-url`: Base URL for Ollama API (default: `http://localhost:11434`)
- `-h, --help`: Show help message
- `-V, --version`: Show version information

### Interactive Chat

Once started, MCPHost provides an interactive chat interface:

1. Type your message and press Enter
2. The LLM will process your request and may call MCP tools if needed
3. Tool results are automatically fed back to the LLM
4. Type `exit` or `quit` to end the session

### Example Session

```
✅ Interactive chat started. Type 'exit' or 'quit' to end.
============================================================

You: What files are in the current directory?

LLM: I'll check the current directory for you.

LLM -> Tool Call: list_directory | Args: {path: "."}

Tool -> Result: Found 15 files and directories...

LLM: The current directory contains 15 items including...

You: exit

============================================================
Chat session ended.
```

## Architecture

### Components

1. **Main**: Entry point and CLI argument parsing using picocli
2. **McpConnectionManager**: Manages connections to multiple MCP servers
3. **ChatController**: Orchestrates the chat loop and tool execution
4. **OllamaApiClient**: Handles communication with Ollama API
5. **SchemaConverter**: Converts between MCP and Ollama tool formats
6. **SystemPromptBuilder**: Builds system prompts with available tools/resources

### Flow

1. Load configuration from `mcp.json`
2. Initialize connections to all configured MCP servers
3. Fetch available tools, resources, and prompts from MCP servers
4. Convert MCP tools to Ollama-compatible format
5. Start interactive chat session
6. Process user messages through LLM
7. Execute requested tools via MCP servers
8. Feed results back to LLM for response generation

## Development

### Project Structure

```
MCPHost/
├── src/main/java/com/brunorozendo/mcphost/
│   ├── Main.java                    # Entry point
│   ├── SchemaConverter.java         # MCP-Ollama schema conversion
│   ├── control/                     # Controllers
│   │   ├── ChatController.java      # Chat orchestration
│   │   ├── McpConnectionManager.java # MCP connection management
│   │   └── SystemPromptBuilder.java # System prompt generation
│   ├── model/                       # Data models
│   │   ├── McpConfig.java          # Configuration model
│   │   └── OllamaApi.java          # Ollama API models
│   ├── service/                     # Services
│   │   ├── McpConfigLoader.java    # Configuration loading
│   │   └── OllamaApiClient.java    # Ollama API client
│   └── util/                        # Utilities
│       └── LoadingAnimator.java     # CLI loading animation
├── build.gradle                     # Gradle build configuration
└── mcp.json                        # Example configuration
```

### Building Native Image (GraalVM)

The project includes GraalVM Native Image support:

```bash
./gradlew nativeCompile
```

This creates a native executable at `build/native/nativeCompile/mcphost`.

## Logging

MCPHost uses SLF4J with Logback for logging. Configure logging in `src/main/resources/logback.xml`.

Log levels:
- **INFO**: General operational messages
- **DEBUG**: Detailed debugging information
- **ERROR**: Error conditions

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

[Include your license information here]

## Acknowledgments

- Built with the [Model Context Protocol SDK](https://github.com/modelcontextprotocol)
- CLI interface powered by [picocli](https://picocli.info/)
- JSON processing with [Jackson](https://github.com/FasterXML/jackson)

## Troubleshooting

### Common Issues

1. **Ollama connection failed**: Ensure Ollama is running and accessible at the specified URL
2. **MCP server failed to start**: Check the command and arguments in your `mcp.json`
3. **Tool execution timeout**: Increase `defaultTimeout` in global settings

### Debug Mode

Enable debug logging for troubleshooting:

```json
{
  "globalSettings": {
    "enableDebugLogging": true
  }
}
```

Or set the log level in `logback.xml`.
