package com.netflix.streamingservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
* Consumed by kafka topic -> video.encoded
* published by encoding service after ffmpeg processing
*/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class VideoEncodedEvent {
    private String movieId;
    private String hlsUrl;                  // Master playlist URL for streaming
    private String masterPlaylistKey;       // s3 key if master.m3u8
    private boolean success;
    private String errorMessage;
}
