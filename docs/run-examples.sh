#!/bin/bash

# Example script to demonstrate MCP Client CLI usage with different LLM providers

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}MCP Client CLI - LLM Provider Examples${NC}"
echo "========================================"
echo ""

# Check if mcp.json exists
if [ ! -f "mcp.json" ]; then
    echo -e "${YELLOW}Warning: mcp.json not found in current directory${NC}"
    echo "Please create an mcp.json configuration file first."
    exit 1
fi

# Example 1: Ollama
echo -e "${GREEN}Example 1: Using Ollama${NC}"
echo "Command:"
echo "  java -jar build/libs/mcp-client-cli-0.1.0-SNAPSHOT.jar \\"
echo "    --model \"ollama:qwen2.5-coder:32b\" \\"
echo "    --config mcp.json"
echo ""

# Example 2: Hugging Face TGI
echo -e "${GREEN}Example 2: Using Hugging Face TGI${NC}"
echo "First, start TGI server:"
echo "  docker run --gpus all --shm-size 1g -p 8080:80 \\"
echo "    ghcr.io/huggingface/text-generation-inference:3.3.4 \\"
echo "    --model-id meta-llama/Llama-3.1-8B-Instruct"
echo ""
echo "Then run MCP client:"
echo "  java -jar build/libs/mcp-client-cli-0.1.0-SNAPSHOT.jar \\"
echo "    --model \"hf:meta-llama/Llama-3.1-8B-Instruct\" \\"
echo "    --config mcp.json \\"
echo "    --api-key \$HF_TOKEN"
echo ""

# Example 3: llama.cpp server
echo -e "${GREEN}Example 3: Using llama.cpp server${NC}"
echo "First, start llama.cpp server:"
echo "  llama-server -hf Qwen/Qwen3-32B-GGUF:Q5_K_M --jinja"
echo ""
echo "Then run MCP client:"
echo "  java -jar build/libs/mcp-client-cli-0.1.0-SNAPSHOT.jar \\"
echo "    --model \"llama-server:qwen3-32b\" \\"
echo "    --config mcp.json"
echo ""

# Example 4: Custom base URLs
echo -e "${GREEN}Example 4: Using custom base URLs${NC}"
echo "For remote Ollama server:"
echo "  java -jar build/libs/mcp-client-cli-0.1.0-SNAPSHOT.jar \\"
echo "    --model \"ollama:llama3:8b\" \\"
echo "    --config mcp.json \\"
echo "    --base-url \"http://remote-server:11434\""
echo ""

echo -e "${BLUE}Note:${NC} Replace model names and URLs with your actual setup."
echo ""

# Check if user wants to run an example
echo -e "${YELLOW}Which example would you like to run? (1-4, or 'q' to quit):${NC}"
read -r choice

case $choice in
    1)
        echo "Running Ollama example..."
        java -jar build/libs/mcp-client-cli-0.1.0-SNAPSHOT.jar \
            --model "ollama:qwen2.5-coder:32b" \
            --config mcp.json
        ;;
    2)
        echo "Please ensure TGI server is running first."
        echo "Enter your HuggingFace token (or press Enter to skip):"
        read -r hf_token
        if [ -n "$hf_token" ]; then
            java -jar build/libs/mcp-client-cli-0.1.0-SNAPSHOT.jar \
                --model "hf:meta-llama/Llama-3.1-8B-Instruct" \
                --config mcp.json \
                --api-key "$hf_token"
        else
            echo "Skipping - HF token required for authenticated access"
        fi
        ;;
    3)
        echo "Please ensure llama.cpp server is running first."
        java -jar build/libs/mcp-client-cli-0.1.0-SNAPSHOT.jar \
            --model "llama-server:qwen3-32b" \
            --config mcp.json
        ;;
    4)
        echo "Enter the base URL for your LLM server:"
        read -r base_url
        echo "Enter the model specification (e.g., ollama:model-name):"
        read -r model_spec
        java -jar build/libs/mcp-client-cli-0.1.0-SNAPSHOT.jar \
            --model "$model_spec" \
            --config mcp.json \
            --base-url "$base_url"
        ;;
    q|Q)
        echo "Exiting..."
        ;;
    *)
        echo "Invalid choice. Exiting..."
        ;;
esac
