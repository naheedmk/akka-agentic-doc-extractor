# Postman Collection - Akka Agentic Doc Extractor API

This document describes the comprehensive Postman collection for testing the Akka-based document extraction API.

## Collection Overview

The collection includes **all scenarios** for testing the API, organized into folders:

1. **Health Check** - Basic health verification
2. **OpenAI Extraction** - All OpenAI extraction scenarios
3. **Claude Extraction** - All Claude extraction scenarios
4. **Error Scenarios** - Common error cases and edge cases

## Setup

### 1. Import Collection and Environment

1. Import `postman_collection.json` into Postman
2. Import `postman_environment.json` into Postman
3. Select the environment in Postman

### 2. Configure Environment Variables

The collection uses the following environment variable:
- `base_url`: Base URL of the API (default: `http://localhost:8080`)

Update this in the environment if your server runs on a different host/port.

### 3. Prepare Test Files

For file upload tests, you'll need sample files:
- PDF documents (invoices, receipts, forms)
- Image files (PNG, JPG) with text or documents

## Test Scenarios

### Health Check

#### GET Health Check
- **Endpoint**: `GET /health`
- **Description**: Verifies the API is running and responsive
- **Expected**: 200 OK with body "ok"
- **Response Time**: < 200ms

### OpenAI Extraction

#### Success Scenarios

1. **Success - Single PDF File**
   - **Endpoint**: `POST /extract`
   - **Body**: `multipart/form-data` with:
     - `prompt`: "Extract key fields into JSON. Return ONLY JSON."
     - `files`: Single PDF file
   - **Expected**: 200 OK with JSON response
   - **Response Time**: < 120 seconds

2. **Success - Multiple Files**
   - **Endpoint**: `POST /extract`
   - **Body**: `multipart/form-data` with:
     - `prompt`: Custom prompt for multiple files
     - `files`: Multiple files (add multiple `files` fields)
   - **Expected**: 200 OK with combined JSON response
   - **Response Time**: < 180 seconds

3. **Success - Custom Prompt**
   - **Endpoint**: `POST /extract`
   - **Body**: `multipart/form-data` with:
     - `prompt`: Specific field extraction prompt
     - `files`: Document file
   - **Expected**: 200 OK with JSON matching the custom prompt requirements

4. **Success - Image File (PNG/JPG)**
   - **Endpoint**: `POST /extract`
   - **Body**: `multipart/form-data` with:
     - `prompt`: Image extraction prompt
     - `files`: Image file (PNG, JPG, etc.)
   - **Expected**: 200 OK with extracted JSON

5. **Success - Default Prompt (No Prompt Provided)**
   - **Endpoint**: `POST /extract`
   - **Body**: `multipart/form-data` with:
     - `files`: File (no prompt field)
   - **Expected**: 200 OK (uses default prompt: "Extract key fields into JSON. Return ONLY JSON.")

#### Error Scenarios

6. **Error - No Files**
   - **Endpoint**: `POST /extract`
   - **Body**: `multipart/form-data` with:
     - `prompt`: Any prompt
     - No `files` field
   - **Expected**: 500 Internal Server Error
   - **Error Response**:
     ```json
     {
       "error": {
         "code": "INVALID_REQUEST",
         "message": "No files provided in request"
       }
     }
     ```

7. **Error - Empty Prompt**
   - **Endpoint**: `POST /extract`
   - **Body**: `multipart/form-data` with:
     - `prompt`: "" (empty string)
     - `files`: File
   - **Expected**: 200 OK (empty prompt falls back to default)

### Claude Extraction

All scenarios are identical to OpenAI extraction but use the `/extract-claude` endpoint:

1. **Success - Single PDF File** → `POST /extract-claude`
2. **Success - Multiple Files** → `POST /extract-claude`
3. **Success - Custom Prompt** → `POST /extract-claude`
4. **Success - Image File (PNG/JPG)** → `POST /extract-claude`
5. **Success - Default Prompt (No Prompt Provided)** → `POST /extract-claude`
6. **Error - No Files** → `POST /extract-claude`
7. **Error - Empty Prompt** → `POST /extract-claude`

### Error Scenarios

#### Common Error Cases

1. **404 - Invalid Endpoint**
   - **Endpoint**: `POST /invalid-endpoint`
   - **Expected**: 404 Not Found

2. **405 - Wrong HTTP Method**
   - **Endpoint**: `GET /extract` (should be POST)
   - **Expected**: 405 Method Not Allowed

3. **415 - Unsupported Media Type**
   - **Endpoint**: `POST /extract`
   - **Headers**: `Content-Type: application/json`
   - **Body**: JSON instead of multipart/form-data
   - **Expected**: 415 Unsupported Media Type or 400 Bad Request

## Sample Requests

### Example 1: Basic Extraction (OpenAI)

