package com.example.extractor.agent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Request object for extraction operations.
 */
public class ExtractionRequest {
    public final String prompt;
    public final List<String> filePaths;
    public final String agentId;

    @JsonCreator
    public ExtractionRequest(
            @JsonProperty("prompt") String prompt,
            @JsonProperty("filePaths") List<String> filePaths,
            @JsonProperty("agentId") String agentId) {
        this.prompt = prompt;
        this.filePaths = filePaths;
        this.agentId = agentId;
    }

    public ExtractionRequest(String prompt, List<String> filePaths) {
        this(prompt, filePaths, "openai-agent");
    }

    // Used by reflection in Main.java
    public String agentId() {
        return agentId;
    }

    // Used by reflection in Main.java
    public Object payload() {
        return this;
    }
}
