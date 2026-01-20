package com.example.minioapp.controller;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/batches")
public class IngestionController {

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.buckets.tmp}")
    private String tmpBucket;

    @PostMapping("/{batchId}/upload")
    public ResponseEntity<String> uploadFiles(@PathVariable String batchId,
                                              @RequestParam("files") MultipartFile[] files) {
        StringBuilder result = new StringBuilder();
        try {
            // 1. Upload all files to tmp-bucket
            for (MultipartFile file : files) {
                String objectName = "data/" + batchId + "/" + file.getOriginalFilename();
                try (InputStream is = file.getInputStream()) {
                    minioClient.putObject(
                            PutObjectArgs.builder()
                                    .bucket(tmpBucket)
                                    .object(objectName)
                                    .stream(is, file.getSize(), -1)
                                    .contentType(file.getContentType())
                                    .build());
                }
                result.append("Uploaded: ").append(objectName).append("\n");
            }

            // 2. Implicitly mark as complete
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
            String timePath = now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd/HH"));
            String markerPath = "ready-to-process/" + timePath + "/" + batchId;
            
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(tmpBucket)
                            .object(markerPath)
                            .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                            .build());
            
            result.append("Batch marked complete at: ").append(markerPath);
            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error processing batch: " + e.getMessage());
        }
    }
}
