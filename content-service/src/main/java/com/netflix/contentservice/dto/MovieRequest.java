package com.netflix.contentservice.dto;

import com.netflix.contentservice.model.Genre;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovieRequest {

    @NotBlank(message = "Title is required.")
    private String title;

    private String description;
    private String cast;
    private String director;
    private double rating;
    @NotNull(message = "Genre is required.")
    private Genre genre;
    private int releaseYear;
    private String thumbnailUrl;
    private int durationMinutes;

}
