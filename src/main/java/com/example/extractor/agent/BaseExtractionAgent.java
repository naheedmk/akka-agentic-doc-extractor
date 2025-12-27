package com.example.extractor.agent;

import akka.javasdk.agent.Agent;

/**
 * Abstract base class for extraction agents.
 * Contains common logic for extraction and health checks.
 */
public abstract class BaseExtractionAgent extends Agent {

    /**
     * Extract JSON data from multiple files.
     * 
     * @param request the extraction request containing prompt and file paths
     * @return Effect containing the extracted JSON response
     */
    public Effect<String> extractFromFiles(ExtractionRequest request) {
        // Build a comprehensive prompt that includes file information
        StringBuilder fullPrompt = new StringBuilder();
        fullPrompt.append("Extract key fields from the following files into JSON format. ");
        fullPrompt.append("Return ONLY valid JSON, no additional text.\n\n");
        fullPrompt.append("Files to process:\n");
        for (int i = 0; i < request.filePaths.size(); i++) {
            fullPrompt.append(String.format("%d. %s\n", i + 1, request.filePaths.get(i)));
        }
        fullPrompt.append("\n").append(request.prompt);

        return effects()
                .systemMessage(
                        "You are a document extraction agent. Your task is to extract structured data from documents and return it as valid JSON.")
                .userMessage(fullPrompt.toString())
                .thenReply();
    }

    /**
     * Health check for the agent.
     * 
     * @return Effect confirming agent is ready
     */
    public Effect<String> healthCheck() {
        return effects()
                .systemMessage("You are a helpful assistant.")
                .userMessage("Respond with 'ok' if you are ready to process extraction requests.")
                .thenReply();
    }
}
