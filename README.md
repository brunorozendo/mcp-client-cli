# MCPHost

MCPHost is a Java-based bridge that connects Large Language Models (LLMs) with Model Context Protocol (MCP) servers, enabling LLMs to interact with external tools, resources, and prompts through a standardized protocol.

## Overview

MCPHost acts as an intermediary between:
- **LLMs** supporting:
  - Ollama models
  - Hugging Face Text Generation Inference (TGI)
  - llama.cpp server (OpenAI-compatible API)
- **MCP Servers** that provide tools, resources, and prompts

This allows you to enhance your LLM interactions with custom functionality provided by MCP-compliant servers, such as filesystem operations, API integrations, or any custom tools you develop.

## Features

- 🔌 **MCP Protocol Support**: Full compatibility with Model Context Protocol specification
- 🤖 **Multiple LLM Providers**: Support for Ollama, Hugging Face TGI, and llama.cpp server
- 🛠️ **Tool Execution**: Execute MCP tools and return results to the LLM
- 📚 **Resource Access**: Query and retrieve MCP resources
- 💬 **Interactive Chat**: Built-in interactive chat interface
- ⚡ **Concurrent Connections**: Support for multiple MCP servers simultaneously
- 🔧 **Flexible Configuration**: JSON-based configuration for easy setup
- 📝 **Comprehensive Logging**: Built-in logging with Logback

## Requirements

- Java 21 or higher
- One of the following LLM providers:
  - Ollama installed and running
  - Hugging Face TGI server running
  - llama.cpp server with OpenAI-compatible API
- One or more MCP-compliant servers

## Installation

### Building from Source

1. Clone the repository:
```bash
git clone <repository-url>
cd mcp-client-cli
```

2. Build the project using Gradle:
```bash
./gradlew build
```

3. The executable JAR will be created in `build/libs/`

### Using Pre-built Distributions

After building, you can find distribution packages in `build/distributions/`:
- `mcp-client-cli-0.1.0-SNAPSHOT.zip`
- `mcp-client-cli-0.1.0-SNAPSHOT.tar`

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
# Using Ollama
java -jar mcp-client-cli-0.1.0-SNAPSHOT.jar \
  --model "ollama:qwen2.5-coder:32b" \
  --config /path/to/mcp.json

# Using Hugging Face TGI
java -jar mcp-client-cli-0.1.0-SNAPSHOT.jar \
  --model "huggingface:meta-llama/Llama-3.1-8B-Instruct" \
  --config /path/to/mcp.json \
  --base-url "http://localhost:8080" \
  --api-key "your-hf-token"

# Using llama.cpp server
java -jar mcp-client-cli-0.1.0-SNAPSHOT.jar \
  --model "llama-server:qwen2.5-coder:32b" \
  --config /path/to/mcp.json \
  --base-url "http://localhost:8080"
```

### Command Line Options

- `-m, --model`: LLM model name (required)
  - Format varies by provider:
    - Ollama: `ollama:model-name` or just `model-name` (e.g., `ollama:qwen2.5-coder:32b`)
    - Hugging Face: `huggingface:model-name` or `hf:model-name` (e.g., `hf:meta-llama/Llama-3.1-8B-Instruct`)
    - llama.cpp server: `llama-server:model-name` (e.g., `llama-server:qwen2.5-coder:32b`)
- `--config`: Path to the mcp.json configuration file (required)
- `--base-url`: Base URL for the LLM API
  - Defaults:
    - Ollama: `http://localhost:11434`
    - Hugging Face/llama-server: `http://localhost:8080`
- `--api-key`: API key for authentication (required for HuggingFace with auth)
- `--hf-token`: HuggingFace token (alias for --api-key)
- `-h, --help`: Show help message
- `-V, --version`: Show version information

### LLM Provider Examples

#### Ollama
```bash
# Default Ollama setup
java -jar mcp-client-cli.jar --model "ollama:llama3:8b" --config mcp.json

# Custom Ollama URL
java -jar mcp-client-cli.jar \
  --model "ollama:mixtral:8x7b" \
  --config mcp.json \
  --base-url "http://remote-server:11434"
```

#### Hugging Face Text Generation Inference (TGI)
```bash
# Start TGI server first
docker run --gpus all --shm-size 1g -p 8080:80 \
  ghcr.io/huggingface/text-generation-inference:3.3.4 \
  --model-id meta-llama/Llama-3.1-8B-Instruct

# Connect with MCPHost
java -jar mcp-client-cli.jar \
  --model "hf:meta-llama/Llama-3.1-8B-Instruct" \
  --config mcp.json \
  --api-key $HF_TOKEN
```

#### llama.cpp Server
```bash
# Start llama.cpp server (example with Qwen model)
llama-server -hf Qwen/Qwen3-32B-GGUF:Q5_K_M --jinja

# Connect with MCPHost
java -jar mcp-client-cli.jar \
  --model "llama-server:qwen3-32b" \
  --config mcp.json
```

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
2. **LlmApiClient**: Interface for LLM providers
   - **OllamaApiClientImpl**: Ollama API implementation
   - **HuggingFaceApiClient**: Hugging Face TGI implementation
   - **LlamaServerApiClient**: llama.cpp server implementation
3. **LlmApiClientFactory**: Factory for creating appropriate LLM clients
4. **McpConnectionManager**: Manages connections to multiple MCP servers
5. **ChatController**: Orchestrates the chat loop and tool execution
6. **SchemaConverter**: Converts between MCP and Ollama tool formats
7. **SystemPromptBuilder**: Builds system prompts with available tools/resources

### Flow

1. Load configuration from `mcp.json`
2. Create appropriate LLM client based on model specification
3. Initialize connections to all configured MCP servers
4. Fetch available tools, resources, and prompts from MCP servers
5. Convert MCP tools to LLM-compatible format
6. Start interactive chat session
7. Process user messages through LLM
8. Execute requested tools via MCP servers
9. Feed results back to LLM for response generation

## Development

### Project Structure

```
mcp-client-cli/
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
│   │   ├── OllamaApiClient.java    # Legacy Ollama client (deprecated)
│   │   └── llm/                    # LLM client implementations
│   │       ├── LlmApiClient.java   # LLM client interface
│   │       ├── LlmApiClientFactory.java # Client factory
│   │       ├── OllamaApiClientImpl.java # Ollama implementation
│   │       ├── HuggingFaceApiClient.java # HF TGI implementation
│   │       └── LlamaServerApiClient.java # llama.cpp implementation
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

This creates a native executable at `build/native/nativeCompile/mcp-client-cli`.

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

1. **LLM connection failed**: 
   - Ensure your LLM provider is running and accessible at the specified URL
   - Check that the base URL is correct for your provider
   - For Hugging Face, ensure your API key is valid

2. **MCP server failed to start**: Check the command and arguments in your `mcp.json`

3. **Tool execution timeout**: Increase `defaultTimeout` in global settings

4. **OpenAI API compatibility issues**: 
   - Ensure your Hugging Face TGI or llama.cpp server version supports OpenAI-compatible endpoints
   - For TGI, version 1.4.0+ is required for OpenAI compatibility

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
