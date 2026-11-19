package com.netflix.contentservice.dto;

import com.netflix.contentservice.model.Genre;
import com.netflix.contentservice.model.VideoStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MovieResponse {
    private String id;
    private String title;
    private String description;
    private String cast;
    private double rating;
    private String director;
    private int releaseYear;
    private String thumbnailUrl;
    private int durationMinutes;
    private Genre genre;
    private String videoKey;
    private String hlsUrl;
    private VideoStatus videoStatus;
    private LocalDateTime createdAt;
}
