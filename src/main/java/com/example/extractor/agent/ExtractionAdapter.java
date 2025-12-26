package com.example.extractor.agent;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Adapter interface for extraction operations.
 * Allows switching between Akka Java SDK Agent and direct client invocation.
 * Supports multi-file attachments and provides extension mechanism.
 */
public interface ExtractionAdapter {
    
    /**
     * Extract JSON from files using the specified prompt.
     * 
     * @param prompt the extraction prompt
     * @param files list of file paths to extract from
     * @return CompletionStage that completes with the extraction result as JSON string
     */
    CompletionStage<String> extract(String prompt, List<Path> files);
    
    /**
     * Health check for the extraction service.
     * 
     * @return CompletionStage that completes with the health status
     */
    CompletionStage<String> healthCheck();
    
    /**
     * Check if this adapter supports multi-file attachments.
     * 
     * @return true if multi-file attachments are supported
     */
    default boolean supportsMultiFile() {
        return true;
    }
    
    /**
     * Get the adapter name for logging/debugging.
     * 
     * @return adapter name
     */
    String getName();
}

