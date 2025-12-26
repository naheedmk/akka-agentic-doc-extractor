package com.example.extractor.agent;

import akka.javasdk.client.ComponentClient;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Adapter that uses Akka Java SDK Agent for extraction.
 * Falls back to direct invocation if SDK has limitations (e.g., text-only support).
 */
public class SdkExtractionAdapter implements ExtractionAdapter {
    private final ExtractionAgentClient agentClient;
    private final DirectExtractionAdapter fallbackAdapter;
    private final String providerName;
    
    public SdkExtractionAdapter(ComponentClient componentClient, String providerName, DirectExtractionAdapter fallbackAdapter) {
        this.agentClient = componentClient != null ? new ExtractionAgentClient(componentClient) : null;
        this.providerName = providerName;
        this.fallbackAdapter = fallbackAdapter;
    }
    
    @Override
    public CompletionStage<String> extract(String prompt, List<Path> files) {
        // If agent client is not available, use fallback
        if (agentClient == null) {
            return fallbackAdapter.extract(prompt, files);
        }
        
        // Try SDK agent first
        try {
            return agentClient.extract(prompt, files)
                .exceptionally(ex -> {
                    // If SDK fails (e.g., AgentResult creation issues), fall back to direct invocation
                    System.err.println("SDK Agent extraction failed, falling back to direct invocation: " + ex.getMessage());
                    return fallbackAdapter.extract(prompt, files)
                        .toCompletableFuture()
                        .join(); // Block to get result for fallback
                });
        } catch (Exception e) {
            // SDK not available, use direct invocation
            System.err.println("SDK Agent not available, using direct invocation");
            return fallbackAdapter.extract(prompt, files);
        }
    }
    
    @Override
    public CompletionStage<String> healthCheck() {
        if (agentClient != null) {
            try {
                return agentClient.healthCheck()
                    .exceptionally(ex -> {
                        return "SDK Agent health check failed: " + ex.getMessage();
                    });
            } catch (Exception e) {
                return fallbackAdapter.healthCheck();
            }
        }
        return fallbackAdapter.healthCheck();
    }
    
    @Override
    public boolean supportsMultiFile() {
        return true; // SDK should support multi-file, but we have fallback
    }
    
    @Override
    public String getName() {
        return "SDK-" + providerName;
    }
}

