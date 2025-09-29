package com.exe.skillverse_backend.mentor_service.controller;

import com.exe.skillverse_backend.mentor_service.dto.response.MentorRegistrationResponse;
import com.exe.skillverse_backend.mentor_service.service.MentorRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/mentors")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mentor Registration", description = "Mentor registration and management endpoints")
public class MentorRegistrationController {

    private final MentorRegistrationService mentorRegistrationService;

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            @RequestParam("fullName") String fullName,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "bio", required = false) String bio,
            @RequestParam(value = "address", required = false) String address,
            @RequestParam(value = "region", required = false) String region,
            @RequestParam(value = "linkedinProfile", required = false) String linkedinProfile,
            @RequestParam("mainExpertiseArea") String mainExpertiseArea,
            @RequestParam("yearsOfExperience") Integer yearsOfExperience,
            @RequestParam("personalProfile") String personalProfile,
            @RequestParam(value = "cvPortfolioFile", required = false) MultipartFile cvPortfolioFile,
            @RequestParam(value = "certificatesFile", required = false) MultipartFile certificatesFile) {
        try {
            log.info("Processing mentor registration for email: {}", email);

            // Delegate all business logic to service layer
            MentorRegistrationResponse response = mentorRegistrationService.registerMentor(
                    email, password, confirmPassword, fullName, phone, bio, address, region,
                    linkedinProfile, mainExpertiseArea, yearsOfExperience, personalProfile,
                    cvPortfolioFile, certificatesFile);

            log.info("Mentor registration successful for email: {}", email);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.warn("Mentor registration failed - bad request: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Mentor registration failed for email: {}", email, e);
            throw e;
        }
    }
}