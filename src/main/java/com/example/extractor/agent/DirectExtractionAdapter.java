package com.example.extractor.agent;

import akka.actor.ActorSystem;
import com.example.extractor.claude.ClaudeFileUploader;
import com.example.extractor.claude.ClaudeMessagesClient;
import com.example.extractor.openai.OpenAIFileUploader;
import com.example.extractor.openai.OpenAIResponsesClient;
import com.typesafe.config.Config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Direct invocation adapter that bypasses SDK and calls AI services directly.
 * Supports multi-file attachments and is used as fallback when SDK has limitations.
 */
public class DirectExtractionAdapter implements ExtractionAdapter {
    private final String providerName;
    private final ActorSystem system;
    private final Config config;
    
    public DirectExtractionAdapter(String providerName, ActorSystem system) {
        this.providerName = providerName;
        this.system = system;
        this.config = system.settings().config();
    }
    
    @Override
    public CompletionStage<String> extract(String prompt, List<Path> files) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        try {
            if ("claude".equalsIgnoreCase(providerName)) {
                extractWithClaude(prompt, files, future);
            } else {
                extractWithOpenAI(prompt, files, future);
            }
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    private void extractWithClaude(String prompt, List<Path> files, CompletableFuture<String> future) {
        try {
            String baseUrl = config.getString("akka.javasdk.agent.claude.base-url");
            String model = config.getString("akka.javasdk.agent.claude.model-name");
            
            ClaudeMessagesClient client = new ClaudeMessagesClient(baseUrl, model);
            
            // Upload files and create attachments
            List<ClaudeMessagesClient.ClaudeAttachment> attachments = new ArrayList<>();
            
            if (files != null && !files.isEmpty()) {
                System.out.println("DirectExtractionAdapter (Claude): Processing " + files.size() + " files");
                ClaudeFileUploader fileUploader = new ClaudeFileUploader(baseUrl);
                
                // Upload all files
                CompletionStage<List<String>> uploadResult = fileUploader.uploadAll(files);
                uploadResult.thenCompose(fileIds -> {
                    System.out.println("DirectExtractionAdapter (Claude): Uploaded " + fileIds.size() + " files, creating attachments");
                    // Create attachments with inferred types
                    List<ClaudeMessagesClient.ClaudeAttachment> attachmentList = new ArrayList<>();
                    for (int i = 0; i < fileIds.size() && i < files.size(); i++) {
                        String fileId = fileIds.get(i);
                        Path path = files.get(i);
                        String fileName = path.getFileName().toString().toLowerCase();
                        
                        ClaudeMessagesClient.ClaudeAttachment.Type type;
                        if (fileName.endsWith(".pdf")) {
                            type = ClaudeMessagesClient.ClaudeAttachment.Type.PDF;
                        } else {
                            type = ClaudeMessagesClient.ClaudeAttachment.Type.IMAGE;
                        }
                        
                        attachmentList.add(new ClaudeMessagesClient.ClaudeAttachment(type, fileId));
                        System.out.println("DirectExtractionAdapter (Claude): Created attachment #" + (i+1) + " - " + fileName + " (type: " + type + ", fileId: " + fileId + ")");
                    }
                    
                    System.out.println("DirectExtractionAdapter (Claude): Making AI call with " + attachmentList.size() + " attachments");
                    // Make the AI call with attachments
                    return client.extractJson(prompt, attachmentList);
                }).whenComplete((result, ex) -> {
                    if (ex != null) {
                        future.completeExceptionally(ex);
                    } else {
                        future.complete(result);
                    }
                });
            } else {
                // No files, just text extraction
                client.extractJson(prompt, new ArrayList<>())
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            future.completeExceptionally(ex);
                        } else {
                            future.complete(result);
                        }
                    });
            }
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
    }
    
    private void extractWithOpenAI(String prompt, List<Path> files, CompletableFuture<String> future) {
        try {
            String baseUrl = config.getString("akka.javasdk.agent.openai.base-url");
            String model = config.getString("akka.javasdk.agent.openai.model-name");
            
            OpenAIResponsesClient client = new OpenAIResponsesClient(baseUrl, model);
            
            // Upload files and get file IDs
            List<String> fileIds = new ArrayList<>();
            
            if (files != null && !files.isEmpty()) {
                System.out.println("DirectExtractionAdapter (OpenAI): Processing " + files.size() + " files");
                OpenAIFileUploader fileUploader = new OpenAIFileUploader(baseUrl);
                
                // Upload all files
                CompletionStage<List<String>> uploadResult = fileUploader.uploadAll(files);
                uploadResult.thenCompose(uploadedFileIds -> {
                    System.out.println("DirectExtractionAdapter (OpenAI): Uploaded " + uploadedFileIds.size() + " files");
                    for (int i = 0; i < uploadedFileIds.size(); i++) {
                        System.out.println("DirectExtractionAdapter (OpenAI): File #" + (i+1) + " - fileId: " + uploadedFileIds.get(i));
                    }
                    System.out.println("DirectExtractionAdapter (OpenAI): Making AI call with " + uploadedFileIds.size() + " file IDs");
                    // Make the AI call with file IDs
                    return client.extractJson(prompt, uploadedFileIds);
                }).whenComplete((result, ex) -> {
                    if (ex != null) {
                        future.completeExceptionally(ex);
                    } else {
                        future.complete(result);
                    }
                });
            } else {
                // No files, just text extraction
                client.extractJson(prompt, new ArrayList<>())
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            future.completeExceptionally(ex);
                        } else {
                            future.complete(result);
                        }
                    });
            }
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
    }
    
    @Override
    public CompletionStage<String> healthCheck() {
        return CompletableFuture.completedFuture("Direct " + providerName + " adapter is healthy");
    }
    
    @Override
    public boolean supportsMultiFile() {
        return true; // Direct invocation fully supports multi-file
    }
    
    @Override
    public String getName() {
        return "Direct-" + providerName;
    }
}

