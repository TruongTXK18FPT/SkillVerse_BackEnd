package com.exe.skillverse_backend.journey_service.service;

import com.exe.skillverse_backend.journey_service.dto.response.JourneyVerificationDetailResponse;

public interface PublicJourneyVerificationService {
    JourneyVerificationDetailResponse getVerificationDetails(Long journeyId);
}
