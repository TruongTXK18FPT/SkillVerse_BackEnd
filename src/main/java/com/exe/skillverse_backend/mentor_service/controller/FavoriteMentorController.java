package com.exe.skillverse_backend.mentor_service.controller;

import com.exe.skillverse_backend.mentor_service.dto.response.MentorProfileResponse;
import com.exe.skillverse_backend.mentor_service.service.FavoriteMentorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
@Tag(name = "Favorite Mentors", description = "APIs for managing favorite mentors")
public class FavoriteMentorController {

    private final FavoriteMentorService favoriteMentorService;

    @PostMapping("/toggle/{mentorId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Toggle favorite status for a mentor")
    public ResponseEntity<?> toggleFavorite(@PathVariable Long mentorId, Authentication authentication) {
        Long studentId = Long.parseLong(authentication.getName());
        boolean isFavorite = favoriteMentorService.toggleFavorite(studentId, mentorId);
        return ResponseEntity.ok(Map.of("isFavorite", isFavorite));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my favorite mentors")
    public ResponseEntity<List<MentorProfileResponse>> getMyFavorites(Authentication authentication) {
        Long studentId = Long.parseLong(authentication.getName());
        List<MentorProfileResponse> favorites = favoriteMentorService.getFavoriteMentors(studentId);
        return ResponseEntity.ok(favorites);
    }

    @GetMapping("/check/{mentorId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Check if a mentor is favorite")
    public ResponseEntity<Boolean> checkFavorite(@PathVariable Long mentorId, Authentication authentication) {
        Long studentId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(favoriteMentorService.isFavorite(studentId, mentorId));
    }
}
