# Video Streaming Platform

A Netflix-inspired video streaming platform built using **Java, Spring Boot microservices, Apache Kafka, AWS S3, Redis, MySQL, FFmpeg, and Docker**.

The platform separates content management, video uploads, video processing, and streaming into independent services. Kafka is used for asynchronous communication between services, allowing resource-intensive video processing to happen independently from the upload workflow.

## Architecture

```text
                         ┌─────────────────────┐
                         │       Client        │
                         └──────────┬──────────┘
                                    │
                    ┌───────────────┴───────────────┐
                    │                               │
                    ▼                               ▼
          ┌──────────────────┐           ┌──────────────────┐
          │ Content Service  │           │  Video Service   │
          │                  │           │                  │
          │ Movie Metadata   │           │ Video Upload     │
          │ Search / Genres  │           │ Upload Workflow  │
          └────────┬─────────┘           └────────┬─────────┘
                   │                              │
                   ▼                              ▼
                MySQL                         AWS S3
                                                  │
                                                  ▼
                                        ┌──────────────────┐
                                        │      Kafka       │
                                        │ video.uploaded   │
                                        └────────┬─────────┘
                                                 │
                                                 ▼
                                        ┌──────────────────┐
                                        │ Encoding Service │
                                        │                  │
                                        │ Kafka Consumer   │
                                        │ FFmpeg Processing│
                                        └────────┬─────────┘
                                                 │
                                                 ▼
                                              AWS S3
                                                 │
                                                 ▼
                                        ┌──────────────────┐
                                        │      Kafka       │
                                        │  video.encoded   │
                                        └────────┬─────────┘
                                                 │
                               ┌─────────────────┴─────────────────┐
                               │                                   │
                               ▼                                   ▼
                     ┌──────────────────┐              ┌──────────────────┐
                     │ Content Service  │              │ Streaming Service│
                     │ Update Metadata  │              │ Video Playback   │
                     └──────────────────┘              │ Redis Cache      │
                                                       └──────────────────┘
```

## System Overview

The platform implements an asynchronous video-processing pipeline:

```text
Video Upload
     │
     ▼
Video Service
     │
     ▼
AWS S3
     │
     ▼
Kafka: video.uploaded
     │
     ▼
Encoding Service
     │
     ▼
FFmpeg Processing
     │
     ▼
Processed Video → AWS S3
     │
     ▼
Kafka: video.encoded
     │
     ▼
Streaming Service
     │
     ▼
Video Playback
```

This architecture decouples video processing from the initial upload request and allows the encoding workload to operate independently.

## Microservices

| Service | Responsibility | Port |
|---|---|---:|
| Content Service | Movie metadata, catalog, search, and genre APIs | 8081 |
| Video Service | Video uploads and event publishing | 8082 |
| Encoding Service | Asynchronous video processing using FFmpeg | 8083 |
| Streaming Service | Video retrieval and streaming operations | 8084 |

### Content Service

Manages the movie catalog and associated metadata.

**Responsibilities:**
- Create and manage movie metadata
- Search movies
- Filter movies by genre
- Persist catalog data in MySQL
- Process relevant video-processing events

### Video Service

Handles video uploads and initiates the asynchronous processing pipeline.

**Responsibilities:**
- Accept video uploads
- Store video objects in AWS S3
- Publish `video.uploaded` events
- Connect uploaded videos with movie metadata

### Encoding Service

Processes uploaded videos asynchronously after consuming Kafka events.

**Responsibilities:**
- Consume `video.uploaded` events
- Process videos using FFmpeg
- Store processed video assets in AWS S3
- Publish `video.encoded` events

### Streaming Service

Provides access to processed video content.

**Responsibilities:**
- Handle streaming requests
- Retrieve processed video resources
- Use Redis as a caching layer
- Provide streaming-related APIs

## Event-Driven Processing

Apache Kafka connects the services through asynchronous events.

### `video.uploaded`

Published by the Video Service after a video upload and consumed by downstream processing components.

```text
Video Service
      │
      │ video.uploaded
      ▼
    Kafka
      │
      ▼
Encoding Service
```

### `video.encoded`

Published after the encoding workflow completes and consumed by downstream services.

```text
Encoding Service
      │
      │ video.encoded
      ▼
    Kafka
      │
      ├──────────────► Content Service
      │
      └──────────────► Streaming Service
```

This event-driven design prevents long-running video processing from blocking synchronous API requests.

## Video Processing

The Encoding Service uses FFmpeg to process uploaded video files before storing the resulting assets in AWS S3.

The processing workflow is:

```text
Raw Video
    │
    ▼
FFmpeg
    │
    ├── Video Processing
    └── Output Generation
             │
             ▼
          AWS S3
```

Video processing is isolated in its own service so that it can be independently scaled from the API-facing services.

## AWS S3

AWS S3 is used for video object storage.

The application reads AWS credentials from environment variables rather than storing credentials directly in source code.

Example configuration:

```properties
aws.access-key=${AWS_ACCESS_KEY}
aws.secret-key=${AWS_SECRET_KEY}
```

