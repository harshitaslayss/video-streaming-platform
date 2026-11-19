package com.netflix.videoservice.event;

/*
* Event published to Kafka when a video is uploaded to S3.
* Encoding service consumes this to start FFmpeg processing.
*
* TOPIC: video.upload
* */

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VideoUploadedEvent {

    private String movieId;
    private String videoKey;
    private String bucketName;
    private String originalFileName;
    private Long fileSizeBytes;

}
