package com.example.extractor.agent;

import java.util.List;

/**
 * Request object for extraction operations.
 */
public class ExtractionRequest {
    public final String prompt;
    public final List<String> filePaths;

    public ExtractionRequest(String prompt, List<String> filePaths) {
        this.prompt = prompt;
        this.filePaths = filePaths;
    }
}