Configure the required AWS credentials and bucket settings before starting the services.

> Do not commit AWS credentials, `.env` files, or access keys to the repository.

## Redis

Redis is used by the Streaming Service as a caching layer for frequently accessed streaming information.

This reduces repeated access to the underlying persistence or storage layer for data that can be efficiently cached.

## Database

MySQL is used for persistent application data, including movie and content metadata.

```text
Content Service
      │
      ▼
    MySQL
```

## Technology Stack

### Backend

- Java
- Spring Boot
- Spring Web
- Spring Data
- Maven

### Architecture and Communication

- Microservices
- REST APIs
- Apache Kafka
- Event-driven architecture

### Storage

- MySQL
- AWS S3
- Redis

### Video Processing

- FFmpeg

### Infrastructure

- Docker
- Docker Compose

## Running the Project

### Prerequisites

Install the following:

- Java
- Maven
- Docker Desktop
- FFmpeg
- AWS account with an S3 bucket

Verify the installations:

```bash
java -version
mvn -version
docker --version
ffmpeg -version
```

### 1. Clone the Repository

```bash
git clone https://github.com/harshitaslayss/video-streaming-platform.git

cd video-streaming-platform
```

### 2. Configure AWS

Set the required environment variables:

```text
AWS_ACCESS_KEY=your_access_key
AWS_SECRET_KEY=your_secret_key
```

Configure the AWS region and S3 bucket according to the application's configuration.

Do not commit credentials to Git.

### 3. Start Infrastructure

```bash
docker compose up -d
```

This starts the supporting infrastructure:

- MySQL
- Redis
- Kafka
- ZooKeeper

### 4. Start the Services

Run each Spring Boot service independently.

#### Content Service

```bash
cd content-service
mvn spring-boot:run
```

#### Video Service

```bash
cd video-service
mvn spring-boot:run
```

#### Encoding Service

```bash
cd encoding-service
mvn spring-boot:run
```

#### Streaming Service

```bash
cd streaming-service
mvn spring-boot:run
```

## End-to-End Workflow

The complete video workflow can be tested using the following sequence:

```text
1. Create movie metadata
        │
        ▼
2. Upload video
        │
        ▼
3. Video Service publishes video.uploaded
        │
        ▼
4. Encoding Service consumes event
        │
        ▼
5. FFmpeg processes the video
        │
        ▼
6. Processed assets are stored in S3
        │
        ▼
7. Encoding Service publishes video.encoded
        │
        ▼
8. Downstream services update video state
        │
        ▼
9. Request streaming information
        │
        ▼
10. Stream the processed video
```

## API Endpoints

### Content Service

```text
POST /api/v1/movies
GET  /api/v1/movies
GET  /api/v1/movies/{id}
GET  /api/v1/movies/genre/{genre}
GET  /api/v1/movies/search
```

### Video Service

```text
POST /api/v1/videos/upload/{movieId}
```

### Streaming Service

```text
GET /api/v1/stream/{movieId}
```

Additional endpoints are available in the individual service controllers.

## Project Structure

```text
video-streaming-platform/
│
├── content-service/
│   ├── src/
│   └── pom.xml
│
├── video-service/
│   ├── src/
│   └── pom.xml
│
├── encoding-service/
│   ├── src/
│   └── pom.xml
│
├── streaming-service/
│   ├── src/
│   └── pom.xml
│
├── docker-compose.yml
└── README.md
```

## Key Backend Concepts

This project demonstrates:

- Microservice architecture
- RESTful API development
- Asynchronous event-driven communication
- Apache Kafka producers and consumers
- Video upload and processing workflows
- FFmpeg-based video processing
- AWS S3 object storage
- Redis caching
- MySQL persistence
- Dockerized infrastructure
- Service-to-service communication
- Separation of responsibilities across backend services

## Design Decisions

### Why Microservices?

Video uploads, encoding, content management, and streaming have different responsibilities and resource requirements. Separating them allows each component to evolve and scale independently.

### Why Kafka?

Video encoding is a potentially long-running operation. Kafka allows the upload workflow to publish an event and return control without requiring the upload service to synchronously execute the entire encoding pipeline.

### Why S3?

Video files can be significantly larger than typical application data. Object storage provides a dedicated storage layer for these large media assets while keeping application services focused on business logic.

### Why Redis?

Streaming-related data can be requested frequently. Redis provides a fast caching layer for data that does not need to be retrieved repeatedly from the primary persistence layer.

## Future Improvements

Potential improvements for a production deployment include:

- Kafka retry and dead-letter queues
- Idempotent event processing
- Distributed tracing
- Centralized configuration
- Automated integration testing
- API Gateway
- Service discovery
- Authentication and authorization
- Application monitoring and observability
- Horizontal scaling of encoding workers

## Author

**Harshita Sharma**

B.Tech Computer Science Engineering  
Indira Gandhi Delhi Technical University for Women, Delhi

GitHub: https://github.com/harshitaslayss
