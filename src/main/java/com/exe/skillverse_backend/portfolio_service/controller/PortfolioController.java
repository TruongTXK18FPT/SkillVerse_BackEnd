package com.exe.skillverse_backend.portfolio_service.controller;

import com.exe.skillverse_backend.portfolio_service.dto.CVGenerationRequest;
import com.exe.skillverse_backend.portfolio_service.dto.CompletedMissionDTO;
import com.exe.skillverse_backend.portfolio_service.dto.ExternalCertificateDTO;
import com.exe.skillverse_backend.portfolio_service.dto.GeneratedCVDTO;
import com.exe.skillverse_backend.portfolio_service.dto.MentorReviewDTO;
import com.exe.skillverse_backend.portfolio_service.dto.PortfolioProjectDTO;
import com.exe.skillverse_backend.portfolio_service.dto.SystemCertificateDTO;
import com.exe.skillverse_backend.portfolio_service.dto.UserProfileDTO;
import com.exe.skillverse_backend.portfolio_service.entity.MentorReview;
import com.exe.skillverse_backend.portfolio_service.repository.MentorReviewRepository;
import com.exe.skillverse_backend.portfolio_service.service.PortfolioService;
import com.exe.skillverse_backend.shared.exception.ConflictException;
import com.exe.skillverse_backend.shared.exception.ForbiddenException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Portfolio Management", description = "APIs for managing user portfolios, projects, certificates, and CV generation")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final MentorReviewRepository reviewRepository;

    // ==================== USER PROFILE ====================

    // ==================== EXTENDED PROFILE ====================

    @PostMapping(value = "/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create portfolio extended profile", description = "Create a new portfolio extended profile with avatar, video intro, and cover image. This complements the basic profile from user_service.")
    public ResponseEntity<?> createExtendedProfile(
            @RequestPart("profile") UserProfileDTO profileDTO,
            @RequestPart(value = "avatar", required = false) @Parameter(description = "Portfolio avatar (separate from basic profile avatar)") MultipartFile avatar,
            @RequestPart(value = "video", required = false) @Parameter(description = "Video introduction") MultipartFile video,
            @RequestPart(value = "coverImage", required = false) @Parameter(description = "Portfolio cover/banner image") MultipartFile coverImage,
            Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            UserProfileDTO result = portfolioService.createExtendedProfile(userId, profileDTO, avatar, video,
                    coverImage);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Portfolio extended profile created successfully",
                    "data", result));
        } catch (Exception e) {
            return handlePortfolioException(e, "Error creating extended profile", "Failed to create extended profile: ");
        }
    }

    @PutMapping(value = "/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update portfolio extended profile", description = "Update existing portfolio extended profile. Can update text fields and/or upload new media files.")
    public ResponseEntity<?> updateExtendedProfile(
            @RequestPart("profile") UserProfileDTO profileDTO,
            @RequestPart(value = "avatar", required = false) @Parameter(description = "New portfolio avatar (optional)") MultipartFile avatar,
            @RequestPart(value = "video", required = false) @Parameter(description = "New video introduction (optional)") MultipartFile video,
            @RequestPart(value = "coverImage", required = false) @Parameter(description = "New portfolio cover image (optional)") MultipartFile coverImage,
            Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            UserProfileDTO result = portfolioService.updateExtendedProfile(userId, profileDTO, avatar, video,
                    coverImage);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Portfolio extended profile updated successfully",
                    "data", result));
        } catch (Exception e) {
            return handlePortfolioException(e, "Error updating extended profile", "Failed to update extended profile: ");
        }
    }

    @DeleteMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete portfolio extended profile", description = "Delete portfolio extended profile and all associated media files. This does NOT delete the basic profile from user_service.")
    public ResponseEntity<?> deleteExtendedProfile(Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            portfolioService.deleteExtendedProfile(userId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Portfolio extended profile deleted successfully"));
        } catch (Exception e) {
            return handlePortfolioException(e, "Error deleting extended profile", "Failed to delete extended profile: ");
        }
    }

    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get combined profile", description = "Retrieve the combined portfolio profile (basic + extended) of the authenticated user")
    public ResponseEntity<?> getProfile(Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            UserProfileDTO profile = portfolioService.getProfile(userId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", profile));
        } catch (Exception e) {
            return handlePortfolioException(e, "Error retrieving profile", "Failed to retrieve profile: ");
        }
    }

    @GetMapping("/profile/check")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Check if user has extended profile", description = "Check whether the authenticated user has created a portfolio extended profile")
    public ResponseEntity<?> checkExtendedProfile(Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            boolean hasProfile = portfolioService.hasExtendedProfile(userId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "hasExtendedProfile", hasProfile));
        } catch (Exception e) {
            log.error("Error checking extended profile", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
        }
    }

    @GetMapping("/profile/slug/{slug}")
    @Operation(summary = "Get public profile by custom URL slug", description = "Retrieve public portfolio by custom URL (e.g., /portfolio/john-doe-developer). Increments view count.")
    public ResponseEntity<?> getProfileBySlug(@PathVariable String slug) {
        try {
            UserProfileDTO profile = portfolioService.getProfileBySlug(slug);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", profile));
        } catch (Exception e) {
            return handlePortfolioException(
                    e,
                    "Error retrieving profile by slug: " + slug,
                    "Failed to retrieve profile by slug: ");
        }
    }

    @GetMapping("/profile/{userId}")
    @Operation(summary = "Get public profile", description = "Retrieve the public portfolio profile of any user")
    public ResponseEntity<?> getPublicProfile(@PathVariable Long userId) {
        try {
            UserProfileDTO profile = portfolioService.getPublicProfile(userId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", profile));
        } catch (Exception e) {
            return handlePortfolioException(e, "Error retrieving public profile", "Failed to retrieve public profile: ");
        }
    }

    @GetMapping("/public")
    @Operation(summary = "Get all public portfolios", description = "Retrieve all public portfolios for the mentorship page")
    public ResponseEntity<?> getAllPublicPortfolios() {
        try {
            List<UserProfileDTO> profiles = portfolioService.getAllPublicPortfolios();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", profiles));
        } catch (Exception e) {
            log.error("Error retrieving public portfolios", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
        }
    }

    @GetMapping("/public/{userId}/projects")
    @Operation(summary = "Get public user projects", description = "Retrieve all projects of a specific user (public)")
    public ResponseEntity<?> getPublicUserProjects(@PathVariable Long userId) {
        try {
            List<PortfolioProjectDTO> projects = portfolioService.getPublicUserProjects(userId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", projects));
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error retrieving public projects", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
        }
    }

    @GetMapping("/public/{userId}/certificates")
    @Operation(summary = "Get public user certificates", description = "Retrieve all certificates of a specific user (public)")
    public ResponseEntity<?> getPublicUserCertificates(@PathVariable Long userId) {
        try {
            List<ExternalCertificateDTO> certificates = portfolioService.getPublicUserCertificates(userId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", certificates));
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error retrieving public certificates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
        }
    }

    @GetMapping("/public/{userId}/reviews")
    @Operation(summary = "Get public user reviews", description = "Retrieve all reviews of a specific user (public)")
    public ResponseEntity<?> getPublicUserReviews(@PathVariable Long userId) {
        try {
            List<MentorReviewDTO> reviews = portfolioService.getPublicUserReviews(userId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", reviews));
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error retrieving public reviews", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
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
                    "data", result));
        } catch (Exception e) {
            return handlePortfolioException(e, "Error creating project", "Failed to create project: ");
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
                    "data", result));
        } catch (Exception e) {
            return handlePortfolioException(e, "Error updating project", "Failed to update project: ");
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
                    "data", projects));
        } catch (Exception e) {
            log.error("Error retrieving projects", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
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
                    "message", "Project deleted successfully"));
        } catch (Exception e) {
            return handlePortfolioException(e, "Error deleting project", "");
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
                    "data", result));
        } catch (Exception e) {
            return handlePortfolioException(e, "Error creating certificate", "Failed to add certificate: ");
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
                    "data", certificates));
        } catch (Exception e) {
            log.error("Error retrieving certificates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
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
                    "message", "Certificate deleted successfully"));
        } catch (Exception e) {
            return handlePortfolioException(e, "Error deleting certificate", "");
        }
    }

    // ==================== SYSTEM CERTIFICATES (AUTO-IMPORT) ====================

    @GetMapping("/system-certificates")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get system certificates", description = "Retrieve course completion certificates and gamification badges that can be imported into the portfolio")
    public ResponseEntity<?> getSystemCertificates(Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            List<SystemCertificateDTO> certs = portfolioService.getSystemCertificates(userId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", certs));
        } catch (Exception e) {
            log.error("Error retrieving system certificates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
        }
    }

    @PostMapping("/certificates/import/system")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Import system certificates", description = "Bulk-import course certificates and/or gamification badges into the portfolio external certificates table. source: COURSE | BADGE | ALL")
    public ResponseEntity<?> importSystemCertificates(
            @RequestParam(defaultValue = "ALL") String source,
            Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            List<SystemCertificateDTO> result = portfolioService.importSystemCertificates(userId, source);
            long imported = result.stream().filter(SystemCertificateDTO::isImported).count();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Import completed. Total system items: " + result.size() + ", imported: " + imported,
                    "data", result));
        } catch (Exception e) {
            return handlePortfolioException(e, "Error importing system certificates", "Failed to import: ");
        }
    }

    // ==================== COMPLETED MISSIONS (SHORT-TERM JOBS) ====================

    @GetMapping("/completed-missions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get completed missions", description = "Retrieve completed short-term job applications for the authenticated user")
    public ResponseEntity<?> getCompletedMissions(Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            List<CompletedMissionDTO> missions = portfolioService.getCompletedMissions(userId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", missions));
        } catch (Exception e) {
            log.error("Error retrieving completed missions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
        }
    }

    @GetMapping("/public/{userId}/completed-missions")
    @Operation(summary = "Get public completed missions", description = "Retrieve completed short-term job applications for a public portfolio")
    public ResponseEntity<?> getPublicCompletedMissions(@PathVariable Long userId) {
        try {
            List<CompletedMissionDTO> missions = portfolioService.getPublicCompletedMissions(userId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", missions));
        } catch (Exception e) {
            return handlePortfolioException(e, "Error retrieving public completed missions", "");
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
                    "data", reviews));
        } catch (Exception e) {
            log.error("Error retrieving reviews", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
        }
    }

    @PutMapping("/reviews/{id}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Verify mentor review (Admin)")
    public ResponseEntity<?> verifyReview(@PathVariable Long id, @RequestParam boolean verified) {
        try {
            MentorReview review = reviewRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Review not found: " + id));
            review.setIsVerified(verified);
            reviewRepository.save(review);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
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
                    "data", cv));
        } catch (Exception e) {
            return handlePortfolioException(e, "Error generating CV", "Failed to generate CV: ");
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
                    "data", cv));
        } catch (Exception e) {
            return handlePortfolioException(e, "Error updating CV", "Failed to update CV: ");
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
                    "data", cv));
        } catch (Exception e) {
            log.error("Error retrieving active CV", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
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
                    "data", cvs));
        } catch (Exception e) {
            log.error("Error retrieving CVs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
        }
    }

    @PutMapping("/cv/{cvId}/set-active")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Set CV as active", description = "Set a specific CV as the active one for the user")
    public ResponseEntity<?> setActiveCV(
            @PathVariable Long cvId,
            Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            GeneratedCVDTO cv = portfolioService.setActiveCV(userId, cvId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "CV set as active successfully",
                    "data", cv));
        } catch (Exception e) {
            return handlePortfolioException(e, "Error setting CV as active", "Failed to set CV as active: ");
        }
    }

    @DeleteMapping("/cv/{cvId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete CV", description = "Delete a specific CV")
    public ResponseEntity<?> deleteCV(
            @PathVariable Long cvId,
            Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            portfolioService.deleteCV(cvId, userId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "CV deleted successfully"));
        } catch (Exception e) {
            return handlePortfolioException(e, "Error deleting CV", "Failed to delete CV: ");
        }
    }

    private ResponseEntity<Map<String, Object>> handlePortfolioException(
            Exception e,
            String logContext,
            String fallbackMessagePrefix) {
        if (e instanceof NotFoundException) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
        }
        if (e instanceof ForbiddenException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
        }
        if (e instanceof ConflictException) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
        }
        if (e instanceof IllegalArgumentException) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
        }

        log.error(logContext, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", fallbackMessagePrefix + e.getMessage()));
    }
}
