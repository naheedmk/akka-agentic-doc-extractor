package com.example.extractor.agent;

import akka.javasdk.annotations.Component;

/**
 * OpenAI-specific extraction agent.
 * Configured via 'akka.javasdk.agent.openai' in application.conf.
 */
@Component(id = "openai-agent")
public class OpenAIExtractionAgent extends BaseExtractionAgent {
    // Inherits logic from BaseExtractionAgent
    // The @Component annotation links it to specific configuration
}
