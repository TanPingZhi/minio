# Technical Reference: Stateless Proxy Upload Gateway

## 1. System Overview

This document details the architecture of the **Stateless Proxy Upload Gateway**, a high-volume ingestion service designed to handle ~2 million uploads/week without a database or persistent queue. Ideally suited for handling bursts of large files with associated metadata.

### Core Technologies

- **Language**: Java 17
- **Framework**: Spring Boot 3.0.0
- **Storage**: MinIO (S3 Compatible)
- **Messaging**: Apache Kafka (accessed via `spring-kafka`)
- **Containerization**: Docker

## 2. Design Pattern: Time-Partitioned State Machine

To avoid the need for a database to track the state of millions of concurrent uploads, we utilize the **FileSystem as the State Machine**, specifically leveraging MinIO's folder structure partitioned by time.

### 2.1 Storage Taxonomy

We employ two distinct buckets to separate transient state from permanent truth:

#### Temporary Bucket (`tmp-bucket`)

- **Purpose**: Temporary staging area for incoming data and "ready" signals.
- **Data Path**: `data/{batch-id}/{filename}`
- **Signal Path**: `ready-to-process/{YYYY}/{MM}/{DD}/{HH}/{batch-id}`
- **Lifecycle**: Objects are deleted after 7 days (via ILM policy).

#### Production Bucket (`prod-bucket`)

- **Purpose**: Permanent storage for successfully processed data.
- **Data Path**: `data/{batch-id}/{filename}`
- **State Path**: `success-markers/{batch-id}` (0-byte file indicating completion)

### 2.2 Workflow

1. **Ingestion (API Layer)**

   * Client uploads files to `tmp-bucket/data/{batch-id}/`.
   * Client calls `POST /api/batches/{batch-id}/complete`.
   * Server calculates **Current UTC Hour**.
   * Server writes a 0-byte marker to `tmp-bucket/ready-to-process/{YYYY}/{MM}/{DD}/{HH}/{batch-id}`.
2. **Processing (Background Worker)**

   * A `@Scheduled` task runs every **5 minutes**.
   * **Scanning**: It lists objects in `tmp-bucket/ready-to-process/` for the **Current Hour** and **Previous Hour**.
     * *Optimization*: Scanning just 2 hours of markers reduces the search space from millions (7 days) to thousands, enabling fast, stateless discovery.
   * **Idempotency Check**: For each marker, it checks if `prod-bucket/success-markers/{batch-id}` exists.
     * If EXISTS: Skip (Already processed).
     * If MISSING: Process.
3. **Execution (Process Batch)**

   * **Copy**: Files are copied from `tmp-bucket` to `prod-bucket`.
   * **Publish**: Metadata (JSON files) is read and published to Kafka using `spring-kafka`.
   * **Finalize**: A success marker is written to `prod-bucket`.

## 3. Implementation Details

### 3.1 Spring Kafka Integration

We leverage the `spring-kafka` library for robust messaging support.

**Dependency:**

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

**Configuration (`application.yml`):**

```yaml
spring:
  kafka:
    bootstrap-servers: kafka:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
```

**Usage:**
The `KafkaTemplate<String, String>` bean is injected into our services (via constructor injection) to send messages.

### 3.2 Topic Management

Topics are automatically created on startup via `KafkaConfig`:

* `metadata-default`
* `topic-alpha`
* `topic-beta`

### 3.2 Resilience & Recovery

* **Statelessness**: The application server holds no state. It can crash and restart without data loss.
* **Crash Recovery**: If the cron job crashes mid-processing, the next run (5 mins later) will re-scan the same time window. The idempotency check ensures no duplicates are created in Kafka or Prod.
* **Race Conditions**: "Lookback Strategy" (scanning previous hour) safeguards against edge cases where an upload completes at `XX:59:59` and the cron job starts at `XX+1:00:00`.

## 4. API Endpoints

### `POST /api/batches/{batchId}/upload`

* **Consumes**: `multipart/form-data`
* **Parameters**: `files` (Array of MultipartFile)
* **Action**: Streams files to `tmp-bucket/data/{batchId}/`. Supports bulk upload of content and metadata.

### `POST /api/batches/{batchId}/complete`

* **Action**: Writes the time-partitioned marker to `tmp-bucket/ready-to-process/...`.
