package com.example.minioapp.controller;

import com.example.minioapp.service.IngestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/batches")
public class IngestionController {

    @Autowired
    private IngestionService ingestionService;

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
}
