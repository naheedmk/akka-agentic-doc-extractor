package com.example.extractor.util;

import akka.http.javadsl.model.Multipart;
import akka.stream.Materializer;
import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Sink;
import akka.util.ByteString;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class MultipartUtil {

  private MultipartUtil() {}

  // ✅ Single canonical type used by routes
  public static final class ParsedForm {
    public final String prompt;
    public final List<Path> files;

    public ParsedForm(String prompt, List<Path> files) {
      this.prompt = prompt;
      this.files = files;
    }
  }

  private static final class PartResult {
    final String prompt;
    final Path file;
    PartResult(String prompt, Path file) { this.prompt = prompt; this.file = file; }
  }

  public static CompletionStage<ParsedForm> parseOnce(
      Multipart.FormData formData,
      String fileField,
      String promptField,
      Materializer mat
  ) {
    return formData.getParts()
        .mapAsync(1, part -> {
          String name = part.getName();
          String filename = part.getFilename().orElse("(no filename)");
          
          System.out.println("MultipartUtil: Processing part - name: '" + name + "', filename: '" + filename + "'");

          if (promptField.equals(name)) {
            System.out.println("MultipartUtil: Matched prompt field");
            return part.getEntity().getDataBytes()
                .runFold(ByteString.emptyByteString(), ByteString::concat, mat)
                .thenApply(ByteString::utf8String)
                .thenApply(p -> {
                  System.out.println("MultipartUtil: Extracted prompt (length: " + p.length() + ")");
                  return new PartResult(p, null);
                });
          }

          if (fileField.equals(name)) {
            System.out.println("MultipartUtil: Matched file field - filename: '" + filename + "'");
            final Path tmp;
            try {
              tmp = Files.createTempFile("upload-", "-" + sanitize(filename));
              System.out.println("MultipartUtil: Created temp file: " + tmp);
            } catch (IOException e) {
              System.out.println("MultipartUtil: Failed to create temp file: " + e.getMessage());
              return CompletableFuture.failedStage(e);
            }

            return part.getEntity().getDataBytes()
                .runWith(FileIO.toPath(tmp), mat)
                .thenApply(ioResult -> {
                  if (!ioResult.wasSuccessful()) {
                    System.out.println("MultipartUtil: Failed writing temp file: " + ioResult.getError());
                    throw new RuntimeException("Failed writing temp file: " + ioResult.getError());
                  }
                  System.out.println("MultipartUtil: Successfully wrote file to: " + tmp);
                  return new PartResult(null, tmp);
                });
          }

          System.out.println("MultipartUtil: Part ignored - name: '" + name + "' (not prompt or file field)");
          return CompletableFuture.completedStage(new PartResult(null, null));
        })
        .runWith(Sink.seq(), mat)
        .thenApply(results -> {
          String prompt = null;
          List<Path> files = new ArrayList<>();

          System.out.println("MultipartUtil: Processing " + results.size() + " parts");
          int fileCount = 0;
          for (PartResult r : results) {
            if (r.prompt != null && prompt == null) {
              prompt = r.prompt;
              System.out.println("MultipartUtil: Found prompt (length: " + prompt.length() + ")");
            }
            if (r.file != null) {
              files.add(r.file);
              fileCount++;
              System.out.println("MultipartUtil: Found file #" + fileCount + ": " + r.file.getFileName());
            }
          }
          System.out.println("MultipartUtil: Total files parsed: " + files.size());
          return new ParsedForm(prompt, files);
        });
  }

  private static String sanitize(String s) {
    return s.replaceAll("[^a-zA-Z0-9._-]", "_");
  }
}

