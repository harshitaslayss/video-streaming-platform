package com.netflix.contentservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name="movies")

public class Movie {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    private String cast;
    private double rating;

    private String director;
    private int releaseYear;

    private String thumbnailUrl;
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    private Genre genre;

    //s3 key for video file
    private String videoKey;

    //hls url for master db
    private String hlsUrl;

    //status for video uploaded or not
    @Enumerated(EnumType.STRING)
    private VideoStatus videoStatus;

    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;

}
