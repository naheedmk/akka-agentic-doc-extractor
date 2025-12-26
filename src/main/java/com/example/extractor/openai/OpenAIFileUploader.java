package com.example.extractor.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.time.Duration;

/**
 * OpenAI file uploader utility.
 * Note: Currently not used by the new Akka Java SDK Agent architecture,
 * but kept as a utility for potential future use.
 */
public class OpenAIFileUploader {
  private final OkHttpClient http = new OkHttpClient.Builder()
    .connectTimeout(Duration.ofSeconds(30))
    .readTimeout(Duration.ofSeconds(600))
    .writeTimeout(Duration.ofSeconds(600))
    .callTimeout(Duration.ofSeconds(900))
    .protocols(List.of(Protocol.HTTP_1_1))
    .build();
  private final ObjectMapper om = new ObjectMapper();
  private final String baseUrl;
  private final String apiKey;

  public OpenAIFileUploader(String baseUrl) {
    this.baseUrl = baseUrl;
    this.apiKey = mustGetEnv("OPENAI_API_KEY");
  }

  public CompletionStage<List<String>> uploadAll(List<Path> paths) {
    List<CompletableFuture<String>> futures = new ArrayList<>();
    for (Path p : paths) futures.add(uploadOne(p).toCompletableFuture());
    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .thenApply(v -> futures.stream().map(CompletableFuture::join).toList());
  }

  public CompletionStage<String> uploadOne(Path filePath) {
    CompletableFuture<String> cf = new CompletableFuture<>();

    RequestBody fileBody = RequestBody.create(filePath.toFile(), MediaType.parse("application/octet-stream"));

    MultipartBody body = new MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("purpose", "user_data")
        .addFormDataPart("file", filePath.getFileName().toString(), fileBody)
        .build();

    Request req = new Request.Builder()
        .url(baseUrl + "/files")
        .header("Authorization", "Bearer " + apiKey)
        .post(body)
        .build();

    http.newCall(req).enqueue(new Callback() {
      @Override public void onFailure(Call call, IOException e) {
        cf.completeExceptionally(e);
      }

      @Override public void onResponse(Call call, Response resp) throws IOException {
        try (resp) {
          String s = resp.body() != null ? resp.body().string() : "";
          if (!resp.isSuccessful()) {
            cf.completeExceptionally(new RuntimeException("Files upload failed: HTTP " + resp.code() + " " + s));
            return;
          }
          JsonNode json = om.readTree(s);
          String fileId = json.get("id").asText();
          cf.complete(fileId);
        }
      }
    });

    return cf;
  }

  private static String mustGetEnv(String name) {
    String v = System.getenv(name);
    if (v == null || v.isBlank()) {
      throw new IllegalStateException("Missing env var: " + name);
    }
    return v;
  }
}

