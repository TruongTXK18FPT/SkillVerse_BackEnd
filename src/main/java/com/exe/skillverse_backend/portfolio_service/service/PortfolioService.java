package com.exe.skillverse_backend.portfolio_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.portfolio_service.dto.*;
import com.exe.skillverse_backend.portfolio_service.entity.*;
import com.exe.skillverse_backend.portfolio_service.repository.*;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {

    // Extended portfolio profile
    private final PortfolioExtendedProfileRepository extendedProfileRepository;
    
    // Portfolio entities
    private final PortfolioProjectRepository projectRepository;
    private final MentorReviewRepository reviewRepository;
    private final ExternalCertificateRepository certificateRepository;
    private final GeneratedCVRepository cvRepository;
    
    // Other dependencies
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;
    private final CVGeneratorAIService cvGeneratorAIService;

    // ==================== USER PROFILE (EXTENDED) ====================

    /**
     * Create new portfolio extended profile
     * This complements the basic profile from user_service
     */
    @Transactional
    public UserProfileDTO createExtendedProfile(Long userId, UserProfileDTO dto, 
                                                MultipartFile avatarFile, 
                                                MultipartFile videoFile,
                                                MultipartFile coverImageFile) {
        User user = getUserOrThrow(userId);
        
        // Check if extended profile already exists
        if (extendedProfileRepository.existsByUserId(userId)) {
            throw new RuntimeException("Portfolio extended profile already exists for user: " + userId);
        }
        
        // Create new extended profile
        PortfolioExtendedProfile extendedProfile = PortfolioExtendedProfile.builder()
                .user(user)
                .build();

        // Upload and set fields
        extendedProfile = uploadMediaAndSetFields(extendedProfile, dto, avatarFile, videoFile, coverImageFile);
        extendedProfile = extendedProfileRepository.save(extendedProfile);
        
        log.info("Created extended profile for user: {}", userId);
        return getCombinedProfile(userId);
    }

    /**
     * Update existing portfolio extended profile
     */
    @Transactional
    public UserProfileDTO updateExtendedProfile(Long userId, UserProfileDTO dto, 
                                                MultipartFile avatarFile, 
                                                MultipartFile videoFile,
                                                MultipartFile coverImageFile) {
        // Get existing extended profile
        PortfolioExtendedProfile extendedProfile = extendedProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Portfolio extended profile not found for user: " + userId));

        // Upload and update fields
        extendedProfile = uploadMediaAndSetFields(extendedProfile, dto, avatarFile, videoFile, coverImageFile);
        extendedProfile = extendedProfileRepository.save(extendedProfile);
        
        log.info("Updated extended profile for user: {}", userId);
        return getCombinedProfile(userId);
    }

    /**
     * Helper method to upload media files and set profile fields
     */
    private PortfolioExtendedProfile uploadMediaAndSetFields(
            PortfolioExtendedProfile extendedProfile,
            UserProfileDTO dto,
            MultipartFile avatarFile,
            MultipartFile videoFile,
            MultipartFile coverImageFile) {

        // Upload portfolio avatar if provided (separate from basic profile avatar)
        if (avatarFile != null && !avatarFile.isEmpty()) {
            // Delete old avatar if exists
            if (extendedProfile.getAvatarPublicId() != null) {
                try {
                    cloudinaryService.deleteFile(extendedProfile.getAvatarPublicId(), "image");
                } catch (IOException e) {
                    log.error("Failed to delete old portfolio avatar", e);
                }
            }
            // Upload new avatar
            try {
                Map<String, Object> uploadResult = cloudinaryService.uploadImage(avatarFile, "portfolios/avatars");
                extendedProfile.setAvatarUrl((String) uploadResult.get("secure_url"));
                extendedProfile.setAvatarPublicId((String) uploadResult.get("public_id"));
            } catch (IOException e) {
                log.error("Failed to upload portfolio avatar", e);
            }
        }

        // Upload video intro if provided
        if (videoFile != null && !videoFile.isEmpty()) {
            // Delete old video if exists
            if (extendedProfile.getVideoIntroPublicId() != null) {
                try {
                    cloudinaryService.deleteFile(extendedProfile.getVideoIntroPublicId(), "video");
                } catch (IOException e) {
                    log.error("Failed to delete old video intro", e);
                }
            }
            // Upload new video
            try {
                Map<String, Object> uploadResult = cloudinaryService.uploadVideo(videoFile, "portfolios/videos");
                extendedProfile.setVideoIntroUrl((String) uploadResult.get("secure_url"));
                extendedProfile.setVideoIntroPublicId((String) uploadResult.get("public_id"));
            } catch (IOException e) {
                log.error("Failed to upload video intro", e);
            }
        }

        // Upload cover image if provided
        if (coverImageFile != null && !coverImageFile.isEmpty()) {
            // Delete old cover if exists
            if (extendedProfile.getCoverImagePublicId() != null) {
                try {
                    cloudinaryService.deleteFile(extendedProfile.getCoverImagePublicId(), "image");
                } catch (IOException e) {
                    log.error("Failed to delete old cover image", e);
                }
            }
            // Upload new cover
            try {
                Map<String, Object> uploadResult = cloudinaryService.uploadImage(coverImageFile, "portfolios/covers");
                extendedProfile.setCoverImageUrl((String) uploadResult.get("secure_url"));
                extendedProfile.setCoverImagePublicId((String) uploadResult.get("public_id"));
            } catch (IOException e) {
                log.error("Failed to upload cover image", e);
            }
        }

        // Update extended profile fields (only if not null)
        if (dto.getFullName() != null) extendedProfile.setFullName(dto.getFullName());
        if (dto.getBasicBio() != null) extendedProfile.setBio(dto.getBasicBio());
        if (dto.getPhone() != null) extendedProfile.setPhone(dto.getPhone());
        if (dto.getAddress() != null) extendedProfile.setAddress(dto.getAddress());
        if (dto.getRegion() != null) extendedProfile.setRegion(dto.getRegion());
        if (dto.getCompanyId() != null) extendedProfile.setCompanyId(dto.getCompanyId());
        if (dto.getSocialLinks() != null) extendedProfile.setSocialLinks(dto.getSocialLinks());
        if (dto.getProfessionalTitle() != null) extendedProfile.setProfessionalTitle(dto.getProfessionalTitle());
        if (dto.getCareerGoals() != null) extendedProfile.setCareerGoals(dto.getCareerGoals());
        if (dto.getYearsOfExperience() != null) extendedProfile.setYearsOfExperience(dto.getYearsOfExperience());
        if (dto.getLinkedinUrl() != null) extendedProfile.setLinkedinUrl(dto.getLinkedinUrl());
        if (dto.getGithubUrl() != null) extendedProfile.setGithubUrl(dto.getGithubUrl());
        if (dto.getPortfolioWebsiteUrl() != null) extendedProfile.setPortfolioWebsiteUrl(dto.getPortfolioWebsiteUrl());
        if (dto.getBehanceUrl() != null) extendedProfile.setBehanceUrl(dto.getBehanceUrl());
        if (dto.getDribbbleUrl() != null) extendedProfile.setDribbbleUrl(dto.getDribbbleUrl());
        if (dto.getTagline() != null) extendedProfile.setTagline(dto.getTagline());
        if (dto.getLocation() != null) extendedProfile.setLocation(dto.getLocation());
        if (dto.getAvailabilityStatus() != null) extendedProfile.setAvailabilityStatus(dto.getAvailabilityStatus());
        if (dto.getHourlyRate() != null) extendedProfile.setHourlyRate(dto.getHourlyRate());
        if (dto.getPreferredCurrency() != null) extendedProfile.setPreferredCurrency(dto.getPreferredCurrency());
        if (dto.getTopSkills() != null) extendedProfile.setTopSkills(dto.getTopSkills());
        if (dto.getLanguagesSpoken() != null) extendedProfile.setLanguagesSpoken(dto.getLanguagesSpoken());
        if (dto.getIsPublic() != null) extendedProfile.setIsPublic(dto.getIsPublic());
        if (dto.getShowContactInfo() != null) extendedProfile.setShowContactInfo(dto.getShowContactInfo());
        if (dto.getAllowJobOffers() != null) extendedProfile.setAllowJobOffers(dto.getAllowJobOffers());
        if (dto.getThemePreference() != null) extendedProfile.setThemePreference(dto.getThemePreference());
        if (dto.getCustomUrlSlug() != null) extendedProfile.setCustomUrlSlug(dto.getCustomUrlSlug());
        if (dto.getMetaDescription() != null) extendedProfile.setMetaDescription(dto.getMetaDescription());
        if (dto.getKeywords() != null) extendedProfile.setKeywords(dto.getKeywords());

        return extendedProfile;
    }

    /**
     * Delete portfolio extended profile and all associated media
     */
    @Transactional
    public void deleteExtendedProfile(Long userId) {
        PortfolioExtendedProfile extendedProfile = extendedProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Portfolio extended profile not found for user: " + userId));

        // Delete all uploaded media from Cloudinary
        if (extendedProfile.getAvatarPublicId() != null) {
            try {
                cloudinaryService.deleteFile(extendedProfile.getAvatarPublicId(), "image");
            } catch (IOException e) {
                log.error("Failed to delete portfolio avatar", e);
            }
        }
        if (extendedProfile.getVideoIntroPublicId() != null) {
            try {
                cloudinaryService.deleteFile(extendedProfile.getVideoIntroPublicId(), "video");
            } catch (IOException e) {
                log.error("Failed to delete video intro", e);
            }
        }
        if (extendedProfile.getCoverImagePublicId() != null) {
            try {
                cloudinaryService.deleteFile(extendedProfile.getCoverImagePublicId(), "image");
            } catch (IOException e) {
                log.error("Failed to delete cover image", e);
            }
        }

        extendedProfileRepository.delete(extendedProfile);
        log.info("Deleted extended profile for user: {}", userId);
    }

    /**
     * Check if user has extended portfolio profile
     */
    @Transactional(readOnly = true)
    public boolean hasExtendedProfile(Long userId) {
        return extendedProfileRepository.existsByUserId(userId);
    }

    /**
     * Get combined profile (basic + extended)
     */
    @Transactional(readOnly = true)
    public UserProfileDTO getProfile(Long userId) {
        return getCombinedProfile(userId);
    }

    /**
     * Get profile by custom URL slug (for public portfolio pages)
     */
    @Transactional(readOnly = true)
    public UserProfileDTO getProfileBySlug(String customUrlSlug) {
        PortfolioExtendedProfile extendedProfile = extendedProfileRepository.findByCustomUrlSlug(customUrlSlug)
                .orElseThrow(() -> new NotFoundException("Portfolio not found with slug: " + customUrlSlug));
        
        // Only return public portfolios
        if (!Boolean.TRUE.equals(extendedProfile.getIsPublic())) {
            throw new NotFoundException("Portfolio is not public");
        }
        
        // Increment view count
        extendedProfile.incrementPortfolioViews();
        extendedProfileRepository.save(extendedProfile);
        
        return getCombinedProfile(extendedProfile.getUserId());
    }

    /**
     * Get all public portfolios
     */
    @Transactional(readOnly = true)
    public List<UserProfileDTO> getAllPublicPortfolios() {
        return extendedProfileRepository.findByIsPublicTrue()
                .stream()
                .map(profile -> getCombinedProfile(profile.getUserId()))
                .collect(Collectors.toList());
    }

    /**
     * Get combined profile from both user_service and portfolio_service
     */
    private UserProfileDTO getCombinedProfile(Long userId) {
        PortfolioExtendedProfile extendedProfile = extendedProfileRepository.findByUserId(userId).orElse(null);
        if (extendedProfile == null) {
            throw new NotFoundException("No profile found for user: " + userId);
        }
        return mapToCombinedProfileDTO(extendedProfile);
    }

    // ==================== PROJECTS ====================

    @Transactional
    public PortfolioProjectDTO createProject(Long userId, PortfolioProjectDTO dto, MultipartFile thumbnailFile) {
        User user = getUserOrThrow(userId);
        
        PortfolioProject project = PortfolioProject.builder()
                .user(user)
                .title(dto.getTitle())
                .description(dto.getDescription())
                .clientName(dto.getClientName())
                .projectType(dto.getProjectType())
                .duration(dto.getDuration())
                .completionDate(dto.getCompletionDate())
                .tools(dto.getTools())
                .outcomes(dto.getOutcomes())
                .rating(dto.getRating())
                .clientFeedback(dto.getClientFeedback())
                .projectUrl(dto.getProjectUrl())
                .githubUrl(dto.getGithubUrl())
                .isFeatured(dto.getIsFeatured() != null ? dto.getIsFeatured() : false)
                .build();

        // Upload thumbnail if provided
        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            try {
                Map<String, Object> uploadResult = cloudinaryService.uploadImage(thumbnailFile, "portfolios/projects");
                project.setThumbnailUrl((String) uploadResult.get("secure_url"));
                project.setThumbnailPublicId((String) uploadResult.get("public_id"));
            } catch (IOException e) {
                log.error("Failed to upload project thumbnail", e);
            }
        }

        project = projectRepository.save(project);
        
        // Update project count in extended profile
        updateExtendedProfileProjectCount(userId);
        
        return mapToProjectDTO(project);
    }

    @Transactional
    public PortfolioProjectDTO updateProject(Long projectId, Long userId, PortfolioProjectDTO dto, MultipartFile thumbnailFile) {
        PortfolioProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        if (!project.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized to update this project");
        }

        // Upload new thumbnail if provided
        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            // Delete old thumbnail
            if (project.getThumbnailPublicId() != null) {
                try {
                    cloudinaryService.deleteFile(project.getThumbnailPublicId(), "image");
                } catch (IOException e) {
                    log.error("Failed to delete old thumbnail", e);
                }
            }
            // Upload new one
            try {
                Map<String, Object> uploadResult = cloudinaryService.uploadImage(thumbnailFile, "portfolios/projects");
                project.setThumbnailUrl((String) uploadResult.get("secure_url"));
                project.setThumbnailPublicId((String) uploadResult.get("public_id"));
            } catch (IOException e) {
                log.error("Failed to upload new thumbnail", e);
            }
        }

        // Update fields
        project.setTitle(dto.getTitle());
        project.setDescription(dto.getDescription());
        project.setClientName(dto.getClientName());
        project.setProjectType(dto.getProjectType());
        project.setDuration(dto.getDuration());
        project.setCompletionDate(dto.getCompletionDate());
        project.setTools(dto.getTools());
        project.setOutcomes(dto.getOutcomes());
        project.setRating(dto.getRating());
        project.setClientFeedback(dto.getClientFeedback());
        project.setProjectUrl(dto.getProjectUrl());
        project.setGithubUrl(dto.getGithubUrl());
        project.setIsFeatured(dto.getIsFeatured());

        project = projectRepository.save(project);
        return mapToProjectDTO(project);
    }

    @Transactional(readOnly = true)
    public List<PortfolioProjectDTO> getUserProjects(Long userId) {
        return projectRepository.findByUserIdOrderByCompletionDateDesc(userId)
                .stream()
                .map(this::mapToProjectDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PortfolioProjectDTO> getPublicUserProjects(Long userId) {
        // For now, return all projects. In future, might filter by isPublic if projects have that flag.
        return getUserProjects(userId);
    }

    @Transactional
    public void deleteProject(Long projectId, Long userId) {
        PortfolioProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        if (!project.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized to delete this project");
        }

        // Delete thumbnail from Cloudinary
        if (project.getThumbnailPublicId() != null) {
            try {
                cloudinaryService.deleteFile(project.getThumbnailPublicId(), "image");
            } catch (IOException e) {
                log.error("Failed to delete project thumbnail", e);
            }
        }

        projectRepository.delete(project);
        
        // Update project count in extended profile
        updateExtendedProfileProjectCount(userId);
    }

    // ==================== EXTERNAL CERTIFICATES ====================

    @Transactional
    public ExternalCertificateDTO createCertificate(Long userId, ExternalCertificateDTO dto, MultipartFile certificateImage) {
        User user = getUserOrThrow(userId);
        
        ExternalCertificate certificate = ExternalCertificate.builder()
                .user(user)
                .title(dto.getTitle())
                .issuingOrganization(dto.getIssuingOrganization())
                .issueDate(dto.getIssueDate())
                .expiryDate(dto.getExpiryDate())
                .credentialId(dto.getCredentialId())
                .credentialUrl(dto.getCredentialUrl())
                .description(dto.getDescription())
                .skills(dto.getSkills())
                .category(dto.getCategory())
                .isVerified(false)
                .build();

        // Upload certificate image if provided
        if (certificateImage != null && !certificateImage.isEmpty()) {
            try {
                Map<String, Object> uploadResult = cloudinaryService.uploadImage(certificateImage, "portfolios/certificates");
                certificate.setCertificateImageUrl((String) uploadResult.get("secure_url"));
                certificate.setCertificateImagePublicId((String) uploadResult.get("public_id"));
            } catch (IOException e) {
                log.error("Failed to upload certificate image", e);
            }
        }

        certificate = certificateRepository.save(certificate);
        
        // Update certificate count in extended profile
        updateExtendedProfileCertificateCount(userId);
        
        return mapToCertificateDTO(certificate);
    }

    @Transactional(readOnly = true)
    public List<ExternalCertificateDTO> getUserCertificates(Long userId) {
        return certificateRepository.findByUserIdOrderByIssueDateDesc(userId)
                .stream()
                .map(this::mapToCertificateDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ExternalCertificateDTO> getPublicUserCertificates(Long userId) {
        return getUserCertificates(userId);
    }

    @Transactional
    public void deleteCertificate(Long certificateId, Long userId) {
        ExternalCertificate certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new NotFoundException("Certificate not found: " + certificateId));

        if (!certificate.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized to delete this certificate");
        }

        // Delete image from Cloudinary
        if (certificate.getCertificateImagePublicId() != null) {
            try {
                cloudinaryService.deleteFile(certificate.getCertificateImagePublicId(), "image");
            } catch (IOException e) {
                log.error("Failed to delete certificate image", e);
            }
        }

        certificateRepository.delete(certificate);
        
        // Update certificate count in extended profile
        updateExtendedProfileCertificateCount(userId);
    }

    @Transactional(readOnly = true)
    public List<MentorReviewDTO> getUserReviews(Long userId) {
        return reviewRepository.findByUserIdAndIsPublicTrueOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToReviewDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MentorReviewDTO> getPublicUserReviews(Long userId) {
        return getUserReviews(userId);
    }

    // ==================== CV GENERATION ====================

    @Transactional
    public GeneratedCVDTO generateCV(Long userId, CVGenerationRequest request) {
        User user = getUserOrThrow(userId);

        // Get portfolio data
        UserProfileDTO profile = getProfile(userId);
        List<PortfolioProjectDTO> projects = getUserProjects(userId);
        List<ExternalCertificateDTO> certificates = getUserCertificates(userId);
        List<MentorReviewDTO> reviews = getUserReviews(userId);

        // Generate CV using AI
        String cvContent = cvGeneratorAIService.generateCV(profile, projects, certificates, reviews, request);
        String cvJson = cvGeneratorAIService.generateCVJson(profile, projects, certificates, reviews);

        // Deactivate previous active CVs
        cvRepository.findByUserIdAndIsActiveTrue(userId).ifPresent(oldCv -> {
            oldCv.setIsActive(false);
            cvRepository.save(oldCv);
        });

        // Get next version number
        long cvCount = cvRepository.countByUserId(userId);
        int nextVersion = (int) cvCount + 1;

        // Save new CV
        GeneratedCV cv = GeneratedCV.builder()
                .user(user)
                .cvContent(cvContent)
                .cvJson(cvJson)
                .templateName(request.getTemplateName())
                .isActive(true)
                .version(nextVersion)
                .generatedByAi(true)
                .aiPrompt(buildPromptSummary(request))
                .build();

        cv = cvRepository.save(cv);
        return mapToCVDTO(cv);
    }

    @Transactional
    public GeneratedCVDTO updateCV(Long cvId, Long userId, String cvContent, String cvJson) {
        GeneratedCV cv = cvRepository.findById(cvId)
                .orElseThrow(() -> new NotFoundException("CV not found: " + cvId));

        if (!cv.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized to update this CV");
        }

        cv.setCvContent(cvContent);
        cv.setCvJson(cvJson);
        cv = cvRepository.save(cv);

        return mapToCVDTO(cv);
    }

    @Transactional(readOnly = true)
    public GeneratedCVDTO getActiveCV(Long userId) {
        GeneratedCV cv = cvRepository.findByUserIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new NotFoundException("No active CV found for user: " + userId));
        return mapToCVDTO(cv);
    }

    @Transactional(readOnly = true)
    public List<GeneratedCVDTO> getAllUserCVs(Long userId) {
        return cvRepository.findByUserIdOrderByVersionDesc(userId)
                .stream()
                .map(this::mapToCVDTO)
                .collect(Collectors.toList());
    }

    // ==================== HELPER METHODS ====================

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    }

    private String buildPromptSummary(CVGenerationRequest request) {
        return String.format("Template: %s, Role: %s, Industry: %s",
                request.getTemplateName(),
                request.getTargetRole(),
                request.getTargetIndustry());
    }

    /**
     * Update project count in extended profile
     */
    private void updateExtendedProfileProjectCount(Long userId) {
        extendedProfileRepository.findByUserId(userId).ifPresent(profile -> {
            int count = (int) projectRepository.countByUserId(userId);
            profile.updateProjectCount(count);
            extendedProfileRepository.save(profile);
        });
    }

    /**
     * Update certificate count in extended profile
     */
    private void updateExtendedProfileCertificateCount(Long userId) {
        extendedProfileRepository.findByUserId(userId).ifPresent(profile -> {
            int count = (int) certificateRepository.countByUserId(userId);
            profile.updateCertificateCount(count);
            extendedProfileRepository.save(profile);
        });
    }

    // ==================== MAPPING METHODS ====================

    /**
     * Map combined profile from basic UserProfile and PortfolioExtendedProfile
     */
    private UserProfileDTO mapToCombinedProfileDTO(PortfolioExtendedProfile extendedProfile) {
        UserProfileDTO.UserProfileDTOBuilder builder = UserProfileDTO.builder();
        
        // Map extended profile data (from portfolio_service)
        if (extendedProfile != null) {
            builder.userId(extendedProfile.getUserId());
            builder.fullName(extendedProfile.getFullName());
            builder.basicBio(extendedProfile.getBio());
            builder.phone(extendedProfile.getPhone());
            builder.address(extendedProfile.getAddress());
            builder.region(extendedProfile.getRegion());
            builder.companyId(extendedProfile.getCompanyId());
            builder.socialLinks(extendedProfile.getSocialLinks());
            builder.basicAvatarUrl(extendedProfile.getAvatarUrl());
            
            builder.professionalTitle(extendedProfile.getProfessionalTitle())
                   .careerGoals(extendedProfile.getCareerGoals())
                   .yearsOfExperience(extendedProfile.getYearsOfExperience())
                   .portfolioAvatarUrl(extendedProfile.getAvatarUrl())
                   .videoIntroUrl(extendedProfile.getVideoIntroUrl())
                   .coverImageUrl(extendedProfile.getCoverImageUrl())
                   .linkedinUrl(extendedProfile.getLinkedinUrl())
                   .githubUrl(extendedProfile.getGithubUrl())
                   .portfolioWebsiteUrl(extendedProfile.getPortfolioWebsiteUrl())
                   .behanceUrl(extendedProfile.getBehanceUrl())
                   .dribbbleUrl(extendedProfile.getDribbbleUrl())
                   .tagline(extendedProfile.getTagline())
                   .location(extendedProfile.getLocation())
                   .availabilityStatus(extendedProfile.getAvailabilityStatus())
                   .hourlyRate(extendedProfile.getHourlyRate())
                   .preferredCurrency(extendedProfile.getPreferredCurrency())
                   .topSkills(extendedProfile.getTopSkills())
                   .languagesSpoken(extendedProfile.getLanguagesSpoken())
                   .isPublic(extendedProfile.getIsPublic())
                   .showContactInfo(extendedProfile.getShowContactInfo())
                   .allowJobOffers(extendedProfile.getAllowJobOffers())
                   .themePreference(extendedProfile.getThemePreference())
                   .portfolioViews(extendedProfile.getPortfolioViews())
                   .totalProjects(extendedProfile.getTotalProjects())
                   .totalCertificates(extendedProfile.getTotalCertificates())
                   .customUrlSlug(extendedProfile.getCustomUrlSlug())
                   .metaDescription(extendedProfile.getMetaDescription())
                   .keywords(extendedProfile.getKeywords())
                   .createdAt(extendedProfile.getCreatedAt())
                   .updatedAt(extendedProfile.getUpdatedAt());
        }
        
        return builder.build();
    }

    private PortfolioProjectDTO mapToProjectDTO(PortfolioProject project) {
        return PortfolioProjectDTO.builder()
                .id(project.getId())
                .userId(project.getUser().getId())
                .title(project.getTitle())
                .description(project.getDescription())
                .clientName(project.getClientName())
                .projectType(project.getProjectType())
                .duration(project.getDuration())
                .completionDate(project.getCompletionDate())
                .tools(project.getTools())
                .outcomes(project.getOutcomes())
                .rating(project.getRating())
                .clientFeedback(project.getClientFeedback())
                .projectUrl(project.getProjectUrl())
                .githubUrl(project.getGithubUrl())
                .thumbnailUrl(project.getThumbnailUrl())
                .isFeatured(project.getIsFeatured())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    private ExternalCertificateDTO mapToCertificateDTO(ExternalCertificate cert) {
        return ExternalCertificateDTO.builder()
                .id(cert.getId())
                .userId(cert.getUser().getId())
                .title(cert.getTitle())
                .issuingOrganization(cert.getIssuingOrganization())
                .issueDate(cert.getIssueDate())
                .expiryDate(cert.getExpiryDate())
                .credentialId(cert.getCredentialId())
                .credentialUrl(cert.getCredentialUrl())
                .description(cert.getDescription())
                .certificateImageUrl(cert.getCertificateImageUrl())
                .skills(cert.getSkills())
                .category(cert.getCategory())
                .isVerified(cert.getIsVerified())
                .createdAt(cert.getCreatedAt())
                .updatedAt(cert.getUpdatedAt())
                .build();
    }

    private MentorReviewDTO mapToReviewDTO(MentorReview review) {
        return MentorReviewDTO.builder()
                .id(review.getId())
                .userId(review.getUser().getId())
                .mentorId(review.getMentor().getId())
                .mentorName(review.getMentor().getFirstName() + " " + review.getMentor().getLastName())
                .feedback(review.getFeedback())
                .skillEndorsed(review.getSkillEndorsed())
                .rating(review.getRating())
                .isVerified(review.getIsVerified())
                .isPublic(review.getIsPublic())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }

    @Transactional
    public GeneratedCVDTO setActiveCV(Long userId, Long cvId) {
        // Deactivate previous active CVs
        cvRepository.findByUserIdAndIsActiveTrue(userId).ifPresent(oldCv -> {
            oldCv.setIsActive(false);
            cvRepository.save(oldCv);
        });

        // Set new CV as active
        GeneratedCV newActiveCv = cvRepository.findById(cvId)
                .orElseThrow(() -> new RuntimeException("CV not found: " + cvId));
        if (!newActiveCv.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized to set this CV as active");
        }
        newActiveCv.setIsActive(true);
        newActiveCv = cvRepository.save(newActiveCv);
        return mapToCVDTO(newActiveCv);
    }

    @Transactional
    public void deleteCV(Long cvId, Long userId) {
        GeneratedCV cv = cvRepository.findById(cvId)
                .orElseThrow(() -> new RuntimeException("CV not found: " + cvId));
        
        if (!cv.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized to delete this CV");
        }
        
        cvRepository.delete(cv);
    }

    private GeneratedCVDTO mapToCVDTO(GeneratedCV cv) {
        return GeneratedCVDTO.builder()
                .id(cv.getId())
                .userId(cv.getUser().getId())
                .cvContent(cv.getCvContent())
                .cvJson(cv.getCvJson())
                .templateName(cv.getTemplateName())
                .isActive(cv.getIsActive())
                .version(cv.getVersion())
                .generatedByAi(cv.getGeneratedByAi())
                .pdfUrl(cv.getPdfUrl())
                .createdAt(cv.getCreatedAt())
                .updatedAt(cv.getUpdatedAt())
                .build();
    }
}
