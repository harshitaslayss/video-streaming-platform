package com.netflix.videoservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    //published when video is uploaded to S3
    //consumed by the encoding service
    @Bean
    public NewTopic videoUploadedTopic(){
        return TopicBuilder.name("video.uploaded")
                .partitions(3)
                .replicas(1)
                .build();
    }

    //Published when the video encoding is complete
    @Bean
    public NewTopic videoEncodedTopic(){
        return TopicBuilder.name("video.encoded")
                .replicas(1)
                .partitions(3)
                .build();
    }
}
