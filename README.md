# Akka Agentic Doc Extractor

An agentic document extraction service built with Akka HTTP and Akka Java SDK Agent framework. It leverages OpenAI and Claude AI to extract structured data from PDFs and images, featuring automatic fallback mechanisms for robustness.

## Prerequisites

Before starting, ensure you have the following environment variables set:

```bash
export OPENAI_API_KEY="your-openai-key"
export ANTHROPIC_API_KEY="your-anthropic-key"
```

## Starting the Server

To start the server using Maven:

```bash
mvn exec:java
```

The server will start on `http://0.0.0.0:8080`.

## Testing the API

### 1. Health Check
Verify the server is running:

```bash
curl http://localhost:8080/health
```

### 2. Extract with OpenAI
Extract data using the OpenAI model (default endpoint):

```bash
curl -v -X POST http://localhost:8080/extract \
  -F "prompt=Extract key fields into JSON. Return ONLY JSON." \
  -F "files=@/path/to/your/document.pdf"
```

### 3. Extract with Claude
Extract data using the Claude model:

```bash
curl -v -X POST http://localhost:8080/extract-claude \
  -F "prompt=Extract key fields into JSON. Return ONLY JSON." \
  -F "files=@/path/to/your/document.pdf"
```

## Configuration

The application is configured via `src/main/resources/application.conf`. Key settings include:
- `http.port`: 8080 (default)
- `akka.http.server.request-timeout`: 20 minutes (to accommodate long AI processing times)

## Project Structure

- `src/main/java/com/example/extractor/`
    - `agent/`: Common AI agent layer and adapters
    - `openai/`: OpenAI-specific implementations
    - `claude/`: Claude-specific implementations
    - `routes/`: HTTP route definitions

## License

This project is licensed under the Apache 2.0 License - see the [LICENSE](LICENSE) file for details.
