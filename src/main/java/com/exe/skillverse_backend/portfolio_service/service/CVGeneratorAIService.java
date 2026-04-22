package com.exe.skillverse_backend.portfolio_service.service;

import java.util.List;
import com.exe.skillverse_backend.portfolio_service.dto.AIEnhanceRequest;
import com.exe.skillverse_backend.portfolio_service.dto.AIEnhanceResponse;
import com.exe.skillverse_backend.portfolio_service.dto.CVGenerationRequest;
import com.exe.skillverse_backend.portfolio_service.dto.CompletedMissionDTO;
import com.exe.skillverse_backend.portfolio_service.dto.ExternalCertificateDTO;
import com.exe.skillverse_backend.portfolio_service.dto.MentorReviewDTO;
import com.exe.skillverse_backend.portfolio_service.dto.PortfolioProjectDTO;
import com.exe.skillverse_backend.portfolio_service.dto.UserProfileDTO;

public interface CVGeneratorAIService {
    String generateCV(
            UserProfileDTO profile,
            List<PortfolioProjectDTO> projects,
            List<ExternalCertificateDTO> certificates,
            List<MentorReviewDTO> reviews,
            List<CompletedMissionDTO> completedMissions,
            CVGenerationRequest request);

    String generateCVJson(
            UserProfileDTO profile,
            List<PortfolioProjectDTO> projects,
            List<ExternalCertificateDTO> certificates,
            List<MentorReviewDTO> reviews);

    /**
     * Enhance a specific CV section using AI.
     * This is used when user wants to refine a particular section
     * after manually creating/exporting their CV.
     *
     * @param request the enhancement request containing section, content, and instruction
     * @return AI-enhanced content with alternatives
     */
    AIEnhanceResponse enhanceSection(AIEnhanceRequest request);
}
