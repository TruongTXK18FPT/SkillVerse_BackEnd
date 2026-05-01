package com.exe.skillverse_backend.portfolio_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.portfolio_service.dto.AIEnhanceRequest;
import com.exe.skillverse_backend.portfolio_service.dto.AIEnhanceResponse;
import com.exe.skillverse_backend.portfolio_service.dto.CVGenerationRequest;
import com.exe.skillverse_backend.portfolio_service.dto.CompletedMissionDTO;
import com.exe.skillverse_backend.portfolio_service.dto.ExternalCertificateDTO;
import com.exe.skillverse_backend.portfolio_service.dto.GeneratedCVDTO;
import com.exe.skillverse_backend.portfolio_service.dto.MentorReviewDTO;
import com.exe.skillverse_backend.portfolio_service.dto.PortfolioVerifiedSkillDetailDTO;
import com.exe.skillverse_backend.portfolio_service.dto.PortfolioVerifiedSkillEvidenceDTO;
import com.exe.skillverse_backend.portfolio_service.dto.PortfolioEducationDTO;
import com.exe.skillverse_backend.portfolio_service.dto.PortfolioProjectDTO;
import com.exe.skillverse_backend.portfolio_service.dto.PortfolioWorkExperienceDTO;
import com.exe.skillverse_backend.portfolio_service.dto.SystemCertificateDTO;
import com.exe.skillverse_backend.portfolio_service.dto.UserProfileDTO;
import com.exe.skillverse_backend.portfolio_service.entity.ExternalCertificate;
import com.exe.skillverse_backend.portfolio_service.entity.GeneratedCV;
import com.exe.skillverse_backend.portfolio_service.entity.MentorReview;
import com.exe.skillverse_backend.portfolio_service.entity.PortfolioExtendedProfile;
import com.exe.skillverse_backend.portfolio_service.entity.PortfolioProject;
import com.exe.skillverse_backend.portfolio_service.repository.ExternalCertificateRepository;
import com.exe.skillverse_backend.portfolio_service.repository.GeneratedCVRepository;
import com.exe.skillverse_backend.portfolio_service.repository.MentorReviewRepository;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioExtendedProfileRepository;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioProjectRepository;
import com.exe.skillverse_backend.portfolio_service.service.CVGeneratorAIService;
import com.exe.skillverse_backend.portfolio_service.service.CVMapperService;
import com.exe.skillverse_backend.portfolio_service.service.PortfolioService;
import com.exe.skillverse_backend.course_service.repository.CertificateRepository;
import com.exe.skillverse_backend.gamification_service.entity.GamificationUserBadge;
import com.exe.skillverse_backend.gamification_service.repository.GamificationUserBadgeRepository;
import com.exe.skillverse_backend.business_service.entity.ShortTermJobApplication;
import com.exe.skillverse_backend.business_service.entity.JobDeliverable;
import com.exe.skillverse_backend.business_service.entity.JobReview;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobApplicationRepository;
import com.exe.skillverse_backend.business_service.repository.JobReviewRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyOutputAssessment;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeSubmission;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.VerificationEvidenceReport;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.JourneyOutputAssessmentRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.RoadmapNodeSubmissionRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.VerificationEvidenceReportRepository;
import com.exe.skillverse_backend.mentor_verification_service.entity.MentorSkillVerificationRequest;
import com.exe.skillverse_backend.mentor_verification_service.entity.MentorVerificationEvidence;
import com.exe.skillverse_backend.mentor_verification_service.repository.MentorSkillVerificationRequestRepository;
import com.exe.skillverse_backend.shared.exception.ConflictException;
import com.exe.skillverse_backend.shared.exception.ForbiddenException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioServiceImpl implements PortfolioService {
    private static final Pattern CUSTOM_SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Set<String> RESERVED_CUSTOM_SLUGS = Set.of("create");
    private static final String SUPPORTED_PREFERRED_CURRENCY = "VND";
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Extended portfolio profile
    private final PortfolioExtendedProfileRepository extendedProfileRepository;

    // Portfolio entities
    private final PortfolioProjectRepository projectRepository;
    private final MentorReviewRepository reviewRepository;
    private final ExternalCertificateRepository externalCertificateRepository;
    private final GeneratedCVRepository cvRepository;

    // Other dependencies
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;
    private final CVGeneratorAIService cvGeneratorAIService;
    private final CVMapperService cvMapperService;
    private final CertificateRepository courseCertificateRepository;
    private final GamificationUserBadgeRepository badgeRepository;
    private final ShortTermJobApplicationRepository jobApplicationRepository;
    private final JobReviewRepository jobReviewRepository;
    private final com.exe.skillverse_backend.portfolio_service.repository.UserVerifiedSkillRepository verifiedSkillRepository;
    private final MentorSkillVerificationRequestRepository mentorVerificationRequestRepository;
    private final VerificationEvidenceReportRepository verificationEvidenceReportRepository;
    private final JourneyOutputAssessmentRepository journeyOutputAssessmentRepository;
    private final RoadmapNodeSubmissionRepository roadmapNodeSubmissionRepository;

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
            throw new ConflictException("Portfolio extended profile already exists for user: " + userId);
        }

        // Create new extended profile
        PortfolioExtendedProfile extendedProfile = PortfolioExtendedProfile.builder()
                .user(user)
                .build();

        // Upload and set fields
        extendedProfile = uploadMediaAndSetFields(extendedProfile, dto, avatarFile, videoFile, coverImageFile, userId);
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
        extendedProfile = uploadMediaAndSetFields(extendedProfile, dto, avatarFile, videoFile, coverImageFile, userId);
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
            MultipartFile coverImageFile,
            Long currentUserId) {

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
        if (dto.getFullName() != null)
            extendedProfile.setFullName(dto.getFullName());
        if (dto.getBasicBio() != null)
            extendedProfile.setBio(dto.getBasicBio());
        if (dto.getPhone() != null)
            extendedProfile.setPhone(dto.getPhone());
        if (dto.getAddress() != null)
            extendedProfile.setAddress(dto.getAddress());
        if (dto.getRegion() != null)
            extendedProfile.setRegion(dto.getRegion());
        if (dto.getCompanyId() != null)
            extendedProfile.setCompanyId(dto.getCompanyId());
        if (dto.getSocialLinks() != null)
            extendedProfile.setSocialLinks(dto.getSocialLinks());
        if (dto.getProfessionalTitle() != null)
            extendedProfile.setProfessionalTitle(dto.getProfessionalTitle());
        if (dto.getCareerGoals() != null)
            extendedProfile.setCareerGoals(dto.getCareerGoals());
        if (dto.getYearsOfExperience() != null)
            extendedProfile.setYearsOfExperience(dto.getYearsOfExperience());
        if (dto.getWorkExperiences() != null)
            extendedProfile.setWorkExperiences(writeJson(dto.getWorkExperiences()));
        if (dto.getEducationHistory() != null)
            extendedProfile.setEducationHistory(writeJson(dto.getEducationHistory()));
        if (dto.getLinkedinUrl() != null)
            extendedProfile.setLinkedinUrl(dto.getLinkedinUrl());
        if (dto.getGithubUrl() != null)
            extendedProfile.setGithubUrl(dto.getGithubUrl());
        if (dto.getPortfolioWebsiteUrl() != null)
            extendedProfile.setPortfolioWebsiteUrl(dto.getPortfolioWebsiteUrl());
        if (dto.getBehanceUrl() != null)
            extendedProfile.setBehanceUrl(dto.getBehanceUrl());
        if (dto.getDribbbleUrl() != null)
            extendedProfile.setDribbbleUrl(dto.getDribbbleUrl());
        if (dto.getTagline() != null)
            extendedProfile.setTagline(dto.getTagline());
        if (dto.getLocation() != null)
            extendedProfile.setLocation(dto.getLocation());
        if (dto.getAvailabilityStatus() != null)
            extendedProfile.setAvailabilityStatus(dto.getAvailabilityStatus());
        if (dto.getHourlyRate() != null)
            extendedProfile.setHourlyRate(dto.getHourlyRate());
        if (dto.getRoadmapMentoringPrice() != null)
            extendedProfile.setRoadmapMentoringPrice(dto.getRoadmapMentoringPrice());
        if (dto.getPreferredCurrency() != null) {
            validatePreferredCurrency(dto.getPreferredCurrency());
            extendedProfile.setPreferredCurrency(dto.getPreferredCurrency().trim().toUpperCase());
        }
        if (dto.getTopSkills() != null)
            extendedProfile.setTopSkills(dto.getTopSkills());
        if (dto.getLanguagesSpoken() != null)
            extendedProfile.setLanguagesSpoken(dto.getLanguagesSpoken());
        if (dto.getIsPublic() != null)
            extendedProfile.setIsPublic(dto.getIsPublic());
        if (dto.getShowContactInfo() != null)
            extendedProfile.setShowContactInfo(dto.getShowContactInfo());
        if (dto.getAllowJobOffers() != null)
            extendedProfile.setAllowJobOffers(dto.getAllowJobOffers());
        if (dto.getThemePreference() != null)
            extendedProfile.setThemePreference(dto.getThemePreference());
        if (dto.getCustomUrlSlug() != null) {
            String normalizedSlug = normalizeAndValidateCustomUrlSlug(dto.getCustomUrlSlug(), currentUserId);
            extendedProfile.setCustomUrlSlug(normalizedSlug);
        }
        if (dto.getMetaDescription() != null)
            extendedProfile.setMetaDescription(dto.getMetaDescription());
        if (dto.getKeywords() != null)
            extendedProfile.setKeywords(dto.getKeywords());
        if (dto.getAchievements() != null)
            extendedProfile.setAchievements(dto.getAchievements());

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

    @Transactional(readOnly = true)
    @Override
    public UserProfileDTO getPublicProfile(Long userId) {
        PortfolioExtendedProfile extendedProfile = getPublicExtendedProfileOrThrow(userId);
        UserProfileDTO profile = mapToCombinedProfileDTO(extendedProfile);
        return applyPublicVisibility(profile);
    }

    /**
     * Get profile by custom URL slug (for public portfolio pages)
     */
    @Transactional
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

        UserProfileDTO profile = mapToCombinedProfileDTO(extendedProfile);
        return applyPublicVisibility(profile);
    }

    /**
     * Get all public portfolios
     */
    @Transactional(readOnly = true)
    public List<UserProfileDTO> getAllPublicPortfolios() {
        return extendedProfileRepository.findByIsPublicTrue()
                .stream()
                .map(this::mapToCombinedProfileDTO)
                .map(this::applyPublicVisibility)
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
    public PortfolioProjectDTO updateProject(Long projectId, Long userId, PortfolioProjectDTO dto,
            MultipartFile thumbnailFile) {
        PortfolioProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        if (!project.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Unauthorized to update this project");
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
        getPublicExtendedProfileOrThrow(userId);
        // For now, return all projects. In future, might filter by isPublic if projects
        // have that flag.
        return getUserProjects(userId);
    }

    @Transactional
    public void deleteProject(Long projectId, Long userId) {
        PortfolioProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        if (!project.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Unauthorized to delete this project");
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
    public ExternalCertificateDTO createCertificate(Long userId, ExternalCertificateDTO dto,
            MultipartFile certificateImage) {
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
                Map<String, Object> uploadResult = cloudinaryService.uploadImage(certificateImage,
                        "portfolios/certificates");
                certificate.setCertificateImageUrl((String) uploadResult.get("secure_url"));
                certificate.setCertificateImagePublicId((String) uploadResult.get("public_id"));
            } catch (IOException e) {
                log.error("Failed to upload certificate image", e);
            }
        }

        certificate = externalCertificateRepository.save(certificate);

        // Update certificate count in extended profile
        updateExtendedProfileCertificateCount(userId);

        return mapToCertificateDTO(certificate);
    }

    @Transactional(readOnly = true)
    public List<ExternalCertificateDTO> getUserCertificates(Long userId) {
        return externalCertificateRepository.findByUserIdOrderByIssueDateDesc(userId)
                .stream()
                .map(this::mapToCertificateDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ExternalCertificateDTO> getPublicUserCertificates(Long userId) {
        getPublicExtendedProfileOrThrow(userId);
        return getUserCertificates(userId);
    }

    // ==================== SYSTEM CERTIFICATES (AUTO-IMPORT) ====================

    @Transactional(readOnly = true)
    public List<SystemCertificateDTO> getSystemCertificates(Long userId) {
        List<SystemCertificateDTO> result = new ArrayList<>();

        // 1. Course completion certificates
        var courseCerts = courseCertificateRepository.findActiveByUserId(userId);
        for (var cert : courseCerts) {
            String serial = cert.getSerial();
            boolean imported = externalCertificateRepository.existsByCredentialId(serial);
            result.add(SystemCertificateDTO.builder()
                    .id(cert.getId())
                    .source("COURSE")
                    .title(cert.getCourseTitleSnapshot())
                    .issuer("SkillVerse")
                    .issueDate(cert.getIssuedAt() != null ? cert.getIssuedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate() : null)
                    .credentialId(serial)
                    .credentialUrl("/api/certificates/verify/" + serial)
                    .category("TECHNICAL")
                    .imageUrl(cert.getInstructorSignatureUrlSnapshot())
                    .imported(imported)
                    .build());
        }

        // 2. Gamification badges
        var badges = badgeRepository.findByUserIdOrderByEarnedAtDesc(userId);
        for (var badge : badges) {
            String badgeKey = badge.getBadgeDefinition() != null
                    ? badge.getBadgeDefinition().getBadgeKey() : null;
            String serial = "BADGE-" + userId + "-" + badge.getBadgeDefId();
            boolean imported = externalCertificateRepository.existsByCredentialId(serial);
            String rarity = badge.getBadgeDefinition() != null
                    ? badge.getBadgeDefinition().getBadgeRarity() : null;

            String title = badge.getBadgeDefinition() != null
                    ? badge.getBadgeDefinition().getBadgeTitle() : "Badge #" + badge.getBadgeDefId();

            List<String> skills = new ArrayList<>();
            skills.add(badge.getBadgeDefinition() != null
                    ? badge.getBadgeDefinition().getBadgeCategory() : "Achievement");

            result.add(SystemCertificateDTO.builder()
                    .id(badge.getUserBadgeId())
                    .source("BADGE")
                    .title(title)
                    .issuer("SkillVerse")
                    .issueDate(badge.getEarnedAt() != null ? badge.getEarnedAt().toLocalDate() : null)
                    .credentialId(serial)
                    .badgeKey(badgeKey)
                    .badgeRarity(rarity)
                    .category("SOFT_SKILLS")
                    .skills(skills)
                    .imported(imported)
                    .build());
        }

        return result;
    }

    @Transactional
    public List<SystemCertificateDTO> importSystemCertificates(Long userId, String source) {
        User user = getUserOrThrow(userId);
        List<SystemCertificateDTO> imported = new ArrayList<>();

        if ("COURSE".equalsIgnoreCase(source) || "ALL".equalsIgnoreCase(source)) {
            var courseCerts = courseCertificateRepository.findActiveByUserId(userId);
            for (var cert : courseCerts) {
                String serial = cert.getSerial();
                if (!externalCertificateRepository.existsByCredentialId(serial)) {
                    ExternalCertificate extCert = ExternalCertificate.builder()
                            .user(user)
                            .title(cert.getCourseTitleSnapshot() + " - Certificate of Completion")
                            .issuingOrganization("SkillVerse")
                            .issueDate(cert.getIssuedAt() != null ? cert.getIssuedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate() : null)
                            .credentialId(serial)
                            .credentialUrl("/api/certificates/verify/" + serial)
                            .category(ExternalCertificate.CertificateCategory.TECHNICAL)
                            .isVerified(true)
                            .build();
                    externalCertificateRepository.save(extCert);
                }
            }
        }

        if ("BADGE".equalsIgnoreCase(source) || "ALL".equalsIgnoreCase(source)) {
            var badges = badgeRepository.findByUserIdOrderByEarnedAtDesc(userId);
            for (var badge : badges) {
                String serial = "BADGE-" + userId + "-" + badge.getBadgeDefId();
                if (!externalCertificateRepository.existsByCredentialId(serial)) {
                    String title = badge.getBadgeDefinition() != null
                            ? badge.getBadgeDefinition().getBadgeTitle() : "Achievement Badge";

                    List<String> skills = new ArrayList<>();
                    if (badge.getBadgeDefinition() != null) {
                        skills.add(badge.getBadgeDefinition().getBadgeCategory());
                    }

                    ExternalCertificate extCert = ExternalCertificate.builder()
                            .user(user)
                            .title(title)
                            .issuingOrganization("SkillVerse")
                            .issueDate(badge.getEarnedAt() != null ? badge.getEarnedAt().toLocalDate() : null)
                            .credentialId(serial)
                            .category(ExternalCertificate.CertificateCategory.SOFT_SKILLS)
                            .skills(skills)
                            .isVerified(true)
                            .build();
                    externalCertificateRepository.save(extCert);
                }
            }
        }

        updateExtendedProfileCertificateCount(userId);
        return getSystemCertificates(userId);
    }

    // ==================== COMPLETED MISSIONS (SHORT-TERM JOBS) ====================

    @Transactional(readOnly = true)
    public List<CompletedMissionDTO> getCompletedMissions(Long userId) {
        // Just verify user exists - owner doesn't need a portfolio to see their completed missions
        getUserOrThrow(userId);
        return buildCompletedMissionDTOs(userId);
    }

    @Transactional(readOnly = true)
    public List<CompletedMissionDTO> getPublicCompletedMissions(Long userId) {
        // Only show missions if the user has a public portfolio
        getPublicExtendedProfileOrThrow(userId);
        return buildCompletedMissionDTOs(userId);
    }

    private List<CompletedMissionDTO> buildCompletedMissionDTOs(Long userId) {
        List<ShortTermJobApplication> applications =
                jobApplicationRepository.findCompletedByUserIdWithDeliverables(userId);

        return applications.stream().map(app -> {
            var shortTermJob = app.getShortTermJob();

            // Recruiter info
            String recruiterName = "Recruiter";
            String recruiterAvatar = null;
            String recruiterCompanyName = null;
            if (shortTermJob != null && shortTermJob.getRecruiterProfile() != null) {
                var recruiterProfile = shortTermJob.getRecruiterProfile();
                if (recruiterProfile.getUser() != null) {
                    var recruiter = recruiterProfile.getUser();
                    recruiterName = recruiter.getFirstName() + " " + recruiter.getLastName();
                }
                recruiterCompanyName = recruiterProfile.getCompanyName();
            }

            // Budget
            BigDecimal budget = app.getProposedPrice();
            if (budget == null && shortTermJob != null) {
                budget = shortTermJob.getBudget();
            }

            // Required skills from job
            List<String> requiredSkills = new ArrayList<>();
            if (shortTermJob != null && shortTermJob.getRequiredSkills() != null) {
                try {
                    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    requiredSkills = mapper.readValue(shortTermJob.getRequiredSkills(),
                            mapper.getTypeFactory().constructCollectionType(List.class, String.class));
                } catch (Exception e) {
                    // ignore parse errors
                }
            }

            // Deliverables
            List<CompletedMissionDTO.DeliverableInfo> deliverables = new ArrayList<>();
            List<String> deliverableTitles = new ArrayList<>();
            if (app.getDeliverables() != null) {
                for (var d : app.getDeliverables()) {
                    deliverables.add(CompletedMissionDTO.DeliverableInfo.builder()
                            .fileName(d.getFileName())
                            .fileUrl(d.getFileUrl())
                            .type(d.getType() != null ? d.getType().name() : null)
                            .build());
                    deliverableTitles.add(d.getFileName());
                }
            }

            // Rating from JobReview (recruiter reviews candidate)
            Double rating = null;
            String reviewComment = null;
            Integer communicationRating = null;
            Integer qualityRating = null;
            Integer timelinessRating = null;
            Integer professionalismRating = null;
            var reviews = jobReviewRepository.findByApplicationId(app.getId());
            for (var review : reviews) {
                if (review.getReviewType() == JobReview.ReviewType.RECRUITER_TO_CANDIDATE
                        && review.getReviewee() != null
                        && review.getReviewee().getId().equals(userId)) {
                    rating = review.getRating() != null ? review.getRating().doubleValue() : null;
                    reviewComment = review.getComment();
                    communicationRating = review.getCommunicationRating();
                    qualityRating = review.getQualityRating();
                    timelinessRating = review.getTimelinessRating();
                    professionalismRating = review.getProfessionalismRating();
                    break;
                }
            }

            return CompletedMissionDTO.builder()
                    .applicationId(app.getId())
                    .jobId(shortTermJob != null ? shortTermJob.getId() : null)
                    .jobTitle(shortTermJob != null ? shortTermJob.getTitle() : "Completed Mission")
                    .jobDescription(shortTermJob != null ? shortTermJob.getDescription() : null)
                    .recruiterName(recruiterName)
                    .recruiterAvatar(recruiterAvatar)
                    .recruiterCompanyName(recruiterCompanyName)
                    .budget(budget)
                    .currency("VND")
                    .deadline(shortTermJob != null && shortTermJob.getDeadline() != null ? shortTermJob.getDeadline().toLocalDate() : null)
                    .estimatedDuration(shortTermJob != null ? shortTermJob.getEstimatedDuration() : null)
                    .isRemote(shortTermJob != null ? shortTermJob.getIsRemote() : null)
                    .location(shortTermJob != null ? shortTermJob.getLocation() : null)
                    .requiredSkills(requiredSkills)
                    .paymentMethod(shortTermJob != null && shortTermJob.getPaymentMethod() != null ? shortTermJob.getPaymentMethod().name() : null)
                    .completedAt(app.getCompletedAt())
                    .rating(rating)
                    .reviewComment(reviewComment)
                    .communicationRating(communicationRating)
                    .qualityRating(qualityRating)
                    .timelinessRating(timelinessRating)
                    .professionalismRating(professionalismRating)
                    .deliverables(deliverables)
                    .status(app.getStatus() != null ? app.getStatus().name() : "COMPLETED")
                    .workNote(app.getWorkNote())
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional
    public void deleteCertificate(Long certificateId, Long userId) {
        ExternalCertificate certificate = externalCertificateRepository.findById(certificateId)
                .orElseThrow(() -> new NotFoundException("Certificate not found: " + certificateId));

        if (!certificate.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Unauthorized to delete this certificate");
        }

        // Delete image from Cloudinary
        if (certificate.getCertificateImagePublicId() != null) {
            try {
                cloudinaryService.deleteFile(certificate.getCertificateImagePublicId(), "image");
            } catch (IOException e) {
                log.error("Failed to delete certificate image", e);
            }
        }

        externalCertificateRepository.delete(certificate);

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
        getPublicExtendedProfileOrThrow(userId);
        return reviewRepository.findByUserIdAndIsPublicTrueOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToReviewDTO)
                .collect(Collectors.toList());
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
        List<CompletedMissionDTO> completedMissions = Boolean.TRUE.equals(request.getIncludeCompletedMissions())
                ? getCompletedMissions(userId)
                : List.of();

        // Generate structured CV JSON using AI
        String cvJson = cvGeneratorAIService.generateCV(
                profile,
                projects,
                certificates,
                reviews,
                completedMissions,
                request);

        // Deactivate previous active CVs
        cvRepository.findByUserIdAndIsActiveTrue(userId).ifPresent(oldCv -> {
            oldCv.setIsActive(false);
            cvRepository.save(oldCv);
        });

        // Get next version number
        long cvCount = cvRepository.countByUserId(userId);
        int nextVersion = (int) cvCount + 1;

        // Save new CV — cvJson holds AI-generated structured data,
        // cvContent kept empty (frontend renders via React templates)
        GeneratedCV cv = GeneratedCV.builder()
                .user(user)
                .cvContent("")
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

    /**
     * Export CV directly from portfolio data WITHOUT using AI.
     * Maps portfolio data to CV structure for manual editing.
     */
    @Override
    @Transactional
    public GeneratedCVDTO exportCV(Long userId, CVGenerationRequest request) {
        User user = getUserOrThrow(userId);

        // Get portfolio data
        UserProfileDTO profile = getProfile(userId);
        List<PortfolioProjectDTO> projects = getUserProjects(userId);
        List<ExternalCertificateDTO> certificates = getUserCertificates(userId);
        List<MentorReviewDTO> reviews = getUserReviews(userId);
        List<CompletedMissionDTO> completedMissions = Boolean.TRUE.equals(request.getIncludeCompletedMissions())
                ? getCompletedMissions(userId)
                : List.of();

        // Use CVMapperService to map data without AI
        CVMapperService.CVStructuredData cvData = cvMapperService.mapPortfolioToCV(
                profile, projects, certificates, reviews, completedMissions, request);

        // Convert to JSON
        String cvJson;
        try {
            cvJson = objectMapper.writeValueAsString(cvData);
        } catch (Exception e) {
            log.error("Error converting CV data to JSON", e);
            throw new RuntimeException("Failed to export CV: " + e.getMessage(), e);
        }

        // Deactivate previous active CVs
        cvRepository.findByUserIdAndIsActiveTrue(userId).ifPresent(oldCv -> {
            oldCv.setIsActive(false);
            cvRepository.save(oldCv);
        });

        // Get next version number
        long cvCount = cvRepository.countByUserId(userId);
        int nextVersion = (int) cvCount + 1;

        // Save new CV - marked as NOT generated by AI
        GeneratedCV cv = GeneratedCV.builder()
                .user(user)
                .cvContent("")
                .cvJson(cvJson)
                .templateName(request.getTemplateName())
                .isActive(true)
                .version(nextVersion)
                .generatedByAi(false)  // This CV is exported from portfolio, not AI-generated
                .aiPrompt("Exported from portfolio data (no AI)")
                .build();

        cv = cvRepository.save(cv);
        log.info("Exported CV from portfolio for user: {} (no AI)", userId);
        return mapToCVDTO(cv);
    }

    /**
     * Enhance a specific CV section using AI.
     */
    @Override
    @Transactional
    public AIEnhanceResponse enhanceCVSection(Long userId, AIEnhanceRequest request) {
        // Verify user exists
        getUserOrThrow(userId);

        log.info("User {} requesting AI enhancement for section: {}", userId, request.getSection());

        // Set userId for token usage tracking
        request.setUserId(userId);

        // Call AI service to enhance the section
        return cvGeneratorAIService.enhanceSection(request);
    }

    @Transactional
    public GeneratedCVDTO updateCV(Long cvId, Long userId, String cvContent, String cvJson) {
        GeneratedCV cv = cvRepository.findById(cvId)
                .orElseThrow(() -> new NotFoundException("CV not found: " + cvId));

        if (!cv.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Unauthorized to update this CV");
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

    private PortfolioExtendedProfile getPublicExtendedProfileOrThrow(Long userId) {
        return extendedProfileRepository.findByUserId(userId)
                .filter(profile -> Boolean.TRUE.equals(profile.getIsPublic()))
                .orElseThrow(() -> new NotFoundException("Public portfolio not found"));
    }

    private UserProfileDTO applyPublicVisibility(UserProfileDTO profile) {
        if (profile == null) {
            return null;
        }
        if (Boolean.TRUE.equals(profile.getShowContactInfo())) {
            return profile;
        }

        profile.setEmail(null);
        profile.setPhone(null);
        profile.setAddress(null);
        profile.setRegion(null);
        profile.setSocialLinks(null);
        profile.setLinkedinUrl(null);
        profile.setGithubUrl(null);
        profile.setPortfolioWebsiteUrl(null);
        profile.setBehanceUrl(null);
        profile.setDribbbleUrl(null);
        return profile;
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
            int count = (int) externalCertificateRepository.countByUserId(userId);
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
            User user = extendedProfile.getUser();
            String displayName = extendedProfile.getFullName();
            if ((displayName == null || displayName.isBlank()) && user != null) {
                displayName = user.getFullName();
            }
            String phone = extendedProfile.getPhone();
            if ((phone == null || phone.isBlank()) && user != null) {
                phone = user.getPhoneNumber();
            }

            builder.userId(extendedProfile.getUserId());
            builder.fullName(displayName);
            builder.email(user != null ? user.getEmail() : null);
            builder.primaryRole(user != null && user.getPrimaryRole() != null
                    ? user.getPrimaryRole().name() : null);
            builder.basicBio(extendedProfile.getBio());
            builder.phone(phone);
            builder.address(extendedProfile.getAddress());
            builder.region(extendedProfile.getRegion());
            builder.companyId(extendedProfile.getCompanyId());
            builder.socialLinks(extendedProfile.getSocialLinks());
            builder.basicAvatarUrl(
                    user != null && user.getAvatarUrl() != null ? user.getAvatarUrl() : extendedProfile.getAvatarUrl());

            builder.professionalTitle(extendedProfile.getProfessionalTitle())
                    .careerGoals(extendedProfile.getCareerGoals())
                    .yearsOfExperience(extendedProfile.getYearsOfExperience())
                    .workExperiences(readJsonList(
                            extendedProfile.getWorkExperiences(),
                            PortfolioWorkExperienceDTO.class))
                    .educationHistory(readJsonList(
                            extendedProfile.getEducationHistory(),
                            PortfolioEducationDTO.class))
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
                    .roadmapMentoringPrice(extendedProfile.getRoadmapMentoringPrice())
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
                    .achievements(extendedProfile.getAchievements())
                    .createdAt(extendedProfile.getCreatedAt())
                    .updatedAt(extendedProfile.getUpdatedAt());
        }

        return builder.build();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize portfolio data", e);
        }
    }

    private <T> List<T> readJsonList(String json, Class<T> elementType) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("Failed to parse portfolio JSON for type {}: {}", elementType.getSimpleName(), e.getMessage());
            return new ArrayList<>();
        }
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
                .orElseThrow(() -> new NotFoundException("CV not found: " + cvId));
        if (!newActiveCv.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Unauthorized to set this CV as active");
        }
        newActiveCv.setIsActive(true);
        newActiveCv = cvRepository.save(newActiveCv);
        return mapToCVDTO(newActiveCv);
    }

    @Transactional
    public void deleteCV(Long cvId, Long userId) {
        GeneratedCV cv = cvRepository.findById(cvId)
                .orElseThrow(() -> new NotFoundException("CV not found: " + cvId));

        if (!cv.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Unauthorized to delete this CV");
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

    private String normalizeAndValidateCustomUrlSlug(String rawSlug, Long currentUserId) {
        String normalized = rawSlug == null ? null : rawSlug.trim().toLowerCase();
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        if (!CUSTOM_SLUG_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Custom URL slug may only contain lowercase letters, numbers, and hyphens");
        }
        if (RESERVED_CUSTOM_SLUGS.contains(normalized)) {
            throw new IllegalArgumentException("This slug is reserved by system");
        }

        extendedProfileRepository.findByCustomUrlSlug(normalized)
                .filter(profile -> !profile.getUserId().equals(currentUserId))
                .ifPresent(profile -> {
                    throw new ConflictException("Custom URL slug already exists");
                });
        return normalized;
    }

    private void validatePreferredCurrency(String preferredCurrency) {
        String normalized = preferredCurrency == null ? null : preferredCurrency.trim().toUpperCase();
        if (normalized == null || normalized.isEmpty()) {
            return;
        }
        if (!SUPPORTED_PREFERRED_CURRENCY.equals(normalized)) {
            throw new IllegalArgumentException("Preferred currency must be VND");
        }
    }

    // ==================== V3 PHASE 2: VERIFIED SKILLS ====================

    @Override
    @Transactional(readOnly = true)
    public List<com.exe.skillverse_backend.portfolio_service.dto.UserVerifiedSkillDTO> getVerifiedSkills(Long userId) {
        getUserOrThrow(userId);
        return enrichVerifiedSkills(
                verifiedSkillRepository.findByUserIdOrderByVerifiedAtDesc(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.exe.skillverse_backend.portfolio_service.dto.UserVerifiedSkillDTO> getPublicVerifiedSkills(Long userId) {
        getPublicExtendedProfileOrThrow(userId);
        return enrichVerifiedSkills(
                verifiedSkillRepository.findByUserIdOrderByVerifiedAtDesc(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortfolioVerifiedSkillDetailDTO> getVerifiedSkillDetails(Long userId) {
        User user = getUserOrThrow(userId);
        return resolveVerifiedSkillDetails(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortfolioVerifiedSkillDetailDTO> getPublicVerifiedSkillDetails(Long userId) {
        getPublicExtendedProfileOrThrow(userId);
        User user = getUserOrThrow(userId);
        return resolveVerifiedSkillDetails(user);
    }

    private List<com.exe.skillverse_backend.portfolio_service.dto.UserVerifiedSkillDTO> enrichVerifiedSkills(
            List<com.exe.skillverse_backend.portfolio_service.entity.UserVerifiedSkill> skills) {
        return skills.stream().map(s -> {
            var dto = com.exe.skillverse_backend.portfolio_service.dto.UserVerifiedSkillDTO.from(s);
            userRepository.findById(s.getVerifiedByMentorId()).ifPresent(mentor ->
                    dto.setVerifiedByMentorName(mentor.getFullName()));
            return dto;
        }).toList();
    }

    private List<PortfolioVerifiedSkillDetailDTO> resolveVerifiedSkillDetails(User user) {
        if (user.getPrimaryRole() == PrimaryRole.MENTOR) {
            return mentorVerificationRequestRepository.findApprovedByMentorId(user.getId())
                    .stream()
                    .map(this::mapMentorVerifiedSkillDetail)
                    .toList();
        }

        return verifiedSkillRepository.findByUserIdOrderByVerifiedAtDesc(user.getId())
                .stream()
                .map(this::mapRoadmapVerifiedSkillDetail)
                .toList();
    }

    private PortfolioVerifiedSkillDetailDTO mapMentorVerifiedSkillDetail(MentorSkillVerificationRequest request) {
        User reviewer = request.getReviewedBy();

        return PortfolioVerifiedSkillDetailDTO.builder()
                .id(request.getId())
                .skillName(request.getSkillName())
                .displaySkillName(formatDisplaySkillName(request.getSkillName()))
                .verificationSource("ADMIN_MENTOR")
                .verifiedAt((request.getReviewedAt() != null ? request.getReviewedAt() : request.getRequestedAt())
                        .toInstant(ZoneOffset.ofHours(7)))
                .reviewerId(reviewer != null ? reviewer.getId() : null)
                .reviewerName(reviewer != null ? reviewer.getFullName() : "Hệ thống Admin")
                .reviewerRole(reviewer != null && reviewer.getPrimaryRole() != null
                        ? reviewer.getPrimaryRole().name()
                        : "ADMIN")
                .reviewNote(request.getReviewNote())
                .verificationRequestId(request.getId())
                .evidences(request.getEvidences() == null
                        ? List.of()
                        : request.getEvidences().stream()
                                .map(this::mapMentorEvidence)
                                .toList())
                .build();
    }

    private PortfolioVerifiedSkillDetailDTO mapRoadmapVerifiedSkillDetail(
            com.exe.skillverse_backend.portfolio_service.entity.UserVerifiedSkill skill) {
        User reviewer = userRepository.findById(skill.getVerifiedByMentorId()).orElse(null);
        VerificationEvidenceReport report = skill.getJourneyId() == null
                ? null
                : verificationEvidenceReportRepository.findFirstByJourneyIdOrderByAttemptNumberDesc(skill.getJourneyId())
                        .orElse(null);
        JourneyOutputAssessment outputAssessment = skill.getJourneyId() == null
                ? null
                : journeyOutputAssessmentRepository.findFirstByJourneyIdOrderBySubmittedAtDesc(skill.getJourneyId())
                        .orElse(null);
        List<RoadmapNodeSubmission> submissions = skill.getJourneyId() == null
                ? List.of()
                : roadmapNodeSubmissionRepository.findByJourneyId(skill.getJourneyId());

        List<PortfolioVerifiedSkillEvidenceDTO> evidences = new ArrayList<>();

        if (report != null) {
            evidences.add(PortfolioVerifiedSkillEvidenceDTO.builder()
                    .id(report.getId())
                    .type("VERIFICATION_REPORT")
                    .title("Báo cáo xác thực cuối kỳ")
                    .description(report.getSummaryReport())
                    .build());

            if (report.getMeetingJitsiLink() != null && !report.getMeetingJitsiLink().isBlank()) {
                evidences.add(PortfolioVerifiedSkillEvidenceDTO.builder()
                        .id(report.getId())
                        .type("MEETING_LINK")
                        .title("Buổi xác thực với mentor")
                        .url(report.getMeetingJitsiLink())
                        .description("Phiên xác thực cuối kỳ qua roadmap mentoring")
                        .build());
            }
        }

        if (outputAssessment != null) {
            appendOutputAssessmentEvidence(evidences, outputAssessment);
        }

        submissions.forEach(submission -> appendNodeSubmissionEvidence(evidences, submission));

        return PortfolioVerifiedSkillDetailDTO.builder()
                .id(skill.getId())
                .skillName(skill.getSkillName())
                .displaySkillName(formatDisplaySkillName(skill.getSkillName()))
                .verificationSource("ROADMAP_MENTOR")
                .verifiedAt(skill.getVerifiedAt())
                .reviewerId(skill.getVerifiedByMentorId())
                .reviewerName(reviewer != null ? reviewer.getFullName() : "Mentor")
                .reviewerRole("MENTOR")
                .reviewNote(report != null && report.getSummaryReport() != null && !report.getSummaryReport().isBlank()
                        ? report.getSummaryReport()
                        : skill.getVerificationNote())
                .journeyId(skill.getJourneyId())
                .bookingId(skill.getBookingId())
                .evidences(evidences)
                .build();
    }

    private PortfolioVerifiedSkillEvidenceDTO mapMentorEvidence(MentorVerificationEvidence evidence) {
        ExternalCertificate certificate = evidence.getCertificate();
        String evidenceUrl = evidence.getEvidenceUrl();
        if ((evidenceUrl == null || evidenceUrl.isBlank()) && certificate != null) {
            evidenceUrl = certificate.getCredentialUrl();
        }

        return PortfolioVerifiedSkillEvidenceDTO.builder()
                .id(evidence.getId())
                .type(evidence.getEvidenceType() != null ? evidence.getEvidenceType().name() : "EVIDENCE")
                .title(certificate != null ? certificate.getTitle() : evidence.getEvidenceType().name())
                .url(evidenceUrl)
                .imageUrl(certificate != null ? certificate.getCertificateImageUrl() : null)
                .issuer(certificate != null ? certificate.getIssuingOrganization() : null)
                .description(evidence.getDescription())
                .build();
    }

    private void appendOutputAssessmentEvidence(
            List<PortfolioVerifiedSkillEvidenceDTO> evidences,
            JourneyOutputAssessment outputAssessment) {
        if (outputAssessment.getEvidenceUrl() != null && !outputAssessment.getEvidenceUrl().isBlank()) {
            evidences.add(PortfolioVerifiedSkillEvidenceDTO.builder()
                    .id(outputAssessment.getId())
                    .type("OUTPUT_EVIDENCE")
                    .title("Minh chứng đầu ra của roadmap")
                    .url(outputAssessment.getEvidenceUrl())
                    .description(outputAssessment.getSubmissionText())
                    .build());
        }

        if (outputAssessment.getAttachmentUrl() != null && !outputAssessment.getAttachmentUrl().isBlank()) {
            evidences.add(PortfolioVerifiedSkillEvidenceDTO.builder()
                    .id(outputAssessment.getId())
                    .type("OUTPUT_ATTACHMENT")
                    .title("Tệp đính kèm đầu ra")
                    .url(outputAssessment.getAttachmentUrl())
                    .description(outputAssessment.getFeedback())
                    .build());
        }
    }

    private void appendNodeSubmissionEvidence(
            List<PortfolioVerifiedSkillEvidenceDTO> evidences,
            RoadmapNodeSubmission submission) {
        if (submission.getEvidenceUrl() != null && !submission.getEvidenceUrl().isBlank()) {
            evidences.add(PortfolioVerifiedSkillEvidenceDTO.builder()
                    .id(submission.getId())
                    .type("NODE_EVIDENCE")
                    .title("Minh chứng node " + submission.getNodeId())
                    .url(submission.getEvidenceUrl())
                    .description(submission.getSubmissionText())
                    .build());
        }

        if (submission.getAttachmentUrl() != null && !submission.getAttachmentUrl().isBlank()) {
            evidences.add(PortfolioVerifiedSkillEvidenceDTO.builder()
                    .id(submission.getId())
                    .type("NODE_ATTACHMENT")
                    .title("Tệp đính kèm node " + submission.getNodeId())
                    .url(submission.getAttachmentUrl())
                    .description(submission.getMentorFeedback())
                    .build());
        }
    }

    private String formatDisplaySkillName(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return "";
        }

        String[] parts = skillName.trim().toLowerCase().split("_+");
        List<String> prettyParts = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            prettyParts.add(part.substring(0, 1).toUpperCase() + part.substring(1));
        }
        return String.join(" ", prettyParts);
    }
}
