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
    private IngestionService ingestionService;

    @PostMapping("/{batchId}/upload")
    public ResponseEntity<String> uploadFiles(@PathVariable String batchId,
                                              @RequestParam("files") MultipartFile[] files) {
        try {
            String result = ingestionService.processBatch(batchId, files);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error processing batch: " + e.getMessage());
        }
    }
}
