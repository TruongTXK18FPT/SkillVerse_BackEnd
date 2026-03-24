package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.business_service.entity.TrustScore;

public interface TrustScoreService {
    TrustScore calculateScore(Long userId);
    TrustScore getScore(Long userId);
    TrustScore recalculateScore(Long userId);
    void triggerRecalculation(Long userId);
    void triggerRecalculationOnJobComplete(Long recruiterId, Long workerId);
    void triggerRecalculationOnDispute(Long userId);
}
