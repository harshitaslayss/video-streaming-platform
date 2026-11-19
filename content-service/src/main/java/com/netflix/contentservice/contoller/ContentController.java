package com.netflix.contentservice.contoller;

import com.netflix.contentservice.dto.MovieRequest;
import com.netflix.contentservice.dto.MovieResponse;
import com.netflix.contentservice.model.Genre;
import com.netflix.contentservice.service.ContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/movies")
@Slf4j
@RequiredArgsConstructor
public class ContentController {
    private final ContentService contentService;

    //add new Movie
    @PostMapping
    private ResponseEntity<MovieResponse> addMovie(@Valid @RequestBody MovieRequest movieRequest){
        return ResponseEntity.status(HttpStatus.CREATED).body(contentService.addMovie(movieRequest));
    }

    //get all movies
    @GetMapping
    private ResponseEntity<List<MovieResponse>> getAllMovies(){
        return ResponseEntity.ok(contentService.getAllMovies());
    }

    @GetMapping("/genres/{genre}")
    private ResponseEntity<List<MovieResponse>> getMoviesByGenre(@PathVariable Genre genre){
        return ResponseEntity.ok(contentService.getMoviesByGenre(genre));
    }

    @GetMapping("/{movieId}")
    private ResponseEntity<MovieResponse> getMovieById(@PathVariable String movieId){
        return ResponseEntity.ok(contentService.getMovieById(movieId));
    }

    @GetMapping("/search")
    private ResponseEntity<List<MovieResponse>> searchMovies(@RequestParam String title){
        return ResponseEntity.ok(contentService.searchMovies(title));
    }




}
