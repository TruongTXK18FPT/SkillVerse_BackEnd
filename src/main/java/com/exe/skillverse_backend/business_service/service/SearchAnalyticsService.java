package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.business_service.entity.CandidateSearchSession;
import com.exe.skillverse_backend.business_service.repository.CandidateSearchSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Thin service for recording search analytics.
 * Uses its own read-write transaction so it doesn't inherit read-only from callers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchAnalyticsService {

    private final CandidateSearchSessionRepository searchSessionRepository;

    @Transactional
    public void recordSearchSession(Long recruiterId, String searchQuery, String filters,
                                    int totalResults, Integer pageSize) {
        try {
            CandidateSearchSession session = CandidateSearchSession.builder()
                    .recruiterId(recruiterId)
                    .searchQuery(searchQuery)
                    .filters(filters)
                    .totalResults(totalResults)
                    .pageSize(pageSize != null ? pageSize : 20)
                    .searchedAt(LocalDateTime.now())
                    .build();

            searchSessionRepository.save(session);
        } catch (Exception e) {
            log.warn("Failed to record search session: {}", e.getMessage());
        }
    }
}
