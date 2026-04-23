package com.exe.skillverse_backend.portfolio_service.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import com.exe.skillverse_backend.portfolio_service.dto.AIEnhanceRequest;
import com.exe.skillverse_backend.portfolio_service.dto.AIEnhanceResponse;
import com.exe.skillverse_backend.portfolio_service.dto.CVGenerationRequest;
import com.exe.skillverse_backend.portfolio_service.dto.CompletedMissionDTO;
import com.exe.skillverse_backend.portfolio_service.dto.ExternalCertificateDTO;
import com.exe.skillverse_backend.portfolio_service.dto.GeneratedCVDTO;
import com.exe.skillverse_backend.portfolio_service.dto.MentorReviewDTO;
import com.exe.skillverse_backend.portfolio_service.dto.PortfolioProjectDTO;
import com.exe.skillverse_backend.portfolio_service.dto.SystemCertificateDTO;
import com.exe.skillverse_backend.portfolio_service.dto.UserProfileDTO;

public interface PortfolioService {
        // User Profile (Extended)
        UserProfileDTO createExtendedProfile(Long userId, UserProfileDTO dto, MultipartFile avatarFile,
                        MultipartFile videoFile, MultipartFile coverImageFile);

        UserProfileDTO updateExtendedProfile(Long userId, UserProfileDTO dto, MultipartFile avatarFile,
                        MultipartFile videoFile, MultipartFile coverImageFile);

        void deleteExtendedProfile(Long userId);

        boolean hasExtendedProfile(Long userId);

        UserProfileDTO getProfile(Long userId);

        UserProfileDTO getPublicProfile(Long userId);

        UserProfileDTO getProfileBySlug(String customUrlSlug);

        List<UserProfileDTO> getAllPublicPortfolios();

        // Projects
        PortfolioProjectDTO createProject(Long userId, PortfolioProjectDTO dto, MultipartFile thumbnailFile);

        PortfolioProjectDTO updateProject(Long projectId, Long userId, PortfolioProjectDTO dto,
                        MultipartFile thumbnailFile);

        List<PortfolioProjectDTO> getUserProjects(Long userId);

        List<PortfolioProjectDTO> getPublicUserProjects(Long userId);

        void deleteProject(Long projectId, Long userId);

        // External Certificates
        ExternalCertificateDTO createCertificate(Long userId, ExternalCertificateDTO dto,
                        MultipartFile certificateImage);

        List<ExternalCertificateDTO> getUserCertificates(Long userId);

        List<ExternalCertificateDTO> getPublicUserCertificates(Long userId);

        void deleteCertificate(Long certificateId, Long userId);

        // System Certificates (course completion certs + gamification badges)
        List<SystemCertificateDTO> getSystemCertificates(Long userId);

        List<SystemCertificateDTO> importSystemCertificates(Long userId, String source);

        // Mentor Reviews
        List<MentorReviewDTO> getUserReviews(Long userId);

        List<MentorReviewDTO> getPublicUserReviews(Long userId);

        // Completed Missions (short-term jobs)
        List<CompletedMissionDTO> getCompletedMissions(Long userId);

        List<CompletedMissionDTO> getPublicCompletedMissions(Long userId);

        // CV Generation (AI)
        GeneratedCVDTO generateCV(Long userId, CVGenerationRequest request);

        // CV Export (No AI - direct portfolio to CV mapping)
        GeneratedCVDTO exportCV(Long userId, CVGenerationRequest request);

        // CV Section Enhancement (AI for specific sections)
        AIEnhanceResponse enhanceCVSection(Long userId, AIEnhanceRequest request);

        GeneratedCVDTO updateCV(Long cvId, Long userId, String cvContent, String cvJson);

        GeneratedCVDTO getActiveCV(Long userId);

        List<GeneratedCVDTO> getAllUserCVs(Long userId);

        GeneratedCVDTO setActiveCV(Long userId, Long cvId);

        void deleteCV(Long cvId, Long userId);

    // V3 Phase 2: Verified skills (from ROADMAP_MENTORING verification)
    List<com.exe.skillverse_backend.portfolio_service.dto.UserVerifiedSkillDTO> getVerifiedSkills(Long userId);

    List<com.exe.skillverse_backend.portfolio_service.dto.UserVerifiedSkillDTO> getPublicVerifiedSkills(Long userId);

    List<com.exe.skillverse_backend.portfolio_service.dto.PortfolioVerifiedSkillDetailDTO> getVerifiedSkillDetails(Long userId);

    List<com.exe.skillverse_backend.portfolio_service.dto.PortfolioVerifiedSkillDetailDTO> getPublicVerifiedSkillDetails(Long userId);
}
