package com.example.extractor.agent;

import akka.javasdk.annotations.Component;

/**
 * Claude-specific extraction agent.
 * Configured via 'akka.javasdk.agent.claude' in application.conf.
 */
@Component(id = "claude-agent")
public class ClaudeExtractionAgent extends BaseExtractionAgent {
    // Inherits logic from BaseExtractionAgent
    // The @Component annotation links it to specific configuration
}
