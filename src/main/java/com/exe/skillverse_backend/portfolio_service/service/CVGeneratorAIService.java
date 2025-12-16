package com.exe.skillverse_backend.portfolio_service.service;

import com.exe.skillverse_backend.portfolio_service.dto.*;
import java.util.List;

public interface CVGeneratorAIService {
    String generateCV(
            UserProfileDTO profile,
            List<PortfolioProjectDTO> projects,
            List<ExternalCertificateDTO> certificates,
            List<MentorReviewDTO> reviews,
            CVGenerationRequest request);

    String generateCVJson(
            UserProfileDTO profile,
            List<PortfolioProjectDTO> projects,
            List<ExternalCertificateDTO> certificates,
            List<MentorReviewDTO> reviews);
}
