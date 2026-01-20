package com.example.minioapp.controller;

import com.example.minioapp.service.BatchProcessor;
import com.example.minioapp.service.IngestionService;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZonedDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/batches")
public class IngestionController {

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private BatchProcessor batchProcessor;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFiles(@RequestParam("files") MultipartFile[] files) {
        try {
            String result = ingestionService.processBatch(files);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error processing batch: " + e.getMessage());
        }
    }

    @PostMapping("/reprocess")
    public ResponseEntity<String> reprocess(@RequestParam("start") String startStr,
                                            @RequestParam("end") String endStr) {
        try {
            ZonedDateTime start = ZonedDateTime.parse(startStr);
            ZonedDateTime end = ZonedDateTime.parse(endStr);
            batchProcessor.processRange(start, end);
            return ResponseEntity.ok("Reprocessing started for range: " + start + " to " + end);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Reprocessing failed: " + e.getMessage());
        }
    }
}
