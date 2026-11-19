package com.netflix.contentservice.service;
import com.netflix.contentservice.dto.MovieRequest;
import com.netflix.contentservice.dto.MovieResponse;
import com.netflix.contentservice.model.Genre;
import com.netflix.contentservice.model.Movie;
import com.netflix.contentservice.model.VideoStatus;
import com.netflix.contentservice.repository.ContentRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContentService {

    private final ContentRepository contentRepository;

    /*
   Add a new movie to the catalog, and movie has not been uploaded yet
   */
    public MovieResponse addMovie(@Valid MovieRequest movieRequest) {

        log.info("Adding new movie: {}", movieRequest.getTitle());

        Movie movie= new Movie();
        movie.setTitle(movieRequest.getTitle());
        movie.setDescription(movieRequest.getDescription());
        movie.setCast(movieRequest.getCast());
        movie.setRating(movieRequest.getRating());
        movie.setDirector(movieRequest.getDirector());
        movie.setReleaseYear(movieRequest.getReleaseYear());
        movie.setThumbnailUrl(movieRequest.getThumbnailUrl());
        movie.setDurationMinutes(movieRequest.getDurationMinutes());
        movie.setGenre(movieRequest.getGenre());
        movie.setVideoStatus(VideoStatus.PENDING);

        Movie savedMovie= contentRepository.save(movie);
        log.info("Movie added with ID: {}",savedMovie.getId());
        return mapToResponse(savedMovie);
    }

    /*
    get all movies in the catalog
    */

    public List<MovieResponse> getAllMovies() {
        return contentRepository.findAll().stream().map(
                this::mapToResponse
        ).collect(Collectors.toList());
    }

    /*
    get movie by ID
     */

    public MovieResponse getMovieById(String movieId) {
        Movie movie= contentRepository.findById(movieId).orElseThrow(()-> new RuntimeException("Movie not found: "+ movieId));
        return mapToResponse(movie);
    }

    /*
    Get movies by genre
    */
    public List<MovieResponse> getMoviesByGenre(Genre genre) {
        return contentRepository.findByGenre(genre).stream().map(
                this::mapToResponse
        ).toList();

    }

    /*
    search movies by title
     */
    public List<MovieResponse> searchMovies(String title) {
        return contentRepository.findByTitleContainingIgnoreCase(title).stream().map(
                this::mapToResponse
        ).collect(Collectors.toList());
    }

    /*
    * uploads video to s3 -> gives us key so we need to update
    */
    public void updateVideoKey(String movieId, String videoKey) {
        log.info("Updating video key for movie: {}",movieId);
        Movie movie = contentRepository.findById(movieId).orElseThrow(() -> new RuntimeException("Movie not found: " + movieId));
        movie.setVideoKey(videoKey);
        movie.setVideoStatus(VideoStatus.UPLOADED);
        contentRepository.save(movie);
    }

    /*
    AFTER UPLOADING WE GET HLS URL-> TO UPDATE HLS URL
     */
    public void updateHlsUrl(String movieId, String hlsUrl) {
        log.info("Updating HLS Url for movie: {}",movieId);
        Movie movie = contentRepository.findById(movieId).orElseThrow(() -> new RuntimeException("Movie not found: " + movieId));
        movie.setHlsUrl(hlsUrl);
        movie.setVideoStatus(VideoStatus.READY);
        contentRepository.save(movie);
    }

    private MovieResponse mapToResponse(Movie savedMovie) {

        MovieResponse movieResponse= new MovieResponse();
        movieResponse.setId(savedMovie.getId());
        movieResponse.setTitle(savedMovie.getTitle());
        movieResponse.setDescription(savedMovie.getDescription());
        movieResponse.setCast(savedMovie.getCast());
        movieResponse.setRating(savedMovie.getRating());
        movieResponse.setDirector(savedMovie.getDirector());
        movieResponse.setReleaseYear(savedMovie.getReleaseYear());
        movieResponse.setThumbnailUrl(savedMovie.getThumbnailUrl());
        movieResponse.setDurationMinutes(savedMovie.getDurationMinutes());
        movieResponse.setGenre(savedMovie.getGenre());
        movieResponse.setVideoStatus(savedMovie.getVideoStatus());
        movieResponse.setVideoKey(savedMovie.getVideoKey());
        movieResponse.setHlsUrl(savedMovie.getHlsUrl());
        movieResponse.setCreatedAt(savedMovie.getCreatedAt());

        return movieResponse;
    }

    public void updateMovieStatus(String movieId, VideoStatus videoStatus){
        Movie movie= contentRepository.findById(movieId).orElseThrow(()->new RuntimeException("Movie not found: "+ movieId));
        movie.setVideoStatus(videoStatus);
        contentRepository.save(movie);
    }

}
