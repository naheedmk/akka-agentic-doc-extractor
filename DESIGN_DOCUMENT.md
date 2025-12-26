# Akka Java SDK Agent Extraction Service - Design Document

## Table of Contents
1. [Executive Summary](#executive-summary)
2. [Architecture Overview](#architecture-overview)
3. [Component Architecture](#component-architecture)
4. [SDK Agent Flow](#sdk-agent-flow)
5. [Adapter Pattern Design](#adapter-pattern-design)
6. [Multi-File Support](#multi-file-support)
7. [Fallback Mechanism](#fallback-mechanism)
8. [Sequence Diagrams](#sequence-diagrams)
9. [Data Flow](#data-flow)
10. [Extension Points](#extension-points)

---

## Executive Summary

This document describes the design of an AI-powered document extraction service built on Akka HTTP and the Akka Java SDK Agent framework. The system addresses a critical limitation where the SDK Agent primarily supports text-only prompts by implementing an **Adapter Pattern** that automatically falls back to direct AI service invocation when file attachments are required.

### Key Design Decisions

1. **Adapter Pattern**: Abstraction layer that allows switching between SDK Agent and direct invocation
2. **Automatic Fallback**: Seamless transition when SDK limitations are encountered
3. **Multi-File Support**: Full support for multiple file attachments via direct invocation
4. **Extension Mechanism**: Pluggable adapter interface for future implementations

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         HTTP Client                              │
│                    (Postman, curl, etc.)                        │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ HTTP POST /extract
                             │ multipart/form-data
                             │ (files + prompt)
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    ExtractRoutes                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  processExtractionRequest()                              │   │
│  │  - Parse multipart form data                             │   │
│  │  - Validate files and prompt                             │   │
│  │  - Delegate to ExtractionAdapter                         │   │
│  └──────────────────────────────────────────────────────────┘   │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ ExtractionAdapter.extract()
                             │
                ┌────────────┴────────────┐
                │                         │
                ▼                         ▼
    ┌──────────────────────┐   ┌──────────────────────┐
    │ SdkExtractionAdapter │   │ DirectExtractionAdapter│
    │                      │   │                      │
    │ 1. Try SDK Agent     │   │ Direct AI Service    │
    │ 2. Fallback if fails │   │ Calls (OpenAI/Claude)│
    └──────────┬───────────┘   └──────────┬───────────┘
               │                          │
               │                          │
    ┌──────────┴───────────┐             │
    │                       │             │
    ▼                       ▼             ▼
┌─────────────┐    ┌─────────────────────────────┐
│ SDK Agent   │    │ Direct AI Service Clients  │
│ (Text Only) │    │ - File Upload              │
│             │    │ - Multi-File Support       │
│             │    │ - Full Attachment Support  │
└─────────────┘    └─────────────────────────────┘
```

---

## Component Architecture

### High-Level Component Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│                        Application Layer                          │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                    ExtractRoutes                            │ │
│  │  - HTTP Route Handler                                       │ │
│  │  - Multipart Form Data Parser                               │ │
│  │  - Error Handling                                           │ │
│  └───────────────────────┬──────────────────────────────────────┘ │
│                          │                                        │
│                          │ Uses                                   │
│                          ▼                                        │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │              ExtractionAdapter (Interface)                  │ │
│  │  + extract(prompt, files): CompletionStage<String>          │ │
│  │  + healthCheck(): CompletionStage<String>                   │ │
│  │  + supportsMultiFile(): boolean                             │ │
│  │  + getName(): String                                        │ │
│  └───────────────────────┬──────────────────────────────────────┘ │
│                          │                                        │
│         ┌────────────────┴────────────────┐                     │
│         │                                   │                     │
│         ▼                                   ▼                     │
│  ┌──────────────────────┐        ┌──────────────────────┐        │
│  │ SdkExtractionAdapter │        │ DirectExtractionAdapter│       │
│  │                      │        │                      │        │
│  │ - Tries SDK first    │        │ - Direct API calls   │        │
│  │ - Falls back on fail │        │ - Full file support  │        │
│  └──────────┬───────────┘        └──────────┬───────────┘        │
│             │                                │                    │
└─────────────┼────────────────────────────────┼────────────────────┘
              │                                │
              │                                │
┌─────────────┴────────────────────────────────┴────────────────────┐
│                      SDK / Service Layer                           │
├────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────────────┐        ┌──────────────────────┐         │
│  │  ExtractionAgent     │        │  Direct Clients      │         │
│  │  (Akka Java SDK)     │        │                      │         │
│  │                      │        │  - OpenAIFileUploader│         │
│  │  @Component(id=...)  │        │  - OpenAIResponsesClient│       │
│  │  - extractFromFiles()│        │  - ClaudeFileUploader │         │
│  │  - healthCheck()     │        │  - ClaudeMessagesClient│        │
│  └──────────┬───────────┘        └──────────┬───────────┘         │
│             │                                │                     │
│             │ Uses                           │ Uses               │
│             ▼                                ▼                     │
│  ┌──────────────────────┐        ┌──────────────────────┐         │
│  │  ComponentClient     │        │  AI Service APIs     │         │
│  │  (Akka Java SDK)     │        │                      │         │
│  │  - forAgent()        │        │  - OpenAI API       │         │
│  │  - invokeAsync()     │        │  - Claude API        │         │
│  └──────────────────────┘        └──────────────────────┘         │
│                                                                     │
└────────────────────────────────────────────────────────────────────┘
```

---

## SDK Agent Flow

### SDK Agent Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                    SDK Agent Request Flow                        │
└──────────────────────────────────────────────────────────────────┘

Client Request
     │
     │ 1. HTTP POST /extract
     │    multipart/form-data
     ▼
┌─────────────────────────────────────────────────────────────────┐
│ ExtractRoutes.processExtractionRequest()                         │
│  - Parse multipart form data                                     │
│  - Extract files and prompt                                      │
│  - Validate input                                                │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        │ 2. adapter.extract(prompt, files)
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│ SdkExtractionAdapter.extract()                                  │
│  - Check if agentClient is available                            │
│  - Try SDK Agent first                                          │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        │ 3. agentClient.extract(prompt, files)
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│ ExtractionAgentClient.extract()                                 │
│  - Convert files to filePaths (List<String>)                    │
│  - Create ExtractionRequest(prompt, filePaths)                  │
│  - Call ComponentClient.forAgent()                               │
│  - Invoke ExtractionAgent::extractFromFiles                     │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        │ 4. componentClient.forAgent()
                        │    .method(ExtractionAgent::extractFromFiles)
                        │    .invokeAsync(request)
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│ ComponentClient (Akka Java SDK)                                 │
│  - Routes to Agent via ComponentClientImpl                      │
│  - Serializes ExtractionRequest to BytesPayload                 │
│  - Sends to AgentClient.send()                                  │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        │ 5. AgentClient.send(AgentRequest)
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│ handleAgentSend() (Main.java)                                   │
│  - Deserializes ExtractionRequest from payload                  │
│  - Instantiates ExtractionAgent                                 │
│  - Invokes agent.extractFromFiles(request)                      │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        │ 6. agent.extractFromFiles(request)
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│ ExtractionAgent.extractFromFiles()                              │
│  - Creates Effect with RequestModel                             │
│  - RequestModel contains:                                        │
│    * modelProvider (OpenAI/Claude)                              │
│    * systemMessage                                              │
│    * userMessage (prompt)                                       │
│    * responseType                                               │
│  - Returns Effect<String>                                        │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        │ 7. Effect execution
                        │    (BaseAgentEffectBuilder)
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│ Effect Execution (handleAgentSend continued)                    │
│  - Extract RequestModel from Effect                             │
│  - Extract modelProvider, messages, responseType                 │
│  - Extract filePaths from ExtractionRequest                     │
│  - Make AI call with files                                      │
│  - Create AgentResult from response                              │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        │ 8. Return AgentResult
                        │    (or fallback on failure)
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│ ComponentClientImpl                                              │
│  - Extracts response from AgentResult                            │
│  - Returns CompletionStage<String>                               │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        │ 9. Return JSON response
                        ▼
                    Client Response
```

### SDK Agent Limitation

**The Problem:**
The Akka Java SDK Agent's `Effect` API primarily supports text-based prompts. When `ExtractionRequest` is serialized and passed through the SDK, the `filePaths` list may be lost or not properly handled by the SDK's internal serialization mechanism.

**Evidence:**
- Logs show: `ExtractionRequest filePaths: 0 files` after deserialization
- `AgentResult` creation fails due to SDK internal type mismatches
- SDK expects `AgentResult` but we can't create it reliably

**Root Cause:**
1. SDK serialization/deserialization may not preserve complex types correctly
2. `AgentResult` class is not publicly accessible (internal SDK class)
3. SDK's Effect execution model doesn't natively support file attachments

---

## Adapter Pattern Design

### Adapter Pattern Structure

```
┌──────────────────────────────────────────────────────────────────┐
│                    ExtractionAdapter Interface                   │
│                                                                   │
│  + extract(prompt: String, files: List<Path>):                  │
│      CompletionStage<String>                                     │
│  + healthCheck(): CompletionStage<String>                         │
│  + supportsMultiFile(): boolean                                  │
│  + getName(): String                                             │
└───────────────────────┬───────────────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┐
        │               │               │
        ▼               ▼               ▼
┌───────────────┐ ┌───────────────┐ ┌───────────────┐
│   SDK Adapter │ │ Direct Adapter │ │ Custom Adapter│
│               │ │                │ │  (Future)     │
└───────────────┘ └───────────────┘ └───────────────┘
```

### SdkExtractionAdapter Flow

```
┌──────────────────────────────────────────────────────────────────┐
│              SdkExtractionAdapter.extract()                       │
└──────────────────────────────────────────────────────────────────┘

Input: prompt, files
     │
     │ 1. Check agentClient availability
     ▼
┌─────────────────────────────────────────────────────────────────┐
│ if (agentClient == null)                                        │
│   → return fallbackAdapter.extract(prompt, files)              │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        │ 2. Try SDK Agent
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│ try {                                                            │
│   return agentClient.extract(prompt, files)                     │
│     .exceptionally(ex -> {                                       │
│       // SDK failed - fallback to direct                        │
│       return fallbackAdapter.extract(...)                       │
│     })                                                           │
│ } catch (Exception e) {                                         │
│   // SDK not available - use direct                             │
│   return fallbackAdapter.extract(prompt, files)                 │
│ }                                                                │
└─────────────────────────────────────────────────────────────────┘
```

### DirectExtractionAdapter Flow

```
┌──────────────────────────────────────────────────────────────────┐
│            DirectExtractionAdapter.extract()                     │
└──────────────────────────────────────────────────────────────────┘

Input: prompt, files
     │
     │ 1. Determine provider (OpenAI/Claude)
     ▼
┌─────────────────────────────────────────────────────────────────┐
│ if (provider == "claude") {                                     │
│   → extractWithClaude(prompt, files)                            │
│ } else {                                                         │
│   → extractWithOpenAI(prompt, files)                             │
│ }                                                                │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        │ 2. Upload files
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│ if (files != null && !files.isEmpty()) {                        │
│   fileUploader.uploadAll(files)                                 │
│     .thenCompose(fileIds -> {                                   │
│       // Continue with file IDs                                 │
│     })                                                           │
│ }                                                                │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        │ 3. Make AI call with attachments
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│ For Claude:                                                      │
│   - Create ClaudeAttachment objects                             │
│   - Infer type (PDF/IMAGE) from file extension                  │
│   - client.extractJson(prompt, attachments)                     │
│                                                                  │
│ For OpenAI:                                                      │
│   - Use file IDs directly                                        │
│   - client.extractJson(prompt, fileIds)                          │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        │ 4. Return JSON response
                        ▼
                    CompletionStage<String>
```

---

## Multi-File Support

### Multi-File Processing Flow

```
┌──────────────────────────────────────────────────────────────────┐
│                    Multi-File Request                           │
└──────────────────────────────────────────────────────────────────┘

HTTP Request
  - files: [file1.pdf, file2.jpg, file3.pdf]
  - prompt: "Extract key fields from all documents"
     │
     ▼
┌─────────────────────────────────────────────────────────────────┐
│ ExtractRoutes                                                   │
│  - Parses all files from multipart form data                    │
│  - Validates: pf.files.size() > 0                               │
│  - Checks: adapter.supportsMultiFile()                           │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        │ adapter.extract(prompt, files)
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│ SdkExtractionAdapter                                            │
│  - Tries SDK Agent (may fail with 0 files)                     │
│  - Falls back to DirectExtractionAdapter                        │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        │ fallbackAdapter.extract(prompt, files)
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│ DirectExtractionAdapter                                         │
│                                                                  │
│  1. Upload All Files                                            │
│     fileUploader.uploadAll([file1, file2, file3])               │
│       → [fileId1, fileId2, fileId3]                             │
│                                                                  │
│  2. Create Attachments                                          │
│     For Claude:                                                 │
│       - file1.pdf → ClaudeAttachment(Type.PDF, fileId1)         │
│       - file2.jpg → ClaudeAttachment(Type.IMAGE, fileId2)      │
│       - file3.pdf → ClaudeAttachment(Type.PDF, fileId3)         │
│                                                                  │
│     For OpenAI:                                                 │
│       - Use fileIds directly: [fileId1, fileId2, fileId3]      │
│                                                                  │
│  3. Make AI Call                                                │
│     client.extractJson(prompt, attachments/fileIds)             │
│       → Combined JSON response from all files                   │
└─────────────────────────────────────────────────────────────────┘
```

### File Upload Sequence

```
┌──────────┐    ┌──────────────┐    ┌─────────────┐    ┌──────────┐
│  Client  │    │ ExtractRoutes│    │   Adapter   │    │ AI API   │
└────┬─────┘    └──────┬───────┘    └──────┬──────┘    └────┬─────┘
     │                  │                    │                │
     │ POST /extract    │                    │                │
     │ [file1, file2]   │                    │                │
     ├─────────────────>│                    │                │
     │                  │                    │                │
     │                  │ extract(prompt,    │                │
     │                  │          files)    │                │
     │                  ├───────────────────>│                │
     │                  │                    │                │
     │                  │                    │ uploadAll()    │
     │                  │                    ├────────────────>│
     │                  │                    │                │
     │                  │                    │ [fileId1,      │
     │                  │                    │  fileId2]      │
     │                  │                    │<────────────────┤
     │                  │                    │                │
     │                  │                    │ extractJson() │
     │                  │                    │ (with fileIds) │
     │                  │                    ├────────────────>│
     │                  │                    │                │
     │                  │                    │ JSON response  │
     │                  │                    │<────────────────┤
     │                  │                    │                │
     │                  │ JSON response      │                │
     │                  │<───────────────────┤                │
     │                  │                    │                │
     │ JSON response    │                    │                │
     │<─────────────────┤                    │                │
     │                  │                    │                │
```

---

## Fallback Mechanism

### Fallback Decision Tree

```
                    Request with Files
                           │
                           ▼
              ┌────────────────────────┐
              │  SdkExtractionAdapter   │
              └────────────┬────────────┘
                           │
            ┌──────────────┴──────────────┐
            │                             │
            ▼                             ▼
    ┌───────────────┐           ┌───────────────┐
    │ SDK Available │           │ SDK Not Avail │
    └───────┬───────┘           └───────┬───────┘
            │                           │
            │ Try SDK                   │ Use Direct
            ▼                           ▼
    ┌───────────────┐           ┌───────────────┐
    │ SDK Succeeds  │           │ DirectAdapter │
    └───────┬───────┘           └───────┬───────┘
            │                           │
            │ Return                    │ Return
            ▼                           ▼
        Success                      Success
            │                           │
            │                           │
    ┌───────┴───────┐
    │ SDK Fails     │
    └───────┬───────┘
            │
            │ Fallback
            ▼
    ┌───────────────┐
    │ DirectAdapter │
    └───────┬───────┘
            │
            │ Return
            ▼
        Success
```

### Fallback Triggers

1. **ComponentClient is null**
   - SDK not initialized
   - License key missing
   - SDK runtime not available

2. **SDK Agent fails**
   - `AgentResult` creation fails
   - File paths lost during serialization
   - Effect execution errors

3. **Exception during SDK call**
   - `ClassCastException` (AgentResult)
   - `ClassNotFoundException` (AgentResult$)
   - Any runtime exception

### Fallback Implementation

```java
public CompletionStage<String> extract(String prompt, List<Path> files) {
    if (agentClient == null) {
        // SDK not available - use direct
        return fallbackAdapter.extract(prompt, files);
    }
    
    try {
        return agentClient.extract(prompt, files)
            .exceptionally(ex -> {
                // SDK failed - fallback to direct
                System.err.println("SDK Agent extraction failed, " +
                    "falling back to direct invocation: " + ex.getMessage());
                return fallbackAdapter.extract(prompt, files)
                    .toCompletableFuture()
                    .join(); // Block to get result for fallback
            });
    } catch (Exception e) {
        // SDK not available - use direct
        return fallbackAdapter.extract(prompt, files);
    }
}
```

---

## Sequence Diagrams

### Successful SDK Agent Flow

```
Client    ExtractRoutes    SdkAdapter    AgentClient    ComponentClient    Agent    AI Service
  │            │               │              │               │            │          │
  │ POST       │               │              │               │            │          │
  ├───────────>│               │              │               │            │          │
  │            │               │              │               │            │          │
  │            │ extract()     │              │               │            │          │
  │            ├──────────────>│              │               │            │          │
  │            │               │              │               │            │          │
  │            │               │ extract()    │               │            │          │
  │            │               ├─────────────>│               │            │          │
  │            │               │              │               │            │          │
  │            │               │              │ invokeAsync() │            │          │
  │            │               │              ├──────────────>│            │          │
  │            │               │              │               │            │          │
  │            │               │              │               │ send()     │          │
  │            │               │              │               ├───────────>│          │
  │            │               │              │               │            │          │
  │            │               │              │               │            │ extract()│
  │            │               │              │               │            ├─────────>│
  │            │               │              │               │            │          │
  │            │               │              │               │            │ Response │
  │            │               │              │               │            │<─────────┤
  │            │               │              │               │            │          │
  │            │               │              │               │ AgentResult│          │
  │            │               │              │               │<───────────┤          │
  │            │               │              │               │            │          │
  │            │               │              │ String        │            │          │
  │            │               │              │<───────────────┤            │          │
  │            │               │              │               │            │          │
  │            │               │ String       │               │            │          │
  │            │               │<─────────────┤               │            │          │
  │            │               │              │               │            │          │
  │            │ String        │              │               │            │          │
  │            │<──────────────┤              │               │            │          │
  │            │               │              │               │            │          │
  │ Response  │               │              │               │            │          │
  │<──────────┤               │              │               │            │          │
```

### Fallback to Direct Invocation

```
Client    ExtractRoutes    SdkAdapter    AgentClient    DirectAdapter    FileUploader    AI Service
  │            │               │              │               │               │              │
  │ POST       │               │              │               │               │              │
  ├───────────>│               │              │               │               │              │
  │            │               │              │               │               │              │
  │            │ extract()     │              │               │               │              │
  │            ├──────────────>│              │               │               │              │
  │            │               │              │               │               │              │
  │            │               │ extract()    │               │               │              │
  │            │               ├─────────────>│               │               │              │
  │            │               │              │               │               │              │
  │            │               │              │ Exception     │               │              │
  │            │               │              ├───────────────>│               │              │
  │            │               │              │               │               │              │
  │            │               │              │               │ extract()     │              │
  │            │               │              │               ├──────────────>│              │
  │            │               │              │               │               │              │
  │            │               │              │               │ uploadAll()   │              │
  │            │               │              │               ├──────────────>│              │
  │            │               │              │               │               │              │
  │            │               │              │               │               │ Upload      │
  │            │               │              │               │               ├─────────────>│
  │            │               │              │               │               │              │
  │            │               │              │               │               │ [fileIds]   │
  │            │               │              │               │               │<─────────────┤
  │            │               │              │               │               │              │
  │            │               │              │               │ [fileIds]     │              │
  │            │               │              │               │<──────────────┤              │
  │            │               │              │               │               │              │
  │            │               │              │               │ extractJson()  │              │
  │            │               │              │               ├──────────────────────────────>│
  │            │               │              │               │               │              │
  │            │               │              │               │ JSON response │              │
  │            │               │              │               │<──────────────────────────────┤
  │            │               │              │               │               │              │
  │            │               │ String       │               │               │              │
  │            │               │<─────────────┤               │               │              │
  │            │               │              │               │               │              │
  │            │ String        │              │               │               │              │
  │            │<──────────────┤              │               │               │              │
  │            │               │              │               │               │              │
  │ Response  │               │              │               │               │              │
  │<──────────┤               │              │               │               │              │
```

---

## Data Flow

### Request Processing Pipeline

```
┌──────────────────────────────────────────────────────────────────┐
│                    Request Processing Pipeline                    │
└──────────────────────────────────────────────────────────────────┘

1. HTTP Request
   ┌─────────────────────────────────────┐
   │ POST /extract                       │
   │ Content-Type: multipart/form-data    │
   │                                     │
   │ --boundary                          │
   │ Content-Disposition: form-data;     │
   │   name="prompt"                     │
   │                                     │
   │ Extract key fields                  │
   │ --boundary                          │
   │ Content-Disposition: form-data;     │
   │   name="files"; filename="doc1.pdf" │
   │ Content-Type: application/pdf        │
   │                                     │
   │ [binary file data]                  │
   │ --boundary--                        │
   └─────────────────────────────────────┘
                    │
                    ▼
2. MultipartUtil.parseOnce()
   ┌─────────────────────────────────────┐
   │ ParsedForm {                        │
   │   prompt: "Extract key fields"      │
   │   files: [Path("/tmp/upload-...")]  │
   │ }                                   │
   └─────────────────────────────────────┘
                    │
                    ▼
3. ExtractionAdapter.extract()
   ┌─────────────────────────────────────┐
   │ Input:                              │
   │   prompt: "Extract key fields"      │
   │   files: [Path("/tmp/upload-...")]  │
   └─────────────────────────────────────┘
                    │
        ┌───────────┴───────────┐
        │                       │
        ▼                       ▼
   ┌─────────┐           ┌──────────────┐
   │   SDK   │           │    Direct    │
   └────┬────┘           └──────┬───────┘
        │                      │
        │                      │
        ▼                      ▼
4a. SDK Path             4b. Direct Path
   ┌──────────┐            ┌──────────┐
   │ Request  │            │ Upload   │
   │ Serialize│            │ Files    │
   └────┬─────┘            └────┬─────┘
        │                       │
        │                       ▼
        │                  ┌──────────┐
        │                  │ Get IDs  │
        │                  └────┬─────┘
        │                       │
        └───────────┬───────────┘
                    │
                    ▼
5. AI Service Call
   ┌─────────────────────────────────────┐
   │ OpenAI/Claude API                    │
   │ - Prompt: "Extract key fields"       │
   │ - Files: [fileId1, fileId2, ...]     │
   └─────────────────────────────────────┘
                    │
                    ▼
6. Response
   ┌─────────────────────────────────────┐
   │ JSON Response                         │
   │ {                                     │
   │   "field1": "value1",                 │
   │   "field2": "value2",                 │
   │   ...                                 │
   │ }                                     │
   └─────────────────────────────────────┘
```

---

## Extension Points

### Custom Adapter Implementation

```java
public class CustomExtractionAdapter implements ExtractionAdapter {
    @Override
    public CompletionStage<String> extract(String prompt, List<Path> files) {
        // Custom implementation
        // - Could use different AI service
        // - Could add preprocessing
        // - Could add caching
        // - Could add rate limiting
    }
    
    @Override
    public CompletionStage<String> healthCheck() {
        // Custom health check
    }
    
    @Override
    public boolean supportsMultiFile() {
        return true; // or false
    }
    
    @Override
    public String getName() {
        return "CustomAdapter";
    }
}
```

### Adapter Registration

```java
// In Main.java
ExtractionAdapter customAdapter = new CustomExtractionAdapter();
ExtractRoutes routes = new ExtractRoutes(system, customAdapter, claudeAdapter);
```

---

## Summary

### Key Design Principles

1. **Separation of Concerns**: Routes handle HTTP, Adapters handle extraction logic
2. **Open/Closed Principle**: Open for extension (new adapters), closed for modification
3. **Fail-Safe Design**: Automatic fallback ensures service availability
4. **Multi-File Support**: Full support via direct invocation path

### Benefits

1. **Reliability**: Automatic fallback ensures requests always succeed
2. **Flexibility**: Easy to add new adapters or AI services
3. **Maintainability**: Clear separation of SDK and direct invocation paths
4. **Extensibility**: Interface-based design allows custom implementations

### Trade-offs

1. **Complexity**: Additional abstraction layer
2. **Performance**: Fallback adds slight overhead (minimal)
3. **SDK Features**: Some SDK features may not be available in fallback mode

---

## Conclusion

The adapter pattern successfully addresses the SDK Agent's text-only limitation by providing a seamless fallback mechanism. The system automatically detects when the SDK cannot handle file attachments and switches to direct AI service invocation, ensuring full multi-file support while maintaining the option to use SDK features when available.

