package com.example.minioapp.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketLifecycleArgs;
import io.minio.messages.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class MinioConfig {

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Value("${minio.buckets.tmp}")
    private String tmpBucket;

    @Value("${minio.buckets.prod}")
    private String prodBucket;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean
    public CommandLineRunner minioInitializer(MinioClient minioClient) {
        return args -> {
            System.out.println("Initializing MinIO buckets...");
            try {
                // 1. Tmp Bucket with TTL
                if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(tmpBucket).build())) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(tmpBucket).build());
                    System.out.println("Created bucket: " + tmpBucket);
                    
                    List<LifecycleRule> rules = new ArrayList<>();
                    rules.add(new LifecycleRule(
                            Status.ENABLED,
                            null,
                            new Expiration((ZonedDateTime) null, 7, null),
                            new RuleFilter(""),
                            "expire-7-days",
                            null,
                            null,
                            null));
                            
                    minioClient.setBucketLifecycle(
                            SetBucketLifecycleArgs.builder()
                                    .bucket(tmpBucket)
                                    .config(new LifecycleConfiguration(rules))
                                    .build());
                    System.out.println("Lifecycle policy set for: " + tmpBucket);
                }

                // 2. Prod Bucket
                if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(prodBucket).build())) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(prodBucket).build());
                    System.out.println("Created bucket: " + prodBucket);
                }
            } catch (Exception e) {
                System.err.println("Error initializing buckets: " + e.getMessage());
            }
        };
    }
}
