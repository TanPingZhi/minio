package com.example.minioapp.service;

import com.example.minioapp.model.MetadataContent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IngestionService {

    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;

    @Value("${minio.buckets.tmp}")
    private String tmpBucket;

    public String processBatch(MultipartFile[] files) throws Exception {
        String batchId = UUID.randomUUID().toString();
        StringBuilder result = new StringBuilder();
        result.append("Batch ID: ").append(batchId).append("\n");

        // 1. Upload all files and generate metadata
        for (MultipartFile file : files) {
            String originalFilename = file.getOriginalFilename();
            String objectName = "data/" + batchId + "/" + originalFilename;
            
            // Upload original file
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

            // Generate metadata files for Topic Alpha and Beta
            MetadataContent metadata = new MetadataContent(originalFilename, batchId);
            byte[] metaBytes = objectMapper.writeValueAsBytes(metadata);

            // Meta 1 -> Topic Alpha
            String meta1Path = "data/" + batchId + "/" + originalFilename + "-meta1.json";
            uploadBytes(meta1Path, metaBytes, "application/json");
            
            // Meta 2 -> Topic Beta
            String meta2Path = "data/" + batchId + "/" + originalFilename + "-meta2.json";
            uploadBytes(meta2Path, metaBytes, "application/json");

            result.append("Generated Metadata: ").append(meta1Path).append(", ").append(meta2Path).append("\n");
        }

        // 2. Implicitly mark as complete
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        String timePath = now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd/HH"));
        String markerPath = "ready-to-process/" + timePath + "/" + batchId;

        uploadBytes(markerPath, new byte[0], "application/octet-stream");

        result.append("Batch marked complete at: ").append(markerPath);
        return result.toString();
    }

    private void uploadBytes(String objectName, byte[] content, String contentType) throws Exception {
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(tmpBucket)
                        .object(objectName)
                        .stream(new ByteArrayInputStream(content), content.length, -1)
                        .contentType(contentType)
                        .build());
    }
}
