package com.example.extractor.agent;

import akka.javasdk.client.ComponentClient;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Client interface for interacting with ExtractionAgent using Akka Java SDK
 * ComponentClient.
 * Provides a clean API for different use cases with built-in session
 * management.
 */
public class ExtractionAgentClient {
    private final ComponentClient componentClient;
    private final String sessionId;
    private final String agentId;

    public ExtractionAgentClient(ComponentClient componentClient, String agentId) {
        this.componentClient = componentClient;
        this.sessionId = UUID.randomUUID().toString();
        this.agentId = agentId;
    }

    public ExtractionAgentClient(ComponentClient componentClient, String sessionId, String agentId) {
        this.componentClient = componentClient;
        this.sessionId = sessionId;
        this.agentId = agentId;
    }

    /**
     * Submit an extraction job and get the result.
     * 
     * @param prompt the extraction prompt
     * @param files  list of files to extract from
     * @return CompletionStage that completes with the extraction result
     */
    public CompletionStage<String> extract(String prompt, List<Path> files) {
        List<String> filePaths = files.stream()
                .map(Path::toString)
                .collect(Collectors.toList());

        ExtractionRequest request = new ExtractionRequest(prompt, filePaths, agentId);

        // Use BaseExtractionAgent method reference.
        // The ComponentClient (via Main.java proxy) will use the agentId
        // from the request to route to the correct agent implementation.
        return componentClient
                .forAgent()
                .inSession(sessionId)
                .method(BaseExtractionAgent::extractFromFiles)
                .invokeAsync(request);
    }

    /**
     * Health check for the agent.
     * 
     * @return CompletionStage that completes with the health status
     */
    public CompletionStage<String> healthCheck() {
        // Use BaseExtractionAgent method reference
        return componentClient
                .forAgent()
                .inSession(sessionId)
                .method(BaseExtractionAgent::healthCheck)
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
        return new ExtractionAgentClient(componentClient, agentId);
    }
}
