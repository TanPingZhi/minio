package com.example.minioapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MinioKafkaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MinioKafkaApplication.class, args);
    }
}