```http
POST http://localhost:8080/extract
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW

------WebKitFormBoundary7MA4YWxkTrZu0gW
Content-Disposition: form-data; name="prompt"

Extract key fields into JSON. Return ONLY JSON.
------WebKitFormBoundary7MA4YWxkTrZu0gW
Content-Disposition: form-data; name="files"; filename="invoice.pdf"
Content-Type: application/pdf

[PDF file content]
------WebKitFormBoundary7MA4YWxkTrZu0gW--
```

### Example 2: Custom Prompt (Claude)

```http
POST http://localhost:8080/extract-claude
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW

------WebKitFormBoundary7MA4YWxkTrZu0gW
Content-Disposition: form-data; name="prompt"

Extract the following fields: invoice_number, invoice_date, total_amount, vendor_name, line_items (as array). Return ONLY JSON.
------WebKitFormBoundary7MA4YWxkTrZu0gW
Content-Disposition: form-data; name="files"; filename="invoice.pdf"
Content-Type: application/pdf

[PDF file content]
------WebKitFormBoundary7MA4YWxkTrZu0gW--
```

### Example 3: Multiple Files

```http
POST http://localhost:8080/extract
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW

------WebKitFormBoundary7MA4YWxkTrZu0gW
Content-Disposition: form-data; name="prompt"

Extract key fields from all documents. Return combined JSON.
------WebKitFormBoundary7MA4YWxkTrZu0gW
Content-Disposition: form-data; name="files"; filename="doc1.pdf"
Content-Type: application/pdf

[PDF file 1 content]
------WebKitFormBoundary7MA4YWxkTrZu0gW
Content-Disposition: form-data; name="files"; filename="doc2.pdf"
Content-Type: application/pdf

[PDF file 2 content]
------WebKitFormBoundary7MA4YWxkTrZu0gW--
```

## Sample Responses

### Success Response

```json
{
  "name": "John Doe",
  "date": "2024-01-15",
  "amount": 1250.50,
  "description": "Invoice payment"
}
```

### Error Response

```json
{
  "error": {
    "code": "INVALID_REQUEST",
    "message": "No files provided in request"
  }
}
```

### Error Response Codes

- `INVALID_REQUEST`: Request validation failed (e.g., no files)
- `EXTRACTION_FAILED`: Extraction process failed
- `PROCESSING_ERROR`: Processing error occurred
- `EMPTY_RESPONSE`: Empty response from AI service

## Automated Tests

Each request includes automated tests that verify:
- Status codes
- Response format (JSON)
- Response content
- Response times
- Error response structure

## Running the Collection

### Run All Tests

1. Open Postman
2. Select the collection
3. Click "Run" button
4. Select all requests or specific folders
5. Click "Run Akka Agentic Doc Extractor API - Complete"

### Run Individual Requests

1. Select a request from the collection
2. Click "Send"
3. View response and test results in the "Test Results" tab

### Run with Newman (CLI)

```bash
# Install Newman
npm install -g newman

# Run collection
newman run postman_collection.json -e postman_environment.json

# Run with HTML report
newman run postman_collection.json -e postman_environment.json -r html --reporter-html-export report.html
```

## Tips

1. **File Selection**: When adding files in Postman, click the file field and select your test files
2. **Multiple Files**: To add multiple files, add multiple `files` form fields with the same key
3. **Custom Prompts**: Customize prompts to extract specific fields you need
4. **Response Time**: Extraction can take 30-120 seconds depending on file size and AI service
5. **Error Handling**: Check error codes and messages to understand what went wrong

## Troubleshooting

### Connection Refused
- Verify the server is running: `GET /health`
- Check the `base_url` environment variable

### Timeout Errors
- Increase timeout in Postman settings (Settings → General → Request timeout)
- Large files may take longer to process

### 500 Errors
- Check server logs for detailed error messages
- Verify API keys are set in environment variables:
  - `OPENAI_API_KEY` for OpenAI
  - `ANTHROPIC_API_KEY` for Claude

### Empty Responses
- Verify files are valid and readable
- Check prompt is clear and specific
- Ensure AI service API keys are valid

## Collection Structure

```
Akka Agentic Doc Extractor API - Complete
├── Health Check
│   └── GET Health Check
├── OpenAI Extraction
│   ├── Success - Single PDF File
│   ├── Success - Multiple Files
│   ├── Success - Custom Prompt
│   ├── Success - Image File (PNG/JPG)
│   ├── Success - Default Prompt (No Prompt Provided)
│   ├── Error - No Files
│   └── Error - Empty Prompt
├── Claude Extraction
│   ├── Success - Single PDF File
│   ├── Success - Multiple Files
│   ├── Success - Custom Prompt
│   ├── Success - Image File (PNG/JPG)
│   ├── Success - Default Prompt (No Prompt Provided)
│   ├── Error - No Files
│   └── Error - Empty Prompt
└── Error Scenarios
    ├── 404 - Invalid Endpoint
    ├── 405 - Wrong HTTP Method
    └── 415 - Unsupported Media Type
```

## Version

- **Collection Version**: 2.0
- **Last Updated**: 2025-12-25
- **API Version**: Akka Java SDK Agent-based (3.5.5)
