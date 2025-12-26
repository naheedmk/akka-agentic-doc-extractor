package com.example.extractor.agent;

import akka.javasdk.client.ComponentClient;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Client interface for interacting with ExtractionAgent using Akka Java SDK ComponentClient.
 * Provides a clean API for different use cases with built-in session management.
 */
public class ExtractionAgentClient {
    private final ComponentClient componentClient;
    private final String sessionId;
    private static final String AGENT_ID = "extraction-agent"; // Must match @Component(id) in ExtractionAgent

    public ExtractionAgentClient(ComponentClient componentClient) {
        this.componentClient = componentClient;
        this.sessionId = UUID.randomUUID().toString();
    }

    public ExtractionAgentClient(ComponentClient componentClient, String sessionId) {
        this.componentClient = componentClient;
        this.sessionId = sessionId;
    }

    /**
     * Submit an extraction job and get the result.
     * 
     * @param prompt the extraction prompt
     * @param files list of files to extract from
     * @return CompletionStage that completes with the extraction result
     */
    public CompletionStage<String> extract(String prompt, List<Path> files) {
        List<String> filePaths = files.stream()
            .map(Path::toString)
            .collect(Collectors.toList());
        
        ExtractionRequest request = new ExtractionRequest(prompt, filePaths);
        
        // Use method reference to ensure correct agent class inference
        // The ComponentClient will infer ExtractionAgent from the method reference
        return componentClient
            .forAgent()
            .inSession(sessionId)
            .method(ExtractionAgent::extractFromFiles)
            .invokeAsync(request);
    }

    /**
     * Health check for the agent.
     * 
     * @return CompletionStage that completes with the health status
     */
    public CompletionStage<String> healthCheck() {
        // Use method reference to ensure correct agent class inference
        return componentClient
            .forAgent()
            .inSession(sessionId)
            .method(ExtractionAgent::healthCheck)
            .invokeAsync();
    }

    /**
     * Get the current session ID.
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Create a new client with a new session.
     */
    public ExtractionAgentClient withNewSession() {
        return new ExtractionAgentClient(componentClient);
    }
}
