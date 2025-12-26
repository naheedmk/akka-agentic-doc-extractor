package com.example.extractor.agent.claude;

import akka.javasdk.client.ComponentClient;
import com.example.extractor.agent.ExtractionAgentClient;

/**
 * Factory for creating Claude-based extraction agents using Akka Java SDK.
 */
public class ClaudeAgentFactory {
    
    /**
     * Creates a Claude extraction agent using Akka Java SDK ComponentClient.
     * 
     * @param componentClient the Akka Java SDK ComponentClient
     * @return ExtractionAgentClient for interacting with the agent
     */
    public static ExtractionAgentClient createAgent(ComponentClient componentClient) {
        return new ExtractionAgentClient(componentClient);
    }
}
