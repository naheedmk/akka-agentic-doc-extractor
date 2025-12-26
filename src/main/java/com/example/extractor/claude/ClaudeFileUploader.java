package com.example.extractor.claude;

import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Claude file uploader utility.
 * Note: Currently not used by the new Akka Java SDK Agent architecture,
 * but kept as a utility for potential future use.
 */
public class ClaudeFileUploader {

  private final OkHttpClient http = new OkHttpClient.Builder()
      .connectTimeout(Duration.ofSeconds(30))
      .readTimeout(Duration.ofSeconds(180))
      .writeTimeout(Duration.ofSeconds(180))
      .callTimeout(Duration.ofSeconds(240))
      .build();

  private final String apiKey = mustGetEnv("ANTHROPIC_API_KEY");
  private final String baseUrl;

  public ClaudeFileUploader(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public CompletionStage<String> uploadOne(Path path) {
    return upload(path);
  }

  public CompletionStage<String> upload(Path path) {
    CompletableFuture<String> cf = new CompletableFuture<>();
    File f = path.toFile();

    MediaType mt = MediaType.parse(detectMime(f.getName()));
    RequestBody fileBody = RequestBody.create(f, mt);

    RequestBody multipart = new MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("file", f.getName(), fileBody)
        .build();

    Request req = new Request.Builder()
        .url(baseUrl + "/files")
        .header("x-api-key", apiKey)
        .header("anthropic-version", "2023-06-01")
        .header("anthropic-beta", "files-api-2025-04-14")
        .post(multipart)
        .build();

    http.newCall(req).enqueue(new Callback() {
      @Override public void onFailure(Call call, IOException e) {
        cf.completeExceptionally(e);
      }

      @Override public void onResponse(Call call, Response resp) throws IOException {
        try (resp) {
          String body = resp.body() != null ? resp.body().string() : "";
          if (!resp.isSuccessful()) {
            cf.completeExceptionally(
                new RuntimeException("Claude file upload failed: HTTP " + resp.code() + " " + body)
            );
            return;
          }

          // Response JSON includes: {"id":"file_..."}
          String id = extractJsonField(body, "id");
          if (id == null) cf.completeExceptionally(new RuntimeException("No file id in response: " + body));
          else cf.complete(id);
        }
      }
    });

    return cf;
  }

  public CompletionStage<List<String>> uploadAll(List<Path> paths) {
    CompletableFuture<List<String>> out = new CompletableFuture<>();
    List<CompletableFuture<String>> futures = new ArrayList<>();

    for (Path p : paths) {
      futures.add(upload(p).toCompletableFuture());
    }

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .whenComplete((v, ex) -> {
          if (ex != null) out.completeExceptionally(ex);
          else out.complete(futures.stream().map(CompletableFuture::join).toList());
        });

    return out;
  }

  private static String detectMime(String filename) {
    String n = filename.toLowerCase();
    if (n.endsWith(".pdf")) return "application/pdf";
    if (n.endsWith(".txt")) return "text/plain";
    if (n.endsWith(".png")) return "image/png";
    if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
    if (n.endsWith(".webp")) return "image/webp";
    return "application/octet-stream";
  }

  private static String mustGetEnv(String name) {
    String v = System.getenv(name);
    if (v == null || v.isBlank()) throw new IllegalStateException("Missing env var: " + name);
    return v;
  }

  // minimal JSON field extraction (you already have Jackson—fine to replace this later)
  private static String extractJsonField(String json, String field) {
    String needle = "\"" + field + "\":";
    int i = json.indexOf(needle);
    if (i < 0) return null;
    int q1 = json.indexOf('"', i + needle.length());
    int q2 = json.indexOf('"', q1 + 1);
    if (q1 < 0 || q2 < 0) return null;
    return json.substring(q1 + 1, q2);
  }
}

