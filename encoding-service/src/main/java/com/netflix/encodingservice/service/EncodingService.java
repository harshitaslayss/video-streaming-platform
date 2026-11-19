package com.netflix.encodingservice.service;

import com.netflix.encodingservice.event.VideoEncodedEvent;
import com.netflix.encodingservice.event.VideoUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class EncodingService {

    private final S3Client s3Client;
    private final KafkaTemplate<String, VideoEncodedEvent> kafkaTemplate;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${ffmpeg.path}")
    private String ffmpegPath;

    @Value("${encoding.base-path}")
    private String basePath;

    private static final String VIDEO_ENCODED_TOPIC= "video.encoded";

    //Video qualities to encode
    //Format: resolution, bitrate and height

    private static final List<int[]> VIDEO_QUALITIES= Arrays.asList(
            new int[]{1920,5000,1080}, //1080 - 5000k bitrate
            new int[]{1280,2800,720}, //720 - 2800k bitrate
            new int[]{854,1200,480}, //480 - 1200k bitrate
            new int[]{640,800,360} //360 - 800k bitrate
    );

    /*
    * Main encoding pipeline:
    * 1. Download raw video from S3
    * 2. Encode to multiple qualities using FFmpeg
    * 3. Generate HLS playlist (.m3u8) for each quality
    * 4. Create master playlist
    * 5. Upload all encoded files back to S3.
    * 6. publish VideoEncodedEvent to Kafka
    * */

    public void encodeVideo(VideoUploadedEvent event){
    log.info("Starting encoding platform for movie: {}",event.getMovieId());

        //create a unique path for movie
        String jobPath= basePath+"/"+event.getMovieId();
        try {
            //create temp directories:
            Files.createDirectories(Paths.get(jobPath));
            Files.createDirectories(Paths.get(jobPath+"/encoded"));

            //step 1: download raw file from s3
            String localVideoPath= jobPath+"/raw_video.mp4";
            downloadFromS3(event.getVideoKey(),localVideoPath);
            log.info("Raw video downloaded to {}",localVideoPath);

            //step 2 and 3: encode to multiple qualities + generate HLS
            for(int[] qualities: VIDEO_QUALITIES){
                int width= qualities[0];
                int bitrate= qualities[1];
                int height= qualities[2];

                String qualityDir= jobPath+"/encoded/"+height+"p";
                Files.createDirectories(Paths.get(qualityDir));

                encodeToHLS(localVideoPath,qualityDir,height,width,bitrate);
                log.info("Encoded {}p successfully",height);
            }

            //step 4: generate master playlist
            String masterPlaylistPath= jobPath+"/encoded/master.m3u8";
            generateMasterPlaylist(masterPlaylistPath);
            log.info("Master playlist generated.");

            //step 5: upload all resources files to S3
            String encodedPrefix= "encoded/"+ event.getMovieId()+"/";
            uploadEncodedFilesToS3(jobPath+"/encoded/",encodedPrefix);
            log.info("All encoded files uploaded to S3");

            //step 6: publish VideoEncodedEvent
            String masterPlaylistKey= encodedPrefix+"master.m3u8";
            String hlsUrl= "https://"+bucketName+".s3.amazonaws.com/"+masterPlaylistKey;

            VideoEncodedEvent videoEncodedEvent= new VideoEncodedEvent(
                    event.getMovieId(),
                    hlsUrl,
                    masterPlaylistKey,
                    true,
                    null
            );

            kafkaTemplate.send(VIDEO_ENCODED_TOPIC,event.getVideoKey(),videoEncodedEvent);
            log.info("VideoEncodedEvent published for movie: {}",event.getMovieId());


        }catch (Exception e){
            log.error("Encoding failed for movie: {}- {}",event.getMovieId(), e.getMessage());
            //Publish failure event
            VideoEncodedEvent failureEvent= new VideoEncodedEvent(
                    event.getMovieId(),
                    null,
                    null,
                    false,
                    e.getMessage()
            );

            kafkaTemplate.send(VIDEO_ENCODED_TOPIC,event.getMovieId(),failureEvent);
        }finally{
            //cleanup temp files
            cleanupTempFiles(jobPath);
        }

    }

    private void downloadFromS3(String s3key, String localVideoPath) {
        GetObjectRequest getObjectRequest= GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3key)
                .build();

        s3Client.getObject(getObjectRequest,Paths.get(localVideoPath));
    }

    /*
    * Encoding raw video file to HLS using FFmpeg
    *
    * FFmpeg command creates:
    * 1. Multiple .ts segments of 10 sec each
    * 2. A m3u8 playlist file of the same quality (height/width/bitrate)
    *
    * */
    private void encodeToHLS(String inputPath, String outputDir, int height, int width, int bitrate) throws InterruptedException, IOException {

        String playlistPath= outputDir+"/playlist.m3u8";
        String segmentPattern= outputDir+"/segment_%03d.ts";

        //ffmpeg command for encoding

        List<String> command= Arrays.asList(
                ffmpegPath,
                "-i",inputPath,
                "-vf", //stands for video filters and is followed by commands u need to put on the video/file
                "scale="+width+":"+height,//scale to resolution
                "-c:v","libx264" ,//video codex/encoder to choose
                "-b:v", bitrate+"k", //video bitrate in kbps
                "-c:a","aac",//codex for audio -> AAC
                "-b:a","128k",//audio bitrate
                "-hls_time","10",//length of each segment in seconds
                "-hls_list_size","0", //keep all segments
                "-hls_segment_filename",segmentPattern, //url for each segment
                "-f","hls" //format output to HLS
                ,playlistPath //output playlist

        );

        ProcessBuilder processBuilder= new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        processBuilder.inheritIO();
        Process process= processBuilder.start();

        int exitCode= process.waitFor();
        if(exitCode!=0){
            throw new RuntimeException("FFmpeg encoding failed with exit code- "+ exitCode);
        }

    }

    /*
    * Generates master HLS file playlist that references all quality playlists
    * This is the file that video player downloads first
    *
    * */

    private void generateMasterPlaylist(String masterPlaylistPath) throws IOException{
        StringBuilder master= new StringBuilder();
        master.append("#EXTM3U\n");
        master.append("EXT-X-VERSION:3\n");

        int[][] qualities= new int[][]{{1920,5000,1080},{1280,2800,720},{854,1200,480},{640,800,360}};

        for(int[] quality: qualities){
            int width= quality[0];
            int bitrate= quality[1];
            int height= quality[2];

            master.append("EXT-X-STREAM-INF:BANDWIDTH=")
                    .append(bitrate*1000) //kbps to bits per second also bw==bitrate
                    .append(",RESOLUTION=").append(width).append("x").append(height)
                    .append(",CODECS=\"avc1.42e01e,mp4a.40.2\"\n");
            master.append(height).append("p/playlist.m3u8\n\n");
        }

        Files.writeString(Paths.get(masterPlaylistPath),master.toString());

    }

    /*
    * to upload all encoded files from local directory to s3
    */

    private void uploadEncodedFilesToS3(String localDir, String s3Prefix) {
        File directory= new File(localDir);
        uploadDirectoryToS3(directory,localDir,s3Prefix);
    }

    private void uploadDirectoryToS3(File directory, String localDir, String s3Prefix) {
        for(File file: directory.listFiles()){
            if(file.isDirectory()){
                uploadDirectoryToS3(file,localDir,s3Prefix);
            }else{
                String relativePath= Paths.get(localDir)
                        .relativize(file.toPath())
                        .toString()
                        .replace("\\", "/");

                String s3Key= s3Prefix+relativePath;
                String contentType= relativePath.endsWith("m3u8")?
                        "application/x-mpegURL": //.m3u8 → HLS playlist
                        "video/MP2T"; //.ts   → MPEG-TS video segment

                PutObjectRequest putObjectRequest= PutObjectRequest.builder()
                        .key(s3Key)
                        .contentType(contentType)
                        .bucket(bucketName)
                        .build();

                s3Client.putObject(putObjectRequest, RequestBody.fromFile(file));
                log.debug("Uploaded: {}",s3Key);

            }
        }
    }

    /*
    * cleanup temp files after encoding
    */

    private void cleanupTempFiles(String jobPath) {
        try{
            Path dirPath= Paths.get(jobPath);
            if(Files.exists(dirPath)){
                Files.walk(dirPath)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);

                log.info("Temp files cleaned up for job: {}", jobPath);
            }
        }catch (IOException e){
                log.warn("Failed to cleanup temp files: {}",e.getMessage());
        }
    }



}
