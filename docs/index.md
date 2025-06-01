# MCPHost-v5: LLM Tool Orchestration CLI

## Table of Contents
- [1. Introduction](#introduction)
- [2. Architecture Overview](#architecture)
  - [2.1. Core Components](#core-components)
  - [2.2. Data Flow](#data-flow)
- [3. Key Concepts](#key-concepts)
  - [3.1. Model Context Protocol (MCP)](#mcp)
  - [3.2. Ollama and Tool Calling](#ollama)
  - [3.3. Picocli](#picocli)
  - [3.4. Logback](#logback)
  - [3.5. GraalVM Native Image](#graalvm)
- [4. Project Structure](#project-structure)
- [5. Setup and Usage](#setup-and-usage)
  - [5.1. Prerequisites](#prerequisites)
  - [5.2. Building the Application](#building)
  - [5.3. Running MCPHost-v5](#running)
  - [5.4. `mcp.json` Configuration](#mcp-json-config)
- [6. Deep Dive into Components](#deep-dive-into-components)
  - [6.1. Main CLI Logic (`Main.java`)](#main-cli)
  - [6.2. MCP Configuration (`McpConfig.java`, `McpConfigLoader.java`)](#mcpconfigloader)
  - [6.3. MCP Tool Client Management (`McpToolClientManager.java`)](#mcptoolclientmanager)
  - [6.4. Ollama API Integration (`OllamaApi.java`, `OllamaApiClient.java`)](#ollama-api)
  - [6.5. Loading Animator (`LoadingAnimator.java`)](#loadinganimator)
  - [6.6. Schema Conversion (MCP to Ollama)](#schema-conversion)
- [7. Design Decisions and Error Handling](#design-decisions-and-error-handling)
  - [7.1. Jackson Deserialization Mismatch (`OllamaApi.FunctionCall.arguments`)](#jackson-deserialization-mismatch)
  - [7.2. `IllegalThreadStateException` in `LoadingAnimator`](#illegalthreadstateexception)
  - [7.3. Logging Strategy](#logging-strategy)
  - [7.4. `<think>` Tag Handling](#think-tag-handling)
- [8. Native Image Considerations](#native-image-considerations)
- [9. Future Enhancements](#future-enhancements)
- [10. Conclusion](#conclusion)

## Introduction

MCPHost-v5 is a command-line interface (CLI) tool built with Java 21, designed to act as an orchestrator between Large Language Models (LLMs) and external tools exposed via the Model Context Protocol (MCP). It enables LLMs, specifically those compatible with the Ollama API supporting tool/function calling, to leverage capabilities provided by various MCP-compliant servers.

The primary goal is to provide a standardized way for LLMs to interact with diverse external systems (e.g., file systems, calculators, APIs) by abstracting these systems as "tools" within the MCP framework. MCPHost-v5 dynamically discovers these tools from configured MCP servers and presents them to the LLM, facilitating a conversational interaction where the LLM can request tool execution and receive results to inform its responses.

This document provides an in-depth explanation of MCPHost-v5's architecture, components, design decisions, and usage for senior Java developers.

## Architecture

MCPHost-v5 follows a modular architecture, with clear separation of concerns for CLI parsing, MCP server management, LLM interaction, and configuration loading.

### Core Components

- **CLI Interface (`Main.java` using Picocli):** Parses command-line arguments (LLM model, MCP configuration path, Ollama URL) and orchestrates the main application flow.
- **MCP Configuration (`McpConfig.java`, `McpConfigLoader.java`):** Defines the structure and loads the `mcp.json` file, which specifies the MCP servers to connect to.
- **MCP Tool Client Manager (`McpToolClientManager.java`):** Manages connections to MCP servers defined in the configuration. It initializes MCP clients (using the [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk)), discovers available tools, and provides a unified interface to call these tools.
- **Ollama API Client (`OllamaApiClient.java`, `OllamaApi.java`):** Handles communication with the Ollama server. It sends chat requests (including user prompts and available tool definitions) and processes responses, which may include requests for tool execution. `OllamaApi.java` contains POJOs for (de)serializing Ollama API payloads.
- **Loading Animator (`LoadingAnimator.java`):** Provides visual feedback to the user during long-running operations like LLM inference or tool execution.
- **Logging (Logback):** Provides structured and configurable logging for debugging and operational insights.

### Data Flow

1. User launches MCPHost-v5 with CLI arguments.
2. `Main.java` parses arguments.
3. `McpConfigLoader` loads `mcp.json`.
4. `McpToolClientManager` initializes connections to MCP servers specified in the config and discovers all available tools.
5. `Main.java` converts the discovered MCP tool schemas into the format expected by the Ollama API.
6. User enters a prompt in the interactive CLI.
7. `Main.java` constructs an `OllamaApi.ChatRequest` containing the conversation history and the list of available (converted) tools.
8. `OllamaApiClient` sends the request to the Ollama server. A loading animation is displayed.
9. Ollama LLM processes the prompt and tool definitions. It may:
   - Respond directly with text.
   - Request one or more tool calls by including `tool_calls` in its response.
10. `OllamaApiClient` receives the response.
11. If the response contains tool calls:
    1. `Main.java` parses the tool name and arguments.
    2. `McpToolClientManager` is invoked to execute the specified MCP tool with the given arguments. A loading animation is displayed for tool execution.
    3. The tool's result is obtained.
    4. `Main.java` adds the tool result to the conversation history (as a message with role "tool").
    5. The process loops back to step 7, sending the updated conversation history (including the tool result) back to the LLM for it to generate a final response based on the tool's output.
12. If the response is a direct text answer (or after tool results are processed), `Main.java` displays the LLM's response to the user.
13. The loop continues until the user exits.
14. On exit, `McpToolClientManager` gracefully closes all MCP client connections.

## Key Concepts

### Model Context Protocol (MCP)

The [Model Context Protocol](https://modelcontextprotocol.org/) is a specification for standardizing how AI models interact with external tools and access contextual information. It defines a client-server architecture where:
- **MCP Servers:** Expose capabilities like tools (executable functions), resources (data sources), and prompts (pre-defined interaction templates). Examples include `@modelcontextprotocol/server-filesystem` for file operations or `@modelcontextprotocol/server-calculator`.
- **MCP Clients:** (MCPHost-v5 acts as a client to these servers) Connect to MCP servers, discover their capabilities, and invoke them.

Communication typically uses JSON-RPC over transports like STDIO or HTTP/SSE. MCPHost-v5 utilizes the [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk), specifically its STDIO client transport, to connect to MCP servers launched as separate processes.

**Key MCP SDK Components Used:**
- `io.modelcontextprotocol.client.McpClient`: Factory for creating MCP clients.
- `io.modelcontextprotocol.client.McpAsyncClient`: Used for non-blocking communication with MCP servers.
- `io.modelcontextprotocol.client.transport.StdioClientTransport`: For communicating with MCP servers over standard input/output.
- `io.modelcontextprotocol.client.transport.ServerParameters`: To define how to launch an MCP server process.
- `io.modelcontextprotocol.spec.McpSchema`: Contains POJOs for MCP request/response structures (e.g., `Tool`, `CallToolRequest`, `CallToolResult`).

### Ollama and Tool Calling

[Ollama](https://ollama.com/) is a platform for running open-source LLMs locally. Recent versions of Ollama support a tool calling (or function calling) mechanism similar to OpenAI's API. This allows the LLM to indicate that it wants to invoke an external function to fulfill a user's request.

The flow typically involves:
1. Sending the user prompt to the LLM along with a list of available tools (functions) and their schemas.
2. The LLM responding with a special message indicating which tool to call and with what arguments.
3. The application (MCPHost-v5) executing the tool.
4. Sending the tool's output back to the LLM.
5. The LLM using the tool's output to generate a final response to the user.

MCPHost-v5 bridges this by presenting MCP tools to Ollama in the format Ollama expects.

### Picocli

[Picocli](https://picocli.info/) is a modern Java library for creating command-line applications. It uses annotations to define CLI options, parameters, and subcommands, and automatically generates help messages. MCPHost-v5 uses Picocli to parse `-m` (model) and `--config` arguments.

### Logback

[Logback](https://logback.qos.ch/) is a logging framework, succeeding Log4j. It's used in MCPHost-v5 via the SLF4J (Simple Logging Facade for Java) API for flexible and configurable logging. This replaces direct `System.out.println` and `System.err.println` calls, allowing for different log levels, output formats, and destinations (e.g., console, file).

A key decision was to use two appenders: one for general debug/application logs and another (`CLI_OUTPUT`) for clean, user-facing interactive messages, controlled by distinct logger names (`com.example.mcphost.Main` vs `com.example.mcphost.Main.CLI`).

### GraalVM Native Image

[GraalVM Native Image](https://www.graalvm.org/native-image/) technology compiles Java code ahead-of-time (AOT) into a standalone executable. This results in significantly faster startup times and lower memory footprint compared to running on a JVM. MCPHost-v5 is configured to be buildable as a native image, making it suitable as a lightweight CLI tool. This requires careful consideration of reflection, resources, and dynamic class loading, often managed via build tool plugins and agent-collected metadata.

## Project Structure

```
java-mcphost-v5/
├── build.gradle                # Gradle build script
├── settings.gradle             # Gradle settings
├── gradlew                     # Gradle wrapper script
├── gradlew.bat                 # Gradle wrapper script (Windows)
├── mcp.json                    # Example MCP server configuration (user-provided)
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/example/mcphost/
    │   │       ├── Main.java                   # CLI entry point, main logic
    │   │       ├── McpConfig.java              # POJO for mcp.json
    │   │       ├── McpConfigLoader.java        # Loads mcp.json
    │   │       ├── McpToolClientManager.java   # Manages MCP server clients and tools
    │   │       ├── OllamaApi.java              # POJOs for Ollama API
    │   │       ├── OllamaApiClient.java        # Client for Ollama API
    │   │       └── LoadingAnimator.java        # Console loading animation
    │   └── resources/
    │       ├── logback.xml                 # Logback configuration
    │       └── META-INF/native-image/    # GraalVM native-image configuration (often agent-generated)
    └── test/
        └── java/
            └── ... (Unit tests)
```

## Setup and Usage

### Prerequisites

- Java Development Kit (JDK) 21 or later.
- Gradle (the project uses the Gradle wrapper, so it will be downloaded automatically).
- An Ollama server running and accessible (default: `http://localhost:11434`). Ensure the desired LLM model is pulled (e.g., `ollama pull qwen2:7b`).
- Any MCP servers you intend to use must be runnable (e.g., if using `@modelcontextprotocol/server-filesystem`, Node.js and npx are required).
- (Optional for native image) GraalVM installed and configured if you want to build the native executable locally outside of Gradle's managed environment.

### Building the Application

#### Building a Fat JAR

To build an executable JAR that includes all dependencies:

```
./gradlew jar
```

The JAR will be located in `build/libs/java-mcphost-v5-<version>.jar`.

#### Building a Native Executable

To build a GraalVM native image:

```
./gradlew nativeCompile
```

The executable will be located in `build/native/nativeCompile/mcphost`.

> **Note on Native Image:** Building native images can be resource-intensive and may require specific configurations for reflection, resources, etc., if the GraalVM agent doesn't capture everything. The current `build.gradle` sets up basic agent usage.

### Running MCPHost-v5

#### Using the Fat JAR:

```
java -jar build/libs/java-mcphost-v5-<version>.jar -m <ollama_model_name> --config <path_to_mcp.json>
```

Example:

```
java -jar build/libs/java-mcphost-v5-0.1.0-SNAPSHOT.jar -m qwen2:7b --config mcp.json
```

#### Using the Native Executable:

```
./build/native/nativeCompile/mcphost -m <ollama_model_name> --config <path_to_mcp.json>
```

Example:

```
./build/native/nativeCompile/mcphost -m qwen2:7b --config mcp.json
```

**CLI Options:**

- `-m, --model <llmModelFullName>` (Required): Specifies the Ollama model to use (e.g., `qwen2:7b`, `llama3:8b`). The `ollama:` prefix is optional.
- `--config <mcpConfigFile>` (Required): Path to the `mcp.json` file defining MCP servers.
- `--ollama-base-url <ollamaBaseUrl>` (Optional): Base URL for the Ollama API. Defaults to `http://localhost:11434`.

### `mcp.json` Configuration

This JSON file defines the MCP servers that MCPHost-v5 will connect to. Each entry specifies how to launch an MCP server process.

**Example `mcp.json`:**

```json
{
  "mcpServers": {
    "filesystemServer": {
      "command": "npx",
      "args": [
        "-y",
        "@modelcontextprotocol/server-filesystem",
        "/path/to/accessible/directory"
      ],
      "env": {}
    },
    "calculatorServer": {
        "command": "npx",
        "args": [
            "-y",
            "@modelcontextprotocol/server-calculator"
        ]
    }
  }
}
```

- `mcpServers`: A map where each key is a unique name for the server configuration (e.g., "filesystemServer").
- `command`: The executable command to run the MCP server (e.g., `npx`, `java`, path to a binary).
- `args`: A list of arguments to pass to the command.
- `env` (Optional): A map of environment variables to set for the MCP server process.

> **Note:** Ensure the commands and paths in `mcp.json` are correct for your environment and that the specified MCP servers are installed or accessible. For `@modelcontextprotocol/server-filesystem`, the last argument in `args` is the root directory it will serve.

## Deep Dive into Components

### Main CLI Logic (`Main.java`)

`Main.java` is the application's entry point and orchestrator.
- **CLI Parsing:** Uses Picocli annotations (`@Command`, `@Option`) to define and parse command-line arguments.
- **Initialization:**
  - Loads MCP configuration using `McpConfigLoader`.
  - Initializes `McpToolClientManager`, which connects to MCP servers and discovers tools.
  - Initializes `OllamaApiClient`.
  - Converts discovered MCP tool schemas to the Ollama tool format.
- **Interactive Loop:**
  - Reads user input from the console.
  - Manages conversation history.
  - Constructs requests for the Ollama API, including available tools.
  - Handles responses from Ollama:
    - If a text response, displays it.
    - If a tool call is requested, it extracts tool name and arguments.
    - Invokes `McpToolClientManager.callTool()`.
    - Sends tool results back to Ollama for final processing.
  - Uses `LoadingAnimator` to provide feedback during waits.
- **Logging:** Employs SLF4J with Logback, using a general logger for internal/debug messages and a `cliLogger` for user-facing output to keep the console clean.
- **Shutdown Hook:** Ensures graceful cleanup of resources (e.g., closing MCP client connections) on application exit.

### MCP Configuration (`McpConfig.java`, `McpConfigLoader.java`)

`McpConfig.java` is a POJO (Plain Old Java Object) that mirrors the structure of the `mcp.json` file, using Jackson annotations (`@JsonProperty`) for mapping.
`McpConfigLoader.java` uses Jackson's `ObjectMapper` to parse the JSON file into an `McpConfig` instance. It includes basic file existence checks and logs errors.

### MCP Tool Client Management (`McpToolClientManager.java`)

This class is crucial for interacting with MCP servers.
- **Client Initialization:** For each server entry in `McpConfig`:
  - It constructs `ServerParameters` from the command and arguments.
  - Creates an `StdioClientTransport`.
  - Builds an `McpAsyncClient` using `McpClient.async()`. Configuration includes request/initialization timeouts and client identification.
  - Calls `client.initialize().block(...)` to synchronously establish and initialize the connection. This blocking call is acceptable here as it's part of the application's setup phase.
  - If initialization is successful, it lists tools from the server (`client.listTools().block(...)`) and stores a mapping from tool name to the server name that provides it. This handles potential tool name collisions by logging a warning and overwriting (last one wins).
- **Tool Aggregation (`getAllTools()`):** Collects `McpSchema.Tool` definitions from all successfully initialized clients, ensuring only tools that were successfully mapped are returned.
- **Tool Invocation (`callTool()`):**
  - Looks up the server responsible for the requested tool name.
  - Retrieves the corresponding `McpAsyncClient`.
  - Creates an `McpSchema.CallToolRequest`.
  - Invokes `client.callTool(request).block(...)` to synchronously execute the tool and get its result. Blocking is used here to simplify the flow within the main chat loop.
  - Handles exceptions during tool calls and returns an error-formatted `CallToolResult`.
- **Client Shutdown (`closeAllClients()`):** Iterates through all active MCP clients and calls `client.closeGracefully().block(...)` to ensure proper termination of MCP server processes.

### Ollama API Integration (`OllamaApi.java`, `OllamaApiClient.java`)

`OllamaApi.java` contains POJOs representing the JSON structures for Ollama's `/api/chat` endpoint, including requests (`ChatRequest`, `Message`, `Tool`, `FunctionCall`, `JsonSchema`) and responses (`ChatResponse`). These are annotated for Jackson (de)serialization.

> **Design Decision (`OllamaApi.FunctionCall.arguments`):** Initially, `FunctionCall.arguments` was typed as `String`, assuming Ollama would send a JSON string. However, Ollama (like OpenAI) sends arguments as a direct JSON *object*. This caused a `MismatchedInputException`. The fix was to change the type to `Map<String, Object>`, allowing Jackson to deserialize the arguments object directly into a map. See [Section 7.1](#jackson-deserialization-mismatch).

`OllamaApiClient.java`:
- Uses Java's built-in `HttpClient` for HTTP communication.
- Configures an `ObjectMapper` with `PropertyNamingStrategies.SNAKE_CASE` as Ollama's API uses snake_case for JSON fields.
- The `chat()` method:
  - Serializes the `OllamaApi.ChatRequest` to a JSON string.
  - Sends a POST request to the Ollama `/api/chat` endpoint.
  - Deserializes the JSON response into an `OllamaApi.ChatResponse`.
  - Includes debug logging for request/response bodies and status codes.
  - Handles non-2xx HTTP responses by throwing a `RuntimeException`.

### Loading Animator (`LoadingAnimator.java`)

Provides simple CLI visual feedback.
- Uses a separate daemon thread to update animation characters (`| / - \`) without blocking the main thread.
- Takes a `PrintWriter` for direct console manipulation, essential for using carriage return (`\r`) to overwrite the animation on the same line. Standard `System.out` can be line-buffered, interfering with this.
- `start(String message)`: Initializes/updates the message and starts a *new* thread for the animation if one isn't already active or if the previous one terminated.
- `stop()`: Sets a flag to stop the animation loop, interrupts the thread to wake it from sleep, and attempts to join it. It then clears the animation line.

> **Design Decision (`LoadingAnimator` Thread Lifecycle):** An earlier version reused the same `Thread` object. This led to an `IllegalThreadStateException` when `start()` was called after the thread had terminated (because a `Thread` cannot be restarted). The fix was to create a new `Thread` instance within `start()` each time the animation needs to begin and the previous one is not active or has finished. Methods `start()` and `stop()` are synchronized to manage state changes safely. See [Section 7.2](#illegalthreadstateexception).

### Schema Conversion (MCP to Ollama)

Located in `Main.java` (`convertMcpToolsToOllamaTools` and `convertMcpSchemaRecursive`).
Ollama's tool definition requires a specific JSON Schema format for function parameters. MCP tools also define their input schemas using JSON Schema (via `McpSchema.JsonSchema`).
The conversion process:
1. Iterates through discovered `McpSchema.Tool` objects.
2. For each MCP tool, it creates an `OllamaApi.Tool`.
3. The core challenge is converting `McpSchema.Tool.inputSchema` (an `McpSchema.JsonSchema` object) into an `OllamaApi.JsonSchema` object for the `parameters` field of an `OllamaApi.OllamaFunction`.
4. `convertMcpSchemaRecursive` attempts this conversion. Since `McpSchema.JsonSchema` is a simplified record and its `properties` field is `Map<String, Object>`, the conversion involves using Jackson's `objectMapper.convertValue()` to interpret nested schema objects.
5. It handles basic types (object, array, string, etc.) and recursively converts properties for objects and item schemas for arrays.

> **Schema Complexity:** JSON Schema can be complex. The current conversion is a best-effort approach based on the provided `McpSchema.JsonSchema` structure. It might need refinement if MCP servers expose highly intricate or less common JSON Schema features (e.g., `oneOf`, `allOf`, complex `$ref` usage not directly supported by the simple `McpSchema.JsonSchema` record). The MCP SDK's `Tool(String name, String description, String schema)` constructor suggests that the SDK itself parses a full JSON schema string, implying that `McpSchema.JsonSchema` might be a simplified view. Our conversion tries to bridge this by re-interpreting `Map<String, Object>` parts as nested schemas.

## Design Decisions and Error Handling

### Jackson Deserialization Mismatch (`OllamaApi.FunctionCall.arguments`)

**Problem:** An initial `MismatchedInputException` occurred because `OllamaApi.FunctionCall.arguments` was defined as `String`, but the Ollama API returns tool arguments as a JSON *object*.

**Log Snippet:**
```
com.fasterxml.jackson.databind.exc.MismatchedInputException: Cannot deserialize value of type `java.lang.String` from Object value (token `JsonToken.START_OBJECT`)
... (through reference chain: ...OllamaApi$FunctionCall["arguments"])
```

**Decision & Fix:** The type of `OllamaApi.FunctionCall.arguments` was changed from `String` to `Map<String, Object>`. This allows Jackson to directly deserialize the JSON object into a Java Map. Consequently, the code in `Main.java` that previously parsed this string into a map was simplified to use the map directly.

### `IllegalThreadStateException` in `LoadingAnimator`

**Problem:** The `LoadingAnimator` threw an `IllegalThreadStateException` when its `start()` method was called multiple times.

**Log Snippet:**
```
java.lang.IllegalThreadStateException
    at java.base/java.lang.Thread.start(Thread.java:1525)
    at com.example.mcphost.LoadingAnimator.start(LoadingAnimator.java:23)
```

**Decision & Fix:** A Java `Thread` object can only be started once. After its `run()` method completes, the thread terminates and cannot be restarted. The fix involved modifying `LoadingAnimator.start()` to create a *new* `Thread` instance each time the animation needs to begin, provided one isn't already running or the previous one has finished. The `start()` and `stop()` methods were also synchronized, and `thread.interrupt()` was added in `stop()` to ensure timely termination of the animator thread.

### Logging Strategy

**Requirement:** Replace all `System.out/err` calls with a robust logging framework for better control over output, levels, and formatting.

**Decision:** SLF4J with Logback was chosen.
- A general application logger (`com.example.mcphost.Main`) is used for internal status, debug information, and errors, configured with a detailed pattern in `logback.xml`.
- A specific "CLI" logger (`com.example.mcphost.Main.CLI`) is used for direct user interaction elements (e.g., "You:", "LLM:", tool results). This logger is configured with a minimal pattern (just the message) to keep the interactive console clean.
- Other classes (`McpToolClientManager`, `OllamaApiClient`, etc.) use their own SLF4J loggers.

This separation allows fine-grained control over what the user sees versus what's logged for debugging or auditing.

### `<think>` Tag Handling

**Requirement:** If the LLM or user input contains `<think>...</think>` tags, display this "thought" process during loading animations.

**Decision:**
- A regex (`THINK_TAG_PATTERN`) was introduced to extract content from these tags.
- In `Main.java`, before calling the Ollama API or an MCP tool, the relevant input (user prompt or previous LLM output) is checked for these tags.
- If found, the extracted thought content is passed to `LoadingAnimator.start(message)` to be displayed as part of the loading message.
- The `<think>` tags are stripped from the LLM's final displayed output to keep the chat clean, but the original message (with tags) is added to the conversation history for the LLM's context.

This provides a conventional way to surface the LLM's "reasoning" steps if the model is prompted or designed to output them using this tag convention.

## Native Image Considerations

Building MCPHost-v5 as a GraalVM native image offers significant performance benefits (startup, memory) but requires attention to Java features that are challenging for AOT compilation, such as reflection, dynamic class loading, and resources.

- **Reflection:** Jackson (for JSON) and Picocli heavily use reflection. The `build.gradle` configures the GraalVM build tools plugin with an agent (`defaultMode = "conditional"`) that can run during tests (`inputTaskNames.add("test")`) to automatically detect and generate reflection configuration. However, manual configuration in `reflect-config.json` (placed in `src/main/resources/META-INF/native-image/<groupid>/<artifactid>/`) might still be needed for POJOs or other classes not covered by the agent.
- **Resources:** `logback.xml` needs to be included in the native image. The default Gradle resource processing usually handles this, but explicit resource configuration might be needed if issues arise.
- **Dynamic Class Loading:** Some libraries might dynamically load classes. The agent helps, but if classes are loaded in very dynamic ways (e.g., from strings), they might need to be explicitly registered.
- **MCP SDK and HTTP Client:** The MCP SDK's `StdioClientTransport` launches external processes, which is generally fine for native images. The Java `HttpClient` used by `OllamaApiClient` is also compatible with native image compilation, but any underlying SSL/TLS components must be correctly configured for native compilation (GraalVM usually handles this for standard JDK providers).
- **Picocli Native Image Support:** The `-Apicocli.nativeImage=true` compiler argument helps Picocli generate code that is more friendly to native image compilation, reducing the need for extensive reflection configuration for Picocli itself.

Thorough testing of the native executable is crucial to catch any runtime issues related to missing native image configurations.

## Future Enhancements

- **Streaming Support for Ollama:** Implement handling for Ollama's streaming responses (`"stream": true`) for more interactive LLM output.
- **Advanced Error Recovery:** More sophisticated error handling and retry mechanisms for Ollama API calls and MCP tool executions.
- **Configuration for Ollama Options:** Allow passing Ollama model options (temperature, top_p, etc.) via CLI or config file.
- **Support for Other LLM Backends:** Abstract the LLM client interface to support other backends beyond Ollama (e.g., OpenAI, Anthropic).
- **More Robust Schema Conversion:** Enhance the MCP-to-Ollama JSON Schema conversion to handle a wider array of schema features.
- **Asynchronous Tool Execution:** While MCP clients are async, the main loop currently blocks on tool calls. For scenarios with many long-running tools, a fully async pipeline could be beneficial.
- **Security Considerations:** If MCP servers are untrusted, sandboxing or stricter validation of tool inputs/outputs would be necessary.
- **Dynamic MCP Server Addition/Removal:** Allow managing MCP server connections at runtime without restarting MCPHost-v5.
- **Stateful Conversations with MCP Servers:** The current MCP interaction is stateless per tool call. Some MCP servers might support session-based interactions.

## Conclusion

MCPHost-v5 serves as a robust and extensible CLI tool for bridging the gap between modern LLMs (via Ollama) and the standardized tool ecosystem provided by the Model Context Protocol. By leveraging the MCP Java SDK, Picocli, and careful architectural design, it provides a practical solution for enhancing LLM capabilities with external functionalities. The use of GraalVM native image makes it a performant and convenient tool for developers working with LLMs and MCP.

The design decisions, particularly around error handling and component interactions, were informed by iterative development and debugging, aiming for a balance of functionality, robustness, and user experience.