# Curl Examples for Akka Agentic Doc Extractor API

This document provides comprehensive curl command examples for testing the API, including multi-file upload scenarios.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Basic Examples](#basic-examples)
- [Multi-File Examples](#multi-file-examples)
- [Advanced Examples](#advanced-examples)
- [Error Scenarios](#error-scenarios)
- [Scripts](#scripts)

## Prerequisites

1. **Server Running**: Ensure the API server is running on `http://localhost:8080` (or update `BASE_URL`)
2. **Test Files**: Prepare PDF or image files for testing
3. **jq (Optional)**: For pretty-printing JSON responses: `brew install jq` (macOS) or `apt-get install jq` (Linux)

## Basic Examples

### 1. Health Check

```bash
curl -X GET "http://localhost:8080/health"
```

**Expected Response:**
```
ok
```

### 2. OpenAI - Single File

```bash
curl -X POST "http://localhost:8080/extract" \
  -F "prompt=Extract key fields into JSON. Return ONLY JSON." \
  -F "files=@sample1.pdf" \
  -H "Accept: application/json"
```

### 3. Claude - Single File

```bash
curl -X POST "http://localhost:8080/extract-claude" \
  -F "prompt=Extract key fields into JSON. Return ONLY JSON." \
  -F "files=@sample1.pdf" \
  -H "Accept: application/json"
```

## Multi-File Examples

### 4. OpenAI - Two Files

```bash
curl -X POST "http://localhost:8080/extract" \
  -F "prompt=Extract key fields from all documents. Return a combined JSON with data from all files." \
  -F "files=@sample1.pdf" \
  -F "files=@sample2.pdf" \
  -H "Accept: application/json"
```

**Key Points:**
- Use multiple `-F "files=@..."` parameters, one per file
- All files are processed together in a single request
- The prompt applies to all files

### 5. OpenAI - Three Files

```bash
curl -X POST "http://localhost:8080/extract" \
  -F "prompt=Extract key fields from all three documents. Return a combined JSON array with data from each file." \
  -F "files=@sample1.pdf" \
  -F "files=@sample2.pdf" \
  -F "files=@sample3.pdf" \
  -H "Accept: application/json"
```

### 6. OpenAI - Four or More Files

```bash
curl -X POST "http://localhost:8080/extract" \
  -F "prompt=Extract key fields from all documents. Return a combined JSON." \
  -F "files=@file1.pdf" \
  -F "files=@file2.pdf" \
  -F "files=@file3.pdf" \
  -F "files=@file4.pdf" \
  -H "Accept: application/json"
```

**Note:** You can add as many files as needed by adding more `-F "files=@..."` parameters.

### 7. Claude - Multiple Files

```bash
curl -X POST "http://localhost:8080/extract-claude" \
  -F "prompt=Extract key fields from all documents. Return a combined JSON with data from all files." \
  -F "files=@sample1.pdf" \
  -F "files=@sample2.pdf" \
  -F "files=@sample3.pdf" \
  -H "Accept: application/json"
```

### 8. Mixed File Types (PDF + Images)

```bash
curl -X POST "http://localhost:8080/extract" \
  -F "prompt=Extract key information from all documents. Return combined JSON." \
  -F "files=@document1.pdf" \
  -F "files=@document2.png" \
  -F "files=@document3.jpg" \
  -H "Accept: application/json"
```

## Advanced Examples

### 9. Custom Prompt with Multiple Files

```bash
curl -X POST "http://localhost:8080/extract" \
  -F "prompt=Extract invoice details from all documents. For each invoice, extract: invoice_number, date, total, vendor. Return as a JSON array with one object per invoice." \
  -F "files=@invoice1.pdf" \
  -F "files=@invoice2.pdf" \
  -F "files=@invoice3.pdf" \
  -H "Accept: application/json"
```

### 10. Pretty Print JSON Response

```bash
curl -X POST "http://localhost:8080/extract" \
  -F "prompt=Extract key fields into JSON. Return ONLY JSON." \
  -F "files=@sample1.pdf" \
  -H "Accept: application/json" \
  -s | jq '.'
```

### 11. Save Response to File

```bash
curl -X POST "http://localhost:8080/extract" \
  -F "prompt=Extract key fields into JSON. Return ONLY JSON." \
  -F "files=@sample1.pdf" \
  -H "Accept: application/json" \
  -o response.json
```

### 12. Show Timing Information

```bash
curl -X POST "http://localhost:8080/extract" \
  -F "prompt=Extract key fields into JSON. Return ONLY JSON." \
  -F "files=@sample1.pdf" \
  -H "Accept: application/json" \
  -w "\n\nHTTP Status: %{http_code}\nTime: %{time_total}s\n" \
  -s | jq '.'
```

### 13. Verbose Output (Debug Headers)

```bash
curl -v -X POST "http://localhost:8080/extract" \
  -F "prompt=Extract key fields into JSON. Return ONLY JSON." \
  -F "files=@sample1.pdf" \
  -H "Accept: application/json"
```

### 14. Default Prompt (No Prompt Provided)

```bash
# OpenAI
curl -X POST "http://localhost:8080/extract" \
  -F "files=@sample1.pdf" \
  -H "Accept: application/json"

# Claude
curl -X POST "http://localhost:8080/extract-claude" \
  -F "files=@sample1.pdf" \
  -H "Accept: application/json"
```

**Note:** When no prompt is provided, the API uses the default: "Extract key fields into JSON. Return ONLY JSON."

## Error Scenarios

### 15. Error - No Files

```bash
curl -X POST "http://localhost:8080/extract" \
  -F "prompt=Extract key fields into JSON." \
  -H "Accept: application/json"
```

**Expected Response:**
```json
{
  "error": {
    "code": "INVALID_REQUEST",
    "message": "No files provided in request"
  }
}
```

### 16. Error - Invalid Endpoint

```bash
curl -X POST "http://localhost:8080/invalid-endpoint" \
  -F "prompt=Test" \
  -F "files=@sample1.pdf" \
  -H "Accept: application/json"
```

**Expected Response:** `404 Not Found`

### 17. Error - Wrong HTTP Method

```bash
curl -X GET "http://localhost:8080/extract" \
  -H "Accept: application/json"
```

**Expected Response:** `405 Method Not Allowed`

## Scripts

### Using the Test Script

We provide two shell scripts for easy testing:

#### 1. `test-api.sh` - Interactive Test Script

```bash
# Make executable
chmod +x test-api.sh

# Run specific scenario
./test-api.sh openai-multi

# Run all tests
./test-api.sh all

# Use custom files
FILE1=invoice.pdf FILE2=receipt.pdf ./test-api.sh openai-multi

# Use custom base URL
BASE_URL=http://localhost:9090 ./test-api.sh health
```

**Available scenarios:**
- `health` - Health check
- `openai-single` - OpenAI with single file
- `openai-multi` - OpenAI with 2 files
- `openai-three` - OpenAI with 3 files
- `openai-custom` - OpenAI with custom prompt
- `claude-single` - Claude with single file
- `claude-multi` - Claude with 2 files
- `claude-three` - Claude with 3 files
- `claude-custom` - Claude with custom prompt
- `error-no-files` - Error scenario
- `error-invalid` - Invalid endpoint
- `all` - Run all scenarios

#### 2. `curl-examples.sh` - Print All Examples

```bash
# Make executable
chmod +x curl-examples.sh

# Print all examples
./curl-examples.sh
```

## Environment Variables

You can set environment variables to customize the scripts:

```bash
# Set base URL
export BASE_URL="http://localhost:8080"

# Set test files
export FILE1="sample1.pdf"
export FILE2="sample2.pdf"
export FILE3="sample3.pdf"

# Run script
./test-api.sh openai-multi
```

## Tips and Best Practices

### 1. File Paths

- Use absolute paths if files are not in the current directory
- Use `@` prefix before file path: `-F "files=@/path/to/file.pdf"`
- File paths with spaces should be quoted: `-F "files=@/path/to/my file.pdf"`

### 2. Multiple Files

- Add multiple `-F "files=@..."` parameters for multiple files
- Files are processed in the order they appear
- All files are sent in a single HTTP request

### 3. Response Handling

- Use `-s` flag to suppress progress meter
- Use `jq` for pretty-printing JSON: `| jq '.'`
- Save responses to files: `-o response.json`
- Show timing: `-w "\nTime: %{time_total}s\n"`

### 4. Debugging

- Use `-v` flag for verbose output (shows headers)
- Use `-i` flag to include response headers
- Check HTTP status codes: `-w "\nHTTP Status: %{http_code}\n"`

### 5. Timeouts

For large files or slow responses, you may need to increase curl timeout:

```bash
curl --max-time 300 -X POST "http://localhost:8080/extract" \
  -F "prompt=Extract key fields into JSON. Return ONLY JSON." \
  -F "files=@large-file.pdf" \
  -H "Accept: application/json"
```

## Example Workflow

### 1. Check API Health

```bash
curl -X GET "http://localhost:8080/health"
```

### 2. Test Single File Extraction

```bash
curl -X POST "http://localhost:8080/extract" \
  -F "prompt=Extract key fields into JSON. Return ONLY JSON." \
  -F "files=@test.pdf" \
  -H "Accept: application/json" \
  -s | jq '.'
```

### 3. Test Multiple Files

```bash
curl -X POST "http://localhost:8080/extract" \
  -F "prompt=Extract key fields from all documents. Return combined JSON." \
  -F "files=@file1.pdf" \
  -F "files=@file2.pdf" \
  -F "files=@file3.pdf" \
  -H "Accept: application/json" \
  -s | jq '.'
```

### 4. Save Results

```bash
curl -X POST "http://localhost:8080/extract" \
  -F "prompt=Extract key fields into JSON. Return ONLY JSON." \
  -F "files=@test.pdf" \
  -H "Accept: application/json" \
  -o results.json \
  -w "\nHTTP Status: %{http_code}\nTime: %{time_total}s\n"
```

## Troubleshooting

### Connection Refused

```bash
# Check if server is running
curl -X GET "http://localhost:8080/health"

# If fails, start the server first
mvn exec:java
```

### File Not Found

```bash
# Use absolute paths
curl -X POST "http://localhost:8080/extract" \
  -F "prompt=Extract key fields into JSON. Return ONLY JSON." \
  -F "files=@/absolute/path/to/file.pdf" \
  -H "Accept: application/json"
```

### Timeout Issues

```bash
# Increase timeout (300 seconds = 5 minutes)
curl --max-time 300 -X POST "http://localhost:8080/extract" \
  -F "prompt=Extract key fields into JSON. Return ONLY JSON." \
  -F "files=@large-file.pdf" \
  -H "Accept: application/json"
```

### JSON Parsing Errors

```bash
# Check if response is valid JSON
curl -X POST "http://localhost:8080/extract" \
  -F "prompt=Extract key fields into JSON. Return ONLY JSON." \
  -F "files=@test.pdf" \
  -H "Accept: application/json" \
  -s | jq '.' 2>&1
```

## Quick Reference

| Scenario | Endpoint | Method | Files |
|----------|----------|--------|-------|
| Health Check | `/health` | GET | - |
| OpenAI Single | `/extract` | POST | 1 |
| OpenAI Multi | `/extract` | POST | 2+ |
| Claude Single | `/extract-claude` | POST | 1 |
| Claude Multi | `/extract-claude` | POST | 2+ |

## See Also

- [README.txt](README.txt) - Main project documentation
- [README_POSTMAN.md](README_POSTMAN.md) - Postman collection documentation
- [test-api.sh](test-api.sh) - Interactive test script
- [curl-examples.sh](curl-examples.sh) - All curl examples

