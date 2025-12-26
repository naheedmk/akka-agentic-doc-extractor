package com.example.extractor.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import okhttp3.Protocol;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class OpenAIResponsesClient {
  private final OkHttpClient http = new OkHttpClient.Builder()
      .connectTimeout(Duration.ofSeconds(30))
      .readTimeout(Duration.ofSeconds(600))
      .writeTimeout(Duration.ofSeconds(600))
      .callTimeout(Duration.ofSeconds(900))
      .protocols(List.of(Protocol.HTTP_1_1))   // ✅ force no HTTP/2
      .build();

  private final ObjectMapper om = new ObjectMapper();
  private final String baseUrl;
  private final String model;
  private final String apiKey;

  public OpenAIResponsesClient(String baseUrl, String model) {
    this.baseUrl = baseUrl;
    this.model = model;
    this.apiKey = mustGetEnv("OPENAI_API_KEY");
  }

  public CompletionStage<String> extractJson(String prompt, List<String> fileIds) {
    CompletableFuture<String> cf = new CompletableFuture<>();

    ObjectNode root = om.createObjectNode();
    root.put("model", model);

    ArrayNode input = root.putArray("input");
    ObjectNode userMsg = input.addObject();
    userMsg.put("role", "user");

    ArrayNode content = userMsg.putArray("content");

    ObjectNode text = content.addObject();
    text.put("type", "input_text");
    text.put("text", prompt + "\n\nReturn ONLY valid JSON. No markdown. No extra text.");

    for (String fid : fileIds) {
      ObjectNode f = content.addObject();
      f.put("type", "input_file");
      f.put("file_id", fid);
    }

    final String bodyJson;
    try {
      bodyJson = om.writeValueAsString(root);
    } catch (Exception e) {
      cf.completeExceptionally(e);
      return cf;
    }

    Request reqHttp = new Request.Builder()
        .url(baseUrl + "/responses")
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .post(RequestBody.create(bodyJson, MediaType.parse("application/json")))
        .build();

    http.newCall(reqHttp).enqueue(new Callback() {
      @Override public void onFailure(Call call, IOException e) {
        cf.completeExceptionally(e);
      }

      @Override public void onResponse(Call call, Response resp) throws IOException {
        try (resp) {
          String s = resp.body() != null ? resp.body().string() : "";
          if (!resp.isSuccessful()) {
            cf.completeExceptionally(new RuntimeException("Responses failed: HTTP " + resp.code() + " " + s));
            return;
          }
          cf.complete(s);
        }
      }
    });

    return cf;
  }

  private static String mustGetEnv(String name) {
    String v = System.getenv(name);
    if (v == null || v.isBlank()) throw new IllegalStateException("Missing env var: " + name);
    return v;
  }
}

