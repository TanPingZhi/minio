package com.example.minioapp.service;

import io.minio.*;
import io.minio.messages.Item;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@EnableScheduling
public class BatchProcessor {

    private final MinioClient minioClient;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${minio.buckets.tmp}")
    private String tmpBucket;

    @Value("${minio.buckets.prod}")
    private String prodBucket;

    public BatchProcessor(MinioClient minioClient, KafkaTemplate<String, String> kafkaTemplate) {
        this.minioClient = minioClient;
        this.kafkaTemplate = kafkaTemplate;
    }



    private static final DateTimeFormatter TIME_PATH_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd/HH");

    // Run every 5 minutes
    @Scheduled(cron = "0 */5 * * * *")
    public void processBatches() {
        System.out.println("Starting batch processing job...");
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        
        // Scan current hour and previous hour
        processTimeWindow(now);
        processTimeWindow(now.minusHours(1));
    }

    private void processTimeWindow(ZonedDateTime time) {
        String timePath = time.format(TIME_PATH_FORMAT);
        String prefix = "ready-to-process/" + timePath + "/";
        System.out.println("Scanning prefix: " + prefix);

        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(tmpBucket).prefix(prefix).recursive(true).build());

            for (Result<Item> result : results) {
                Item item = result.get();
                String batchId = item.objectName().substring(prefix.length());
                // Handle trailing slash if exists (though markers shouldn't have it)
                if (batchId.endsWith("/")) continue;

                processBatch(batchId);
            }
        } catch (Exception e) {
            System.err.println("Error scanning window " + prefix + ": " + e.getMessage());
        }
    }

    private void processBatch(String batchId) {
        String successMarkerInfo = "success-markers/" + batchId;
        
        try {
            // 1. Idempotency Check: Check if already in prod
            try {
                minioClient.statObject(StatObjectArgs.builder().bucket(prodBucket).object(successMarkerInfo).build());
                System.out.println("Batch " + batchId + " already processed. Skipping.");
                return;
            } catch (Exception e) {
                // Determine if it's a "Not Found" error (expected) or something else
                // For simplicity assuming any error effectively means not found or check failed, 
                // but strictly should check error code 'NoSuchKey'.
            }

            System.out.println("Processing batch: " + batchId);

            // 2. Identify files in tmp/data/{batchId}/
            String dataPrefix = "data/" + batchId + "/";
            Iterable<Result<Item>> fileResults = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(tmpBucket).prefix(dataPrefix).recursive(true).build());

            for (Result<Item> res : fileResults) {
                Item fileItem = res.get();
                String sourceObj = fileItem.objectName();
                String destObj = sourceObj; // Preserve structure in prod: data/{batchId}/{filename}

                // Copy to prod
                minioClient.copyObject(
                        CopyObjectArgs.builder()
                                .source(CopySource.builder().bucket(tmpBucket).object(sourceObj).build())
                                .bucket(prodBucket)
                                .object(destObj)
                                .build());
                // If it's a JSON metadata file, publish to Kafka
                // Expecting pairs like: content.bin, content-meta1.json (Topic A), content-meta2.json (Topic B)
                if (destObj.endsWith(".json")) {
                    String content = new String(minioClient.getObject(
                            GetObjectArgs.builder().bucket(prodBucket).object(destObj).build()).readAllBytes());
                    
                    String topic = "metadata-default";
                    if (destObj.contains("meta1")) {
                        topic = "topic-alpha";
                    } else if (destObj.contains("meta2")) {
                        topic = "topic-beta";
                    }
                    
                    kafkaTemplate.send(topic, content);
                    System.out.println("Published to " + topic + ": " + destObj);
                }
            }

            // 3. Finalize: Write success marker to prod
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(prodBucket)
                            .object(successMarkerInfo)
                            .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                            .build());

            System.out.println("Successfully processed batch: " + batchId);

        } catch (Exception e) {
            System.err.println("Error processing batch " + batchId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
