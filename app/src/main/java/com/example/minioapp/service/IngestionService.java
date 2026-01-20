package com.example.minioapp.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Service
public class IngestionService {

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.buckets.tmp}")
    private String tmpBucket;

    public String processBatch(String batchId, MultipartFile[] files) throws Exception {
        StringBuilder result = new StringBuilder();

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
        return result.toString();
    }
}
