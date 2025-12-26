package com.example.extractor.agent.openai;

import akka.javasdk.client.ComponentClient;
import com.example.extractor.agent.ExtractionAgentClient;

/**
 * Factory for creating OpenAI-based extraction agents using Akka Java SDK.
 */
public class OpenAIAgentFactory {
    
    /**
     * Creates an OpenAI extraction agent using Akka Java SDK ComponentClient.
     * 
     * @param componentClient the Akka Java SDK ComponentClient
     * @return ExtractionAgentClient for interacting with the agent
     */
    public static ExtractionAgentClient createAgent(ComponentClient componentClient) {
        return new ExtractionAgentClient(componentClient);
    }
}
