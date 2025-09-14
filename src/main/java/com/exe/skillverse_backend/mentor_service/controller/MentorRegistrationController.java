package com.exe.skillverse_backend.mentor_service.controller;

import com.exe.skillverse_backend.mentor_service.dto.request.MentorRegistrationRequest;
import com.exe.skillverse_backend.mentor_service.dto.response.MentorRegistrationResponse;
import com.exe.skillverse_backend.mentor_service.service.MentorRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mentors")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mentor Registration", description = "Mentor registration and management endpoints")
public class MentorRegistrationController {

    private final MentorRegistrationService mentorRegistrationService;

    @PostMapping("/register")
    @Operation(summary = "Register as a mentor", description = "Register a new mentor account with profile information. "
            +
            "Creates User entity in auth service and MentorProfile in mentor service. " +
            "Application will be pending admin approval.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Mentor registration successful", content = @Content(schema = @Schema(implementation = MentorRegistrationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data or email already exists", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    public ResponseEntity<MentorRegistrationResponse> registerMentor(
            @Valid @RequestBody MentorRegistrationRequest request) {
        try {
            log.info("Processing mentor registration for email: {}", request.getEmail());

            MentorRegistrationResponse response = mentorRegistrationService.register(request);

            log.info("Mentor registration successful for email: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.warn("Mentor registration failed - bad request: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Mentor registration failed for email: {}", request.getEmail(), e);
            throw e;
        }
    }
}