# MCPHost: LLM Tool Orchestration CLI

MCPHost is a command-line interface (CLI) tool built with Java 21. It acts as an orchestrator between Large Language Models (LLMs) accessed via the Ollama API and external tools exposed via the Model Context Protocol (MCP). This enables LLMs to leverage capabilities provided by various MCP-compliant servers, such as file system operations, calculations, or interactions with other APIs.

The project is designed to be lightweight and performant, with support for GraalVM Native Image compilation for fast startup and reduced memory footprint.

### How to use

```bash
java -jar ./build/libs/java-mcphost-0.1.0-SNAPSHOT.jar -m qwen3:8b --config /Users/bruno/Developer/workspaces/claude/java-mcphost/mcp.json 
```


## Table of Contents




- [Features](#features)
- [Architecture](#architecture)
- [Key Concepts](#key-concepts)
- [Prerequisites](#prerequisites)
- [Building the Application](#building-the-application)
    - [Fat JAR](#fat-jar)
    - [Native Executable](#native-executable)
- [Running MCPHost](#running-mcphost)
    - [CLI Options](#cli-options)
- [Configuration (`mcp.json`)](#configuration-mcpjson)
- [Project Structure](#project-structure)
- [Development Notes](#development-notes)
    - [Logging](#logging)
    - [Schema Conversion](#schema-conversion)
- [Future Enhancements](#future-enhancements)

## Features

*   **LLM Integration:** Connects to LLMs via the Ollama API.
*   **MCP Tool Integration:** Dynamically discovers and utilizes tools from MCP servers.
*   **Tool Calling Orchestration:** Manages the multi-turn conversation flow when an LLM decides to use a tool.
*   **STDIO Transport for MCP:** Communicates with MCP servers launched as separate processes using standard input/output.
*   **Interactive CLI:** Provides a chat interface for users to interact with the LLM.
*   **Configurable:** MCP servers are defined in an external `mcp.json` file.
*   **Loading Animation:** Visual feedback during long-running operations.
*   **Structured Logging:** Uses Logback for configurable application logging.
*   **Native Image Support:** Can be compiled into a fast-starting native executable using GraalVM.
*   **`<think>` Tag Handling:** Displays "thought processes" from the LLM or user during loading animations if enclosed in `<think>...</think>` tags.

## Architecture

MCPHost comprises several key components:

1.  **Main CLI (<code>Main.java</code>):**
    *   Parses command-line arguments using Picocli.
    *   Orchestrates the overall application flow, including initialization and the interactive chat loop.
2.  **MCP Configuration (<code>McpConfig.java</code>, <code>McpConfigLoader.java</code>):**
    *   Loads and parses the `mcp.json` file which defines the MCP servers to connect to.
3.  **MCP Tool Client Manager (<code>McpToolClientManager.java</code>):**
    *   Manages connections to MCP servers using the [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk).
    *   Initializes `McpAsyncClient` instances for each configured server.
    *   Discovers tools exposed by these servers.
    *   Provides a unified interface to execute discovered tools.
4.  **Ollama API Client (<code>OllamaApiClient.java</code>, <code>OllamaApi.java</code>):**
    *   Communicates with a running Ollama instance.
    *   Sends chat requests, including the list of available tools (converted from MCP format).
    *   Processes Ollama responses, handling both direct text replies and tool call requests.
5.  **Loading Animator (<code>LoadingAnimator.java</code>):**
    *   Provides a simple text-based loading animation in the console during waits.

The general data flow involves the user providing a prompt, MCPHost sending it to Ollama with available tool definitions, Ollama potentially requesting a tool call, MCPHost executing the tool via the appropriate MCP server, and then sending the tool's result back to Ollama for a final response.

*(For detailed diagrams, please refer to the full HTML documentation or generate them from the provided PlantUML sources.)*

## Key Concepts

*   **Model Context Protocol (MCP):** A standard for AI models to interact with external tools and context. MCPHost acts as an MCP client to various MCP servers. See [modelcontextprotocol.org](https://modelcontextprotocol.org/).
*   **Ollama:** A platform for running LLMs locally. MCPHost uses Ollama's API, particularly its tool/function calling capabilities. See [ollama.com](https://ollama.com/).
*   **Tool Calling:** A mechanism where an LLM can request the execution of an external function (a "tool") to gather information or perform an action.
*   **Picocli:** A Java library for creating command-line applications with ease.
*   **Logback:** A logging framework used for structured application logging.
*   **GraalVM Native Image:** Technology to compile Java applications into standalone native executables.

## Prerequisites

*   Java Development Kit (JDK) 21 or later.
*   Gradle (uses wrapper, will download automatically).
*   A running Ollama instance (default: `http://localhost:11434`) with the desired model(s) pulled (e.g., `ollama pull qwen2:7b`).
*   MCP servers (defined in `mcp.json`) must be executable. For example, if using Node.js based MCP servers like `@modelcontextprotocol/server-filesystem`, ensure Node.js and `npx` are installed.

## Building the Application

Clone the repository and navigate to the project root directory.

### Fat JAR

To build an executable JAR file that includes all dependencies:
```bash
./gradlew jar
