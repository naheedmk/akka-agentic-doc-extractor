package com.example.extractor.claude;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import okhttp3.*;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

public class ClaudeMessagesClient {
  private final OkHttpClient http = new OkHttpClient.Builder()
      .connectTimeout(Duration.ofSeconds(30))
      .readTimeout(Duration.ofSeconds(180))
      .writeTimeout(Duration.ofSeconds(180))
      .callTimeout(Duration.ofSeconds(240))
      .build();

  private final ObjectMapper om = new ObjectMapper();
  private final String apiKey = mustGetEnv("ANTHROPIC_API_KEY");
  private final String baseUrl;
  private final String model; // e.g. "claude-sonnet-4-5"

  public ClaudeMessagesClient(String baseUrl, String model) {
    this.baseUrl = baseUrl;
    this.model = model;
  }

  public CompletionStage<String> extractJson(String prompt, List<ClaudeAttachment> attachments) {
    CompletableFuture<String> cf = new CompletableFuture<>();

    ObjectNode root = om.createObjectNode();
    root.put("model", model);
    root.put("max_tokens", 1024);

    ArrayNode messages = root.putArray("messages");
    ObjectNode user = messages.addObject();
    user.put("role", "user");
    ArrayNode content = user.putArray("content");

    content.addObject()
        .put("type", "text")
        .put("text", prompt + "\n\nReturn ONLY valid JSON. No markdown. No extra text.");

    for (ClaudeAttachment a : attachments) {
      if (a.type == ClaudeAttachment.Type.PDF) {
        // document block w/ file_id :contentReference[oaicite:8]{index=8}
        ObjectNode doc = content.addObject();
        doc.put("type", "document");
        ObjectNode source = doc.putObject("source");
        source.put("type", "file");
        source.put("file_id", a.fileId);
      } else {
        // image block w/ file_id :contentReference[oaicite:9]{index=9}
        ObjectNode img = content.addObject();
        img.put("type", "image");
        ObjectNode source = img.putObject("source");
        source.put("type", "file");
        source.put("file_id", a.fileId);
      }
    }

    final String bodyJson;
    try {
      bodyJson = om.writeValueAsString(root);
    } catch (Exception e) {
      cf.completeExceptionally(e);
      return cf;
    }

    Request req = new Request.Builder()
        .url(baseUrl + "/messages")
        .header("x-api-key", apiKey)
        .header("anthropic-version", "2023-06-01") // required :contentReference[oaicite:10]{index=10}
        .header("anthropic-beta", "files-api-2025-04-14") // needed when referencing file_id :contentReference[oaicite:11]{index=11}
        .header("content-type", "application/json")
        .post(RequestBody.create(bodyJson, MediaType.parse("application/json")))
        .build();

    http.newCall(req).enqueue(new Callback() {
      @Override public void onFailure(Call call, IOException e) { cf.completeExceptionally(e); }

      @Override public void onResponse(Call call, Response resp) throws IOException {
        try (resp) {
          String s = resp.body() != null ? resp.body().string() : "";
          if (!resp.isSuccessful()) {
            cf.completeExceptionally(new RuntimeException("Claude messages failed: HTTP " + resp.code() + " " + s));
            return;
          }
          cf.complete(s);
        }
      }
    });

    return cf;
  }

  public static final class ClaudeAttachment {
    public enum Type { PDF, IMAGE }
    public final Type type;
    public final String fileId;
    public ClaudeAttachment(Type type, String fileId) { this.type = type; this.fileId = fileId; }
  }
private static String mustGetEnv(String name) {
  String v = System.getenv(name);
  if (v == null || v.isBlank()) throw new IllegalStateException("Missing env var: " + name);
  return v;
}

}

