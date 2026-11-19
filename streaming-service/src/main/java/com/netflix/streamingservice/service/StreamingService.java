package com.netflix.streamingservice.service;

import com.netflix.streamingservice.dto.StreamingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingService {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final RedisTemplate<String,String> redisTemplate;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.presigned-url-expiry}")
    private long presignedUrlExpiry; //60 minutes

    //Redis key for caching streaming Urls
    private static final String STREAMING_URL_CACHING_PREFIX= "streaming:url";

    /*
    * Get streaming URL for a movie
    * FLOW:
    *
    * 1. check redis cache for existing presigned URL
    * 2. if cached- return immediately
    * 3. if not cached- generate new presigned url from s3
    * 4. cache the url in redis
    * 5. return the streaming url
    *
    * why presigned url-> s3 bucket is private locker room- videos are not publicly accessible
    * presigned url gives temp access (X minutes)
    * prevent unauthorised access and video downloads
    *
    */
    public StreamingResponse getStreamingUrl(String movieId, String playlistKey) {
        log.info("get streaming url for movie: {}", movieId);
        String cacheKey= STREAMING_URL_CACHING_PREFIX+ movieId;

        //check redis cache first
        String cachedUrl= redisTemplate.opsForValue().get(cacheKey);
        if(cachedUrl!=null){
            log.info("returning streaming url for movie: {}",movieId);
            return new StreamingResponse(movieId,
                    cachedUrl,
                    "1080p, 720p, 480p, 360p",
                    presignedUrlExpiry);
        }

        //generate presigned url for s3
        log.info("Generating presigned Url for movie: {}",movieId);
        String presignedUrl= generatePresignedUrl(playlistKey);

        // cache in redis for 55 minutes
        // (5 minutes less than actual expiry to avoid edge cases)
        redisTemplate.opsForValue().set(
                cacheKey,
                presignedUrl,
                55,
                TimeUnit.MINUTES
        );

        log.info("Streaming url generated and cached for movie: {}",movieId);
        return new StreamingResponse(
                movieId,
                presignedUrl,
                "1080p, 720p, 480p, 360p",
                presignedUrlExpiry
        );


    }

    /*
    * Generate a presigned URL for S3 object
    * it expires after the allotted time.
    */
    private String generatePresignedUrl(String playlistKey) {
        GetObjectRequest getObjectRequest= GetObjectRequest.builder()
                .bucket(bucketName)
                .key(playlistKey)
                .build();

        GetObjectPresignRequest getObjectPresignRequest= GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlExpiry))
                .getObjectRequest(getObjectRequest).build();

        return s3Presigner.presignGetObject(getObjectPresignRequest)
                .url()
                .toString();

    }

    private String rewriteM3u8SignedUrls(String m3u8Content, String basePath){
        StringBuilder rewritten= new StringBuilder();
        for(String line: m3u8Content.split("\n")){
            String trimmed= line.trim();

            //skip empty lines and comments
            if(trimmed.isEmpty() || trimmed.startsWith("#")){
                rewritten.append(line).append('\n');
                continue;
            }

            //this is a segment or playlist reference
            //build full s3 key and sign it

            String fullKey= basePath+trimmed;
            String signedUrl= generatePresignedUrl(fullKey);

            rewritten.append(signedUrl).append('\n');
        }
        return rewritten.toString();
    }

    /*
    * Read file content from S3
    */

    private String readFromS3(String s3Key){
        GetObjectRequest request= GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();
        ResponseInputStream<GetObjectResponse> response= s3Client.getObject(request);

        return new BufferedReader(new InputStreamReader(response)).lines()
                .collect(Collectors.joining("\n"));
    }

    public String getSignedPlaylist(String movieId, String playlistPath){

        //get base path for this playlist
        String basePath= playlistPath.substring(
                0, playlistPath.lastIndexOf('/')+1
        );

        //read m3u8 content from s3
        String m3u8Content= readFromS3(playlistPath);

        //rewrite each line that is a segment or playlist reference
        String signedContent= rewriteM3u8SignedUrls(m3u8Content,basePath);
        return signedContent;
    }

    /*
    * Invalidate cache streaming url
    * Called when video is re-encoded or updated
    * */

    public void invalidateString(String movieId){
        String cacheKey= STREAMING_URL_CACHING_PREFIX+movieId;
        redisTemplate.delete(cacheKey);

        log.info("Streaming URL cache invalidated for movie: {}",movieId);
    }
}
