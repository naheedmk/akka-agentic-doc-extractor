# Akka Agentic Doc Extractor

An agentic document extraction service built with Akka HTTP and Akka Java SDK Agent framework that uses OpenAI and Claude AI to extract structured data from PDFs and images. Features automatic fallback from SDK Agent to direct invocation for full multi-file support.

## Architecture

The service uses a common AI agent layer that provides a unified interface for interacting with different AI providers:

- **AIAgent**: Base class that orchestrates file uploads and message extraction
- **OpenAIAgent**: OpenAI-specific implementation
- **ClaudeAgent**: Claude-specific implementation

## Endpoints

### Health Check
```bash
curl -X GET http://localhost:8080/health
```

### Extract with OpenAI
```bash
curl -v -X POST http://localhost:8080/extract \
  -F "prompt=Extract key fields into JSON. Return ONLY JSON." \
  -F "files=@/Users/kunnummaln/Downloads/thanksgivingtix.pdf"
```

### Extract with Claude
```bash
curl -v -X POST http://localhost:8080/extract-claude \
  -F "prompt=Extract key fields into JSON. Return ONLY JSON." \
  -F "files=@/path/to/document.pdf"
```

## Configuration

### Environment Variables

Required:
- `OPENAI_API_KEY`: Your OpenAI API key
- `ANTHROPIC_API_KEY`: Your Anthropic (Claude) API key

### application.conf

Key settings:
- `http.host`: Server host (default: 0.0.0.0)
- `http.port`: Server port (default: 8080)
- `openai.baseUrl`: OpenAI API base URL
- `openai.model`: OpenAI model to use
- `claude.baseUrl`: Claude API base URL
- `claude.model`: Claude model to use

### Timeout Configuration

The server is configured with extended timeouts to accommodate long-running AI operations:
- `akka.http.server.request-timeout = 20m` - Maximum time for request processing
- `akka.http.server.idle-timeout = 20m` - Maximum idle connection time

These timeouts are necessary because:
- OpenAI operations can take up to 15 minutes
- Claude operations can take up to 4 minutes
- File uploads and processing add additional time

## Building and Running

### Build
```bash
mvn clean compile 
```

### Run
```bash
mvn exec:java
```

### Test
```bash
mvn clean test
```

## Project Structure

```
src/main/java/com/example/extractor/
├── agent/                    # Common AI agent layer
│   ├── AIAgent.java         # Base agent class
│   ├── FileUploader.java    # File upload interface
│   ├── MessageClient.java   # Message client interface
│   ├── openai/              # OpenAI agent implementation
│   └── claude/              # Claude agent implementation
├── routes/                   # HTTP routes
│   └── ExtractRoutes.java  # Main route definitions
├── openai/                   # OpenAI clients
│   ├── OpenAIFileUploader.java
│   └── OpenAIResponsesClient.java
├── claude/                   # Claude clients
│   ├── ClaudeFileUploader.java
│   └── ClaudeMessagesClient.java
└── util/                     # Utilities
    └── MultipartUtil.java   # Multipart form parsing
```

## Error Handling

The service includes comprehensive error handling:
- Full exception chain extraction for detailed error messages
- Structured JSON error responses with error codes
- Proper HTTP status codes (200 for success, 500 for errors)
- Logging of all errors with full context

## Testing

### Unit Tests
- Health endpoint test
- Route structure validation
- Mock-based testing with AIAgent interfaces

### Postman Collection
See `postman_collection.json` and `README_POSTMAN.md` for:
- Complete API test suite
- Sample requests and responses
- Automated test scripts

## Troubleshooting

### 503 Timeout Errors
If you see "The server was not able to produce a timely response":
- Ensure server timeouts are set to at least 20 minutes
- Check that AI API keys are valid
- Verify network connectivity to AI services
- Review server logs for detailed error messages

### SSL Certificate Issues
If you encounter SSL errors during Maven builds:
- Ensure `maven-truststore.p12` exists at `$HOME/<folder>
- Use the SSL truststore flags in all Maven commands
- Check that the truststore password is correct

### Empty Responses
If you get empty responses:
- Verify API keys are set in environment variables
- Check that files are being uploaded successfully
- Review AI service logs for processing errors
- Ensure prompts are clear and specific

## Quick Start

1. Set environment variables:
   ```bash
   export OPENAI_API_KEY="your-openai-key"
   export ANTHROPIC_API_KEY="your-anthropic-key"
   ```

2. Start the server:
   ```bash
   mvn exec:java
   ```

3. Test the health endpoint:
   ```bash
   curl http://localhost:8080/health
   ```

4. Extract data from a document:
   ```bash
   curl -X POST http://localhost:8080/extract \
     -F "prompt=Extract invoice number, vendor, date, total. Return ONLY JSON." \
     -F "files=@/path/to/document.pdf"
   ```

## License

[Add your license information here]
