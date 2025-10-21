package com.exe.skillverse_backend.portfolio_service.controller;

import com.exe.skillverse_backend.portfolio_service.dto.*;
import com.exe.skillverse_backend.portfolio_service.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Portfolio Management", description = "APIs for managing user portfolios, projects, certificates, and CV generation")
public class PortfolioController {

    private final PortfolioService portfolioService;

    // ==================== USER PROFILE ====================

    // ==================== EXTENDED PROFILE ====================

    @PostMapping(value = "/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create portfolio extended profile", 
               description = "Create a new portfolio extended profile with avatar, video intro, and cover image. This complements the basic profile from user_service.")
    public ResponseEntity<?> createExtendedProfile(
            @RequestPart("profile") UserProfileDTO profileDTO,
            @RequestPart(value = "avatar", required = false) 
            @Parameter(description = "Portfolio avatar (separate from basic profile avatar)") MultipartFile avatar,
            @RequestPart(value = "video", required = false) 
            @Parameter(description = "Video introduction") MultipartFile video,
            @RequestPart(value = "coverImage", required = false) 
            @Parameter(description = "Portfolio cover/banner image") MultipartFile coverImage,
            Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            UserProfileDTO result = portfolioService.createExtendedProfile(userId, profileDTO, avatar, video, coverImage);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Portfolio extended profile created successfully",
                    "data", result
            ));
        } catch (RuntimeException e) {
            if (e.getMessage().contains("already exists")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "success", false,
                        "message", e.getMessage()
                ));
            }
            log.error("Error creating extended profile", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to create extended profile: " + e.getMessage()
            ));
        }
    }

    @PutMapping(value = "/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update portfolio extended profile", 
               description = "Update existing portfolio extended profile. Can update text fields and/or upload new media files.")
    public ResponseEntity<?> updateExtendedProfile(
            @RequestPart("profile") UserProfileDTO profileDTO,
            @RequestPart(value = "avatar", required = false) 
            @Parameter(description = "New portfolio avatar (optional)") MultipartFile avatar,
            @RequestPart(value = "video", required = false) 
            @Parameter(description = "New video introduction (optional)") MultipartFile video,
            @RequestPart(value = "coverImage", required = false) 
            @Parameter(description = "New portfolio cover image (optional)") MultipartFile coverImage,
            Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            UserProfileDTO result = portfolioService.updateExtendedProfile(userId, profileDTO, avatar, video, coverImage);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Portfolio extended profile updated successfully",
                    "data", result
            ));
        } catch (Exception e) {
            log.error("Error updating extended profile", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to update extended profile: " + e.getMessage()
            ));
        }
    }

    @DeleteMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete portfolio extended profile", 
               description = "Delete portfolio extended profile and all associated media files. This does NOT delete the basic profile from user_service.")
    public ResponseEntity<?> deleteExtendedProfile(Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            portfolioService.deleteExtendedProfile(userId);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Portfolio extended profile deleted successfully"
            ));
        } catch (Exception e) {
            log.error("Error deleting extended profile", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to delete extended profile: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get combined profile", 
               description = "Retrieve the combined portfolio profile (basic + extended) of the authenticated user")
    public ResponseEntity<?> getProfile(Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            UserProfileDTO profile = portfolioService.getProfile(userId);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", profile
            ));
        } catch (Exception e) {
            log.error("Error retrieving profile", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/profile/check")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Check if user has extended profile", 
               description = "Check whether the authenticated user has created a portfolio extended profile")
    public ResponseEntity<?> checkExtendedProfile(Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            boolean hasProfile = portfolioService.hasExtendedProfile(userId);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "hasExtendedProfile", hasProfile
            ));
        } catch (Exception e) {
            log.error("Error checking extended profile", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/profile/slug/{slug}")
    @Operation(summary = "Get public profile by custom URL slug", 
               description = "Retrieve public portfolio by custom URL (e.g., /portfolio/john-doe-developer). Increments view count.")
    public ResponseEntity<?> getProfileBySlug(@PathVariable String slug) {
        try {
            UserProfileDTO profile = portfolioService.getProfileBySlug(slug);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", profile
            ));
        } catch (Exception e) {
            log.error("Error retrieving profile by slug: {}", slug, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/profile/{userId}")
    @Operation(summary = "Get public profile", description = "Retrieve the public portfolio profile of any user")
    public ResponseEntity<?> getPublicProfile(@PathVariable Long userId) {
        try {
            UserProfileDTO profile = portfolioService.getProfile(userId);
            
            if (!profile.getIsPublic()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "success", false,
                        "message", "This profile is private"
                ));
            }
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", profile
            ));
        } catch (Exception e) {
            log.error("Error retrieving public profile", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    // ==================== PROJECTS ====================

    @PostMapping(value = "/projects", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a new project", description = "Add a new project to the portfolio")
    public ResponseEntity<?> createProject(
            @RequestPart("project") PortfolioProjectDTO projectDTO,
            @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail,
            Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            PortfolioProjectDTO result = portfolioService.createProject(userId, projectDTO, thumbnail);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Project created successfully",
                    "data", result
            ));
        } catch (Exception e) {
            log.error("Error creating project", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to create project: " + e.getMessage()
            ));
        }
    }

    @PutMapping(value = "/projects/{projectId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update a project", description = "Update an existing project")
    public ResponseEntity<?> updateProject(
            @PathVariable Long projectId,
            @RequestPart("project") PortfolioProjectDTO projectDTO,
            @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail,
            Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            PortfolioProjectDTO result = portfolioService.updateProject(projectId, userId, projectDTO, thumbnail);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Project updated successfully",
                    "data", result
            ));
        } catch (Exception e) {
            log.error("Error updating project", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to update project: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/projects")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get user projects", description = "Retrieve all projects of the authenticated user")
    public ResponseEntity<?> getUserProjects(Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            List<PortfolioProjectDTO> projects = portfolioService.getUserProjects(userId);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", projects
            ));
        } catch (Exception e) {
            log.error("Error retrieving projects", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/projects/{projectId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete a project", description = "Delete a project from the portfolio")
    public ResponseEntity<?> deleteProject(
            @PathVariable Long projectId,
            Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            portfolioService.deleteProject(projectId, userId);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Project deleted successfully"
            ));
        } catch (Exception e) {
            log.error("Error deleting project", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    // ==================== EXTERNAL CERTIFICATES ====================

    @PostMapping(value = "/certificates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Add external certificate", description = "Add a certificate from outside the system")
    public ResponseEntity<?> createCertificate(
            @RequestPart("certificate") ExternalCertificateDTO certificateDTO,
            @RequestPart(value = "image", required = false) MultipartFile image,
            Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            ExternalCertificateDTO result = portfolioService.createCertificate(userId, certificateDTO, image);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Certificate added successfully",
                    "data", result
            ));
        } catch (Exception e) {
            log.error("Error creating certificate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to add certificate: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/certificates")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get user certificates", description = "Retrieve all certificates of the authenticated user")
    public ResponseEntity<?> getUserCertificates(Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            List<ExternalCertificateDTO> certificates = portfolioService.getUserCertificates(userId);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", certificates
            ));
        } catch (Exception e) {
            log.error("Error retrieving certificates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/certificates/{certificateId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete a certificate", description = "Delete a certificate from the portfolio")
    public ResponseEntity<?> deleteCertificate(
            @PathVariable Long certificateId,
            Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            portfolioService.deleteCertificate(certificateId, userId);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Certificate deleted successfully"
            ));
        } catch (Exception e) {
            log.error("Error deleting certificate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    // ==================== MENTOR REVIEWS ====================

    @GetMapping("/reviews")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get mentor reviews", description = "Retrieve all mentor reviews for the authenticated user")
    public ResponseEntity<?> getUserReviews(Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            List<MentorReviewDTO> reviews = portfolioService.getUserReviews(userId);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", reviews
            ));
        } catch (Exception e) {
            log.error("Error retrieving reviews", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    // ==================== CV GENERATION ====================

    @PostMapping("/cv/generate")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Generate CV with AI", description = "Generate a professional CV using AI based on portfolio data")
    public ResponseEntity<?> generateCV(
            @RequestBody CVGenerationRequest request,
            Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            log.info("Generating CV for user: {} with template: {}", userId, request.getTemplateName());
            
            GeneratedCVDTO cv = portfolioService.generateCV(userId, request);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "CV generated successfully",
                    "data", cv
            ));
        } catch (Exception e) {
            log.error("Error generating CV", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to generate CV: " + e.getMessage()
            ));
        }
    }

    @PutMapping("/cv/{cvId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update CV", description = "Update an existing CV (content and JSON)")
    public ResponseEntity<?> updateCV(
            @PathVariable Long cvId,
            @RequestBody Map<String, String> updates,
            Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            String cvContent = updates.get("cvContent");
            String cvJson = updates.get("cvJson");
            
            GeneratedCVDTO cv = portfolioService.updateCV(cvId, userId, cvContent, cvJson);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "CV updated successfully",
                    "data", cv
            ));
        } catch (Exception e) {
            log.error("Error updating CV", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to update CV: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/cv/active")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get active CV", description = "Retrieve the currently active CV")
    public ResponseEntity<?> getActiveCV(Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            GeneratedCVDTO cv = portfolioService.getActiveCV(userId);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", cv
            ));
        } catch (Exception e) {
            log.error("Error retrieving active CV", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/cv/all")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all CVs", description = "Retrieve all CV versions")
    public ResponseEntity<?> getAllCVs(Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            List<GeneratedCVDTO> cvs = portfolioService.getAllUserCVs(userId);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", cvs
            ));
        } catch (Exception e) {
            log.error("Error retrieving CVs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}
