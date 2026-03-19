package com.exe.skillverse_backend.portfolio_service.service;

import com.exe.skillverse_backend.portfolio_service.dto.*;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

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

        // Mentor Reviews
        List<MentorReviewDTO> getUserReviews(Long userId);

        List<MentorReviewDTO> getPublicUserReviews(Long userId);

        // CV Generation
        GeneratedCVDTO generateCV(Long userId, CVGenerationRequest request);

        GeneratedCVDTO updateCV(Long cvId, Long userId, String cvContent, String cvJson);

        GeneratedCVDTO getActiveCV(Long userId);

        List<GeneratedCVDTO> getAllUserCVs(Long userId);

        GeneratedCVDTO setActiveCV(Long userId, Long cvId);

        void deleteCV(Long cvId, Long userId);
}
