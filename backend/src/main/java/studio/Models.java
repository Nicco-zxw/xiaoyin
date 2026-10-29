package studio;

import java.time.Instant;
import java.util.*;

public final class Models {
 private Models() {}
 public record User(String id,String username,String passwordHash,Instant createdAt) {}
 public record Category(String id,String name,int sortOrder) {}
 public record Template(String id,String categoryId,String title,String description,String coverUrl,int requiredImageCount,long maxFileSizeBytes) {
   public List<String> getAcceptedMimeTypes() { return List.of("image/jpeg","image/png"); }
 }
 public record Asset(String id,String userId,String path,String mimeType,long sizeBytes,int width,int height,Instant createdAt) {}
 public record Task(String id,String userId,String templateId,String templateTitle,String status,String currentStage,String failureCode,String failureMessage,String resultPath,String simulationJson,Instant createdAt,Instant startedAt,Instant finishedAt) {}
 public record Step(String stage,String status,Instant startedAt,Instant finishedAt) {}
 public record Simulation(Map<String,Integer> stageDelayMs,String failAtStage) {
   public static Simulation defaults() { return new Simulation(Map.of("MATERIAL_CHECK",1000,"MOCK_GENERATION",6000,"RESULT_PACKAGING",1000),null); }
 }
}
