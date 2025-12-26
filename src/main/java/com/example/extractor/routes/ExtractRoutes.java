package com.example.extractor.routes;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.Multipart;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.stream.Materializer;

import com.example.extractor.agent.ExtractionAdapter;
import com.example.extractor.util.MultipartUtil;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class ExtractRoutes extends AllDirectives {

  private final akka.actor.ActorSystem system;
  private final Materializer mat;
  private final ExtractionAdapter openaiAdapter;
  private final ExtractionAdapter claudeAdapter;

  public ExtractRoutes(
      akka.actor.ActorSystem system,
      ExtractionAdapter openaiAdapter,
      ExtractionAdapter claudeAdapter
  ) {
    this.system = system;
    this.mat = Materializer.createMaterializer(system);
    this.openaiAdapter = openaiAdapter;
    this.claudeAdapter = claudeAdapter;
  }

  public Route routes() {
    return concat(
        path("health", () ->
            get(() -> complete("ok"))
        ),

        // -------- OpenAI: /extract --------
        path("extract", () ->
            post(() ->
                entity(Unmarshaller.entityToMultipartFormData(), (Multipart.FormData formData) -> {
                  CompletionStage<String> out = processExtractionRequest(
                      formData, 
                      openaiAdapter, 
                      "OpenAI"
                  );
                  return handleExtractionResponse(out, "OpenAI");
                })
            )
        ),

        // -------- Claude: /extract-claude --------
        path("extract-claude", () ->
            post(() ->
                entity(Unmarshaller.entityToMultipartFormData(), (Multipart.FormData formData) -> {
                  CompletionStage<String> out = processExtractionRequest(
                      formData, 
                      claudeAdapter, 
                      "Claude"
                  );
                  return handleExtractionResponse(out, "Claude");
                })
            )
        )
    );
  }

  /**
   * Common method to process extraction requests using the extraction adapter.
   * Handles multipart parsing, validation, and delegates to the adapter.
   * Supports multi-file attachments.
   */
  private CompletionStage<String> processExtractionRequest(
      Multipart.FormData formData,
      ExtractionAdapter adapter,
      String providerName) {
    
    CompletionStage<MultipartUtil.ParsedForm> parsed =
        MultipartUtil.parseOnce(formData, "files", "prompt", mat)
            .exceptionally(ex -> {
              String errorMsg = extractFullErrorMessage(ex, "Failed to parse multipart form data");
              system.log().error("Multipart parsing failed: {}", errorMsg, ex);
              throw new RuntimeException("Multipart parsing error: " + errorMsg, ex);
            });

    return parsed.thenCompose(pf -> {
      try {
        if (pf.files == null || pf.files.isEmpty()) {
          String errorMsg = "No files provided in request";
          system.log().error(errorMsg);
          throw new IllegalArgumentException(errorMsg);
        }

        String prompt = normalizePrompt(
            pf.prompt,
            "Extract key fields into JSON. Return ONLY JSON."
        );

        // Use adapter (handles SDK with fallback to direct invocation)
        system.log().info("Using {} adapter for {} extraction ({} files)", adapter.getName(), providerName, pf.files.size());
        
        // Log file details for debugging
        for (int i = 0; i < pf.files.size(); i++) {
          system.log().info("  File #{}: {}", i + 1, pf.files.get(i).getFileName());
        }
        
        // Check if adapter supports multi-file
        if (pf.files.size() > 1 && !adapter.supportsMultiFile()) {
          system.log().warning("Adapter " + adapter.getName() + " does not support multi-file, but " + 
              pf.files.size() + " files provided. Will attempt anyway.");
        }
        
        return adapter.extract(prompt, pf.files)
          .thenApply(json -> {
            if (json == null || json.isBlank()) {
              String errorMsg = "Empty response from adapter";
              system.log().error("{} extraction failed: {}", providerName, errorMsg);
              throw new RuntimeException(
                  String.format("%s extraction error: %s", providerName, errorMsg));
            }
            system.log().info("✓ {} extraction completed successfully using {} adapter", providerName, adapter.getName());
            return json;
          })
          .exceptionally(ex -> {
            String errorMsg = extractFullErrorMessage(ex, 
                String.format("Failed to extract JSON from %s", providerName));
            system.log().error("{} JSON extraction failed: {}", providerName, errorMsg, ex);
            throw new RuntimeException(
                String.format("%s JSON extraction error: %s", providerName, errorMsg), ex);
          });
      } catch (Exception e) {
        String errorMsg = extractFullErrorMessage(e, 
            String.format("Error processing %s extraction request", providerName));
        system.log().error("{} extraction processing failed: {}", providerName, errorMsg, e);
        return CompletableFuture.failedStage(
            new RuntimeException(String.format("Processing error: %s", errorMsg), e));
      }
    });
  }
  

  /**
   * Common method to handle extraction responses and return appropriate HTTP responses.
   */
  private akka.http.javadsl.server.Route handleExtractionResponse(
      CompletionStage<String> jsonFuture, 
      String providerName) {
    
    return onComplete(jsonFuture, result -> {
      if (result.isSuccess()) {
        String json = result.get();
        if (json == null || json.isBlank()) {
          String errorMsg = String.format("Empty response from %s", providerName);
          system.log().error(errorMsg);
          return complete(StatusCodes.INTERNAL_SERVER_ERROR,
              HttpEntities.create(ContentTypes.APPLICATION_JSON,
                  createErrorJson("EMPTY_RESPONSE", errorMsg)));
        }
        return complete(HttpEntities.create(ContentTypes.APPLICATION_JSON, json));
      } else {
        Throwable ex = result.failed().get();
        String errorMsg = extractFullErrorMessage(ex, 
            String.format("Failed to process %s extraction request", providerName));
        system.log().error("{} extraction failed: {}", providerName, errorMsg, ex);
        
        // Determine appropriate error code
        String errorCode = "EXTRACTION_FAILED";
        if (ex instanceof IllegalArgumentException) {
          errorCode = "INVALID_REQUEST";
        } else if (ex instanceof IllegalStateException) {
          errorCode = "PROCESSING_ERROR";
        }
        
        return complete(StatusCodes.INTERNAL_SERVER_ERROR,
            HttpEntities.create(ContentTypes.APPLICATION_JSON,
                createErrorJson(errorCode, errorMsg)));
      }
    });
  }

  /**
   * Handles extraction failures with proper error responses.
   * This is used as a fallback route when extraction fails.
   */
  private akka.http.javadsl.server.Route handleExtractionFailure(String providerName) {
    return extractRequestContext(ctx -> {
      String errorMsg = String.format("Failed to process %s extraction request", providerName);
      system.log().error("{} extraction failed", providerName);
      return complete(StatusCodes.INTERNAL_SERVER_ERROR,
          HttpEntities.create(ContentTypes.APPLICATION_JSON,
              createErrorJson("EXTRACTION_FAILED", errorMsg)));
    });
  }

  private static String normalizePrompt(String prompt, String fallback) {
    if (prompt == null || prompt.isBlank()) return fallback;
    return prompt;
  }

  /**
   * Extracts the full error message from an exception, including all causes in the chain.
   * This ensures we capture the complete error information from the source of failure.
   */
  private static String extractFullErrorMessage(Throwable ex, String defaultMessage) {
    if (ex == null) {
      return defaultMessage;
    }

    StringBuilder sb = new StringBuilder();
    Throwable current = ex;
    int depth = 0;
    final int maxDepth = 10; // Prevent infinite loops

    while (current != null && depth < maxDepth) {
      if (depth > 0) {
        sb.append(" -> Caused by: ");
      }

      String message = current.getMessage();
      if (message != null && !message.isBlank()) {
        sb.append(message);
      } else {
        sb.append(current.getClass().getSimpleName());
      }

      // Include additional context for specific exception types
      if (current instanceof java.io.IOException) {
        sb.append(" [IOException]");
      } else if (current instanceof java.net.SocketTimeoutException) {
        sb.append(" [Timeout]");
      } else if (current instanceof java.util.concurrent.TimeoutException) {
        sb.append(" [Timeout]");
      } else if (current instanceof java.net.UnknownHostException) {
        sb.append(" [Network Error]");
      }

      current = current.getCause();
      depth++;
    }

    String result = sb.toString();
    return result.isEmpty() ? defaultMessage : result;
  }

  /**
   * Creates a JSON error response with error code and message.
   */
  private static String createErrorJson(String errorCode, String errorMessage) {
    return String.format(
        "{\"error\":{\"code\":\"%s\",\"message\":\"%s\"}}",
        escapeJson(errorCode),
        escapeJson(errorMessage)
    );
  }

  /**
   * Escapes special characters in JSON strings.
   */
  private static String escapeJson(String str) {
    if (str == null) return "";
    return str.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

}

