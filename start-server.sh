#!/bin/bash

# Start script for Akka Agentic Doc Extractor API
# This script starts the server with proper configuration

set -e

echo "=========================================="
echo "Starting Akka Agentic Doc Extractor Server"
echo "=========================================="
echo ""

# Check if Java is available
if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed or not in PATH"
    exit 1
fi

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed or not in PATH"
    exit 1
fi

# Set environment variables if not already set
export OPENAI_API_KEY="${OPENAI_API_KEY:-}"
export ANTHROPIC_API_KEY="${ANTHROPIC_API_KEY:-}"

if [ -z "$OPENAI_API_KEY" ] && [ -z "$ANTHROPIC_API_KEY" ]; then
    echo "Warning: Neither OPENAI_API_KEY nor ANTHROPIC_API_KEY is set"
    echo "The server will start but extraction endpoints may not work"
    echo ""
fi

echo "Configuration:"
echo "  Base URL: http://localhost:8080"

echo "  OpenAI API Key: ${OPENAI_API_KEY:+Set} ${OPENAI_API_KEY:-Not set}"
echo "  Anthropic API Key: ${ANTHROPIC_API_KEY:+Set} ${ANTHROPIC_API_KEY:-Not set}"
echo ""

echo "Starting server..."
echo "Press Ctrl+C to stop"
echo ""

# Start the server
mvn exec:java 
