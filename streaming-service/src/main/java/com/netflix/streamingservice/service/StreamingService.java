package com.netflix.streamingservice.service;

import com.netflix.streamingservice.dto.StreamingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.presigned-url-expiry}")
    private long presignedUrlExpiry; // minutes, applies to video segments

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    /*
     * Returns the URL of OUR playlist endpoint for the master playlist.
     * The player loads this, and every playlist it fetches afterwards
     * also comes from our endpoint, so every segment link gets signed.
     */
    public StreamingResponse getStreamingUrl(String movieId, String masterPlaylistKey) {
        log.info("Building streaming url for movie: {}", movieId);
        return new StreamingResponse(
                movieId,
                playlistUrl(movieId, masterPlaylistKey),
                "1080p, 720p, 480p, 360p",
                presignedUrlExpiry
        );
    }

    public String getSignedPlaylist(String movieId, String playlistPath) {
        String prefix = "encoded/" + movieId + "/";

        // Only allow .m3u8 files inside this movie's own folder
        if (!isValidPlaylistPath(prefix, playlistPath)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid playlist path");
        }

        String basePath = playlistPath.substring(0, playlistPath.lastIndexOf('/') + 1);

        String content;
        try {
            content = readFromS3(playlistPath);
        } catch (NoSuchKeyException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Playlist not found");
        }

        return rewritePlaylist(movieId, content, basePath);
    }

    private boolean isValidPlaylistPath(String prefix, String path) {
        return path != null
                && path.startsWith(prefix)
                && path.endsWith(".m3u8")
                && !path.contains("..")
                && !path.contains("\\");
    }

    /*
     * .m3u8 entries -> point back to our /playlist endpoint
     * .ts entries   -> presigned S3 URL
     */
    private String rewritePlaylist(String movieId, String content, String basePath) {
        StringBuilder out = new StringBuilder();

        for (String line : content.split("\\r?\\n")) {
            String trimmed = line.trim();

            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                out.append(line).append('\n');
                continue;
            }

            if (trimmed.contains("..") || trimmed.startsWith("/") || trimmed.contains("://")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsafe playlist entry");
            }

            String fullKey = basePath + trimmed;
            if (trimmed.endsWith(".m3u8")) {
                out.append(playlistUrl(movieId, fullKey));
            } else {
                out.append(presign(fullKey));
            }
            out.append('\n');
        }
        return out.toString();
    }

    private String playlistUrl(String movieId, String key) {
        return publicBaseUrl + "/api/v1/stream/" + movieId + "/playlist?path="
                + URLEncoder.encode(key, StandardCharsets.UTF_8);
    }

    private String presign(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlExpiry))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    // getObjectAsBytes closes the connection for us (the old version leaked it)
    private String readFromS3(String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        return s3Client.getObjectAsBytes(request).asUtf8String();
    }
}