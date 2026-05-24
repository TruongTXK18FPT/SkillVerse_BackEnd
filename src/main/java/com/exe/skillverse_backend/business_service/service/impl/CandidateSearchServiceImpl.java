package com.exe.skillverse_backend.business_service.service.impl;

import com.exe.skillverse_backend.ai_search_service.AISearchService;
import com.exe.skillverse_backend.ai_search_service.dto.AICandidateMatchResponse;
import com.exe.skillverse_backend.business_service.dto.request.CandidateSearchRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateRecruitmentStatusRequest;
import com.exe.skillverse_backend.business_service.dto.response.RecruitmentSessionResponse;
import com.exe.skillverse_backend.business_service.entity.CandidateMatchScore;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.RecruiterShortlist;
import com.exe.skillverse_backend.business_service.entity.ShortTermJob;
import com.exe.skillverse_backend.business_service.entity.enums.RecruitmentJobContextType;
import com.exe.skillverse_backend.business_service.entity.enums.RecruitmentSessionStatus;
import com.exe.skillverse_backend.business_service.entity.enums.RecruitmentSessionSource;
import com.exe.skillverse_backend.business_service.repository.CandidateMatchScoreRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobRepository;
import com.exe.skillverse_backend.business_service.service.SearchAnalyticsService;
import com.exe.skillverse_backend.business_service.repository.CandidateSearchSessionRepository;
import com.exe.skillverse_backend.business_service.repository.JobPostingRepository;
import com.exe.skillverse_backend.business_service.repository.RecruiterShortlistRepository;
import com.exe.skillverse_backend.business_service.service.CandidateFitScoringService;
import com.exe.skillverse_backend.business_service.service.CandidateSearchService;
import com.exe.skillverse_backend.business_service.service.RecruitmentChatService;
import com.exe.skillverse_backend.portfolio_service.dto.CandidateSummaryDTO;
import com.exe.skillverse_backend.portfolio_service.entity.PortfolioExtendedProfile;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioExtendedProfileRepository;
import com.exe.skillverse_backend.premium_service.entity.FeatureType;
import com.exe.skillverse_backend.premium_service.service.RecruiterSubscriptionService;
import com.exe.skillverse_backend.premium_service.service.UsageLimitService;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ForbiddenException;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of CandidateSearchService with hybrid scoring.
 * Combines rule-based matching with optional AI enhancement.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CandidateSearchServiceImpl implements CandidateSearchService {

    private final PortfolioExtendedProfileRepository portfolioRepository;
    private final JobPostingRepository jobPostingRepository;
    private final ShortTermJobRepository shortTermJobRepository;
    private final CandidateMatchScoreRepository matchScoreRepository;
    private final RecruiterShortlistRepository shortlistRepository;
    private final RecruiterSubscriptionService recruiterSubscriptionService;
    private final UsageLimitService usageLimitService;
    private final AISearchService aiSearchService;
    private final RecruitmentChatService recruitmentChatService;
    private final SearchAnalyticsService searchAnalyticsService;
    private final CandidateFitScoringService candidateFitScoringService;
    private final ObjectMapper objectMapper;
    private final JourneyRepository journeyRepository;

    // Deterministic scoring weights (Standardized for filtering)
    private static final BigDecimal SKILL_WEIGHT = new BigDecimal("0.50"); // 50% - Core requirement
    private static final BigDecimal PROJECT_WEIGHT = new BigDecimal("0.25"); // 25% - Practical experience
    private static final BigDecimal MISSION_WEIGHT = new BigDecimal("0.15"); // 15% - System verified experience
    private static final BigDecimal CERT_WEIGHT = new BigDecimal("0.10"); // 10% - Theoretical knowledge

    // Score thresholds
    private static final double EXCELLENT_THRESHOLD = 0.8;
    private static final double GOOD_THRESHOLD = 0.6;
    private static final double FAIR_THRESHOLD = 0.4;

    @Override
    @Transactional(readOnly = true)
    public Page<CandidateSummaryDTO> searchCandidates(Long recruiterId, CandidateSearchRequest request) {
        log.info("Searching candidates for recruiter {} with query: {}", recruiterId, request.getQuery());

        // Check permission
        if (!hasCandidateAccess(recruiterId)) {
            throw new ForbiddenException("Bạn cần gói Premium Recruiter để truy cập cơ sở dữ liệu ứng viên.");
        }

        int page = request.getPage() != null ? Math.max(0, request.getPage()) : 0;
        int size = request.getSize() != null && request.getSize() > 0 ? Math.min(request.getSize(), 50) : 20;
        PageRequest pageRequest = PageRequest.of(page, size);
        int scoringWindow = Math.max(200, Math.min(500, (page + 1) * size * 5));
        Page<PortfolioExtendedProfile> profiles = portfolioRepository.findPortfoliosOpenToOffers(PageRequest.of(0, scoringWindow));

        JobPosting resolvedJob = null;
        if (request.getJobId() != null) {
            resolvedJob = jobPostingRepository.findById(request.getJobId()).orElse(null);
        }

        ShortTermJob resolvedShortTermJob = null;
        if (request.getShortTermJobId() != null) {
            resolvedShortTermJob = shortTermJobRepository.findById(request.getShortTermJobId()).orElse(null);
        }

        // Verify ownership for short-term job
        if (resolvedShortTermJob != null && !resolvedShortTermJob.getRecruiterProfile().getUser().getId().equals(recruiterId)) {
            throw new ForbiddenException("Bạn không có quyền truy cập tin ngắn hạn này.");
        }

        if (resolvedJob != null && !resolvedJob.getRecruiterProfile().getUser().getId().equals(recruiterId)) {
            throw new ForbiddenException("Bạn không có quyền truy cập tin tuyển dụng này.");
        }

        final JobPosting job = resolvedJob;
        final ShortTermJob shortTermJob = resolvedShortTermJob;

        Map<Long, PortfolioExtendedProfile> profileIndex = profiles.getContent().stream()
                .collect(Collectors.toMap(
                        PortfolioExtendedProfile::getUserId,
                        profile -> profile,
                        (left, right) -> left
                ));

        List<CandidateSummaryDTO> scoredCandidates = profiles.getContent().stream()
                .map(profile -> safelyCalculateHybridScore(profile, job, shortTermJob, request))
                .filter(Objects::nonNull)
                .filter(candidate -> matchesCandidateFilters(
                        profileIndex.get(candidate.getUserId()),
                        candidate,
                        request
                ))
                .collect(Collectors.toList());

        sortCandidates(scoredCandidates, profileIndex, request);
        searchAnalyticsService.recordSearchSession(recruiterId, request.getQuery(), request.getSkills(),
                scoredCandidates.size(), request.getSize());

        int fromIndex = Math.min(page * size, scoredCandidates.size());
        int toIndex = Math.min(fromIndex + size, scoredCandidates.size());

        return new PageImpl<>(
                scoredCandidates.subList(fromIndex, toIndex),
                pageRequest,
                scoredCandidates.size()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Object getCandidateMatchExplanation(Long recruiterId, Long jobId, Long candidateId) {
        // Check permission
        if (!hasCandidateAccess(recruiterId)) {
            throw new ForbiddenException("Bạn cần gói Premium Recruiter để sử dụng tính năng đánh giá ứng viên.");
        }

        // Get job
        JobPosting job = jobPostingRepository.findById(jobId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy tin tuyển dụng."));

        if (!job.getRecruiterProfile().getUser().getId().equals(recruiterId)) {
            throw new ForbiddenException("Bạn không có quyền truy cập công việc này.");
        }

        PortfolioExtendedProfile profile = portfolioRepository.findById(candidateId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy ứng viên."));

        CandidateSearchRequest request = CandidateSearchRequest.builder().jobId(jobId).build();
        return calculateHybridScore(profile, job, null, request);
    }

    @Override
    @Transactional(readOnly = true)
    public Object getShortTermJobMatchExplanation(Long recruiterId, Long shortTermJobId, Long candidateId) {
        // Check permission
        if (!hasCandidateAccess(recruiterId)) {
            throw new ForbiddenException("Bạn cần gói Premium Recruiter để sử dụng tính năng đánh giá ứng viên.");
        }

        // Verify short-term job ownership
        var shortTermJob = shortTermJobRepository.findById(shortTermJobId).orElse(null);
        if (shortTermJob == null) {
            throw new BadRequestException("Không tìm thấy công việc ngắn hạn.");
        }
        if (!shortTermJob.getRecruiterProfile().getUser().getId().equals(recruiterId)) {
            throw new ForbiddenException("Bạn không có quyền truy cập công việc này.");
        }

        PortfolioExtendedProfile profile = portfolioRepository.findById(candidateId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy ứng viên."));

        CandidateSearchRequest request = CandidateSearchRequest.builder().shortTermJobId(shortTermJobId).build();
        return calculateHybridScore(profile, null, shortTermJob, request);
    }

    @Override
    @Transactional(readOnly = true)
    public Object getAiEnhancedAnalysis(Long recruiterId, Long jobId, Long shortTermJobId, Long candidateId) {
        // Check permission
        if (!hasCandidateAccess(recruiterId)) {
            throw new ForbiddenException("Bạn cần gói Premium Recruiter để sử dụng AI phân tích.");
        }

        // Resolve job
        JobPosting job = null;
        ShortTermJob shortTermJob = null;
        if (jobId != null) {
            job = jobPostingRepository.findById(jobId)
                    .orElseThrow(() -> new BadRequestException("Không tìm thấy tin tuyển dụng."));
            if (!job.getRecruiterProfile().getUser().getId().equals(recruiterId)) {
                throw new ForbiddenException("Bạn không có quyền truy cập công việc này.");
            }
        } else if (shortTermJobId != null) {
            shortTermJob = shortTermJobRepository.findById(shortTermJobId)
                    .orElseThrow(() -> new BadRequestException("Không tìm thấy công việc ngắn hạn."));
            if (!shortTermJob.getRecruiterProfile().getUser().getId().equals(recruiterId)) {
                throw new ForbiddenException("Bạn không có quyền truy cập công việc này.");
            }
        } else {
            throw new BadRequestException("Cần truyền jobId hoặc shortTermJobId.");
        }

        PortfolioExtendedProfile profile = portfolioRepository.findById(candidateId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy ứng viên."));

        // 1. Calculate deterministic scores
        CandidateSearchRequest searchRequest = CandidateSearchRequest.builder()
                .jobId(jobId).shortTermJobId(shortTermJobId).build();
        CandidateSummaryDTO deterministicResult = calculateHybridScore(profile, job, shortTermJob, searchRequest);

        // 2. Run AI analysis (optional, may fail gracefully)
        AICandidateMatchResponse aiResult = null;
        String aiError = null;
        try {
            if (aiSearchService.isEnabled() && aiSearchService.canMakeRequest()) {
                if (shortTermJobId != null) {
                    aiResult = aiSearchService.generateShortTermJobMatchExplanation(shortTermJobId, candidateId);
                } else {
                    aiResult = aiSearchService.generateMatchExplanation(jobId, candidateId);
                }
            } else {
                aiError = "AI service hiện không khả dụng hoặc đã hết quota.";
            }
        } catch (Exception e) {
            log.warn("AI analysis failed for candidate {} job {}: {}", candidateId, jobId != null ? jobId : shortTermJobId, e.getMessage());
            aiError = "Không thể hoàn tất AI phân tích: " + e.getMessage();
        }

        // 3. Build recommendation
        String recommendation;
        String verdict;
        double matchScore = deterministicResult.getMatchScore() != null ? deterministicResult.getMatchScore() : 0;
        boolean primaryMatch = Boolean.TRUE.equals(deterministicResult.getPrimarySkillMatch());

        if (matchScore >= 0.8) {
            verdict = "STRONG_ACCEPT";
            recommendation = "✅ Đề xuất mạnh — Ứng viên đáp ứng xuất sắc yêu cầu. Nên ưu tiên liên hệ ngay.";
        } else if (matchScore >= 0.6) {
            verdict = "ACCEPT";
            recommendation = "✅ Đề xuất — Ứng viên phù hợp tốt, có tiềm năng. Nên tiến hành phỏng vấn.";
        } else if (matchScore >= 0.4) {
            if (primaryMatch) {
                verdict = "CONSIDER";
                recommendation = "⚠️ Cân nhắc — Điểm tổng trung bình nhưng có kỹ năng quan trọng nhất. Nên phỏng vấn để đánh giá thêm.";
            } else {
                verdict = "WEAK";
                recommendation = "⚠️ Phù hợp yếu — Thiếu nhiều yếu tố, cần cân nhắc kỹ trước khi liên hệ.";
            }
        } else {
            if (primaryMatch) {
                verdict = "RISKY";
                recommendation = "⚠️ Rủi ro — Điểm rất thấp nhưng có kỹ năng chính. Chỉ nên cân nhắc nếu không có ứng viên khác.";
            } else {
                verdict = "REJECT";
                recommendation = "❌ Không phù hợp — Thiếu kỹ năng quan trọng và kinh nghiệm. Không nên liên hệ.";
            }
        }

        // 4. Combine into response
        Map<String, Object> combined = new LinkedHashMap<>();
        combined.put("deterministicScores", deterministicResult);
        combined.put("verdict", verdict);
        combined.put("recommendation", recommendation);
        combined.put("matchScore", matchScore);
        combined.put("primarySkillMatch", primaryMatch);

        if (aiResult != null) {
            Map<String, Object> aiSection = new LinkedHashMap<>();
            aiSection.put("fitSummary", aiResult.getFitSummary());
            aiSection.put("reasoning", aiResult.getReasoning());
            aiSection.put("confidenceScore", aiResult.getConfidenceScore());
            aiSection.put("matchQuality", aiResult.getMatchQuality());
            aiSection.put("skillSignals", aiResult.getSkillSignals());
            aiSection.put("modelUsed", aiResult.getModelUsed());
            aiSection.put("processingTimeMs", aiResult.getProcessingTimeMs());
            aiSection.put("isFallback", aiResult.getIsFallback());
            combined.put("aiAnalysis", aiSection);
        }
        if (aiError != null) {
            combined.put("aiError", aiError);
        }

        return combined;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CandidateSummaryDTO> getMatchingCandidatesForJob(Long recruiterId, Long jobId, int page, int size) {
        log.info("Getting matching candidates for recruiter {} job {}", recruiterId, jobId);

        // Check permission
        if (!hasCandidateAccess(recruiterId)) {
            throw new ForbiddenException("Bạn cần gói Premium Recruiter để truy cập ứng viên phù hợp.");
        }

        // Get job
        JobPosting job = jobPostingRepository.findById(jobId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy tin tuyển dụng."));

        // Check ownership
        if (!job.getRecruiterProfile().getUser().getId().equals(recruiterId)) {
            throw new ForbiddenException("Bạn không có quyền xem ứng viên cho tin này.");
        }

        CandidateSearchRequest request = CandidateSearchRequest.builder()
                .jobId(jobId)
                .page(page)
                .size(size)
                .sortBy("matchScore")
                .sortOrder("DESC")
                .build();

        return searchCandidates(recruiterId, request);
    }

    @Override
    @Transactional
    public void shortlistCandidate(Long recruiterId, Long candidateId, Long jobId, String notes) {
        log.info("Shortlisting candidate {} for recruiter {}", candidateId, recruiterId);

        // Check if already shortlisted
        if (shortlistRepository.existsByRecruiterIdAndCandidateIdAndJobPostingId(recruiterId, candidateId, jobId)) {
            throw new BadRequestException("Ứng viên này đã trong danh sách shortlist.");
        }

        // Get candidate
        PortfolioExtendedProfile candidate = portfolioRepository.findById(candidateId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy ứng viên."));

        // Get job if provided
        JobPosting job = null;
        if (jobId != null) {
            job = jobPostingRepository.findById(jobId).orElse(null);
        }

        // Create shortlist
        RecruiterShortlist shortlist = RecruiterShortlist.builder()
                .recruiterId(recruiterId)
                .candidate(candidate.getUser())
                .jobPosting(job)
                .notes(notes)
                .shortlistStatus(RecruiterShortlist.ShortlistStatus.ACTIVE)
                .build();

        shortlistRepository.save(shortlist);
        log.info("Candidate {} shortlisted by recruiter {}", candidateId, recruiterId);
    }

    @Override
    @Transactional
    public void removeFromShortlist(Long recruiterId, Long candidateId, Long jobId) {
        log.info("Removing candidate {} from shortlist for recruiter {}", candidateId, recruiterId);

        RecruiterShortlist shortlist = shortlistRepository
                .findByRecruiterIdAndCandidateIdAndJobPostingId(recruiterId, candidateId, jobId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy trong danh sách shortlist."));

        shortlistRepository.delete(shortlist);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CandidateSummaryDTO> getShortlistedCandidates(Long recruiterId, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<RecruiterShortlist> shortlists;
        if (status != null && !status.isEmpty()) {
            RecruiterShortlist.ShortlistStatus shortlistStatus = RecruiterShortlist.ShortlistStatus.valueOf(status.toUpperCase());
            shortlists = shortlistRepository.findByRecruiterIdAndShortlistStatus(recruiterId, shortlistStatus, pageable);
        } else {
            shortlists = shortlistRepository.findByRecruiterId(recruiterId, pageable);
        }

        List<CandidateSummaryDTO> candidates = shortlists.getContent().stream()
                .map(this::mapShortlistToDTO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new PageImpl<>(candidates, pageable, shortlists.getTotalElements());
    }

    @Override
    @Transactional
    public RecruitmentSessionResponse connectCandidateToJob(Long recruiterId, Long candidateId, Long jobId) {
        log.info("Connecting candidate {} to job {} by recruiter {}", candidateId, jobId, recruiterId);

        // Create or get existing recruitment session
        RecruitmentSessionResponse session = recruitmentChatService.getOrCreateSession(
                recruiterId, candidateId, jobId, RecruitmentSessionSource.AI_SEARCH, RecruitmentJobContextType.JOB_POSTING);

        // Update session status to INVITED if job is provided
        if (jobId != null) {
            UpdateRecruitmentStatusRequest statusRequest =
                    UpdateRecruitmentStatusRequest.builder()
                            .status(RecruitmentSessionStatus.INVITED)
                            .build();
            session = recruitmentChatService.updateSessionStatus(recruiterId, session.getId(), statusRequest);
        }

        return session;
    }

    @Override
    @Transactional
    public RecruitmentSessionResponse startChatWithCandidate(Long recruiterId, Long candidateId, Long jobId) {
        log.info("Starting chat with candidate {} by recruiter {} for job {}", candidateId, recruiterId, jobId);

        // Create or get existing recruitment session
        return recruitmentChatService.getOrCreateSession(
                recruiterId, candidateId, jobId, RecruitmentSessionSource.MANUAL, RecruitmentJobContextType.JOB_POSTING);
    }

    // ==================== Private Helper Methods ====================

    private boolean hasCandidateAccess(Long recruiterId) {
        return recruiterSubscriptionService.hasActiveRecruiterSubscription(recruiterId);
    }

    /**
     * Calculate hybrid match score combining multiple factors.
     */
    private CandidateSummaryDTO calculateHybridScore(
            PortfolioExtendedProfile profile,
            JobPosting job,
            ShortTermJob shortTermJob,
            CandidateSearchRequest request
    ) {
        // Start with base candidate info
        CandidateSummaryDTO dto = mapProfileToDTO(profile);

        CandidateFitScoringService.ScoreResult score =
                candidateFitScoringService.score(profile, job, shortTermJob, request);

        dto.setMatchScore(Math.round(score.getOverallScore() * 100.0) / 100.0);
        dto.setMatchQuality(determineMatchQuality(score.getOverallScore()));
        dto.setSkillMatchPercent((int) Math.round(score.getSkillFit() * 100));

        // Backward-compatible weighted component fields for existing UI.
        dto.setSkillMatchScore(Math.round(score.getSkillFit() * 0.35 * 100.0) / 100.0);
        dto.setProjectMatchScore(Math.round(score.getEvidenceFit() * 0.20 * 100.0) / 100.0);
        dto.setCertMatchScore(Math.round(score.getConfidenceFit() * 0.10 * 100.0) / 100.0);
        dto.setMissionMatchScore(Math.round(score.getDeliveryFit() * 0.10 * 100.0) / 100.0);
        dto.setExperienceMatchScore(Math.round(score.getExperienceFit() * 0.15 * 100.0) / 100.0);
        dto.setEvidenceMatchScore(Math.round(score.getEvidenceFit() * 0.20 * 100.0) / 100.0);
        dto.setDeliveryMatchScore(Math.round(score.getDeliveryFit() * 0.10 * 100.0) / 100.0);
        dto.setLogisticsMatchScore(Math.round(score.getLogisticsFit() * 0.10 * 100.0) / 100.0);
        dto.setConfidenceMatchScore(Math.round(score.getConfidenceFit() * 0.10 * 100.0) / 100.0);
        dto.setRiskPenaltyScore(score.getRiskPenalty());

        dto.setPrimarySkillMatch(score.getPrimarySkillMatch());
        dto.setMatchedSkills(score.getMatchedSkills());
        dto.setUnmatchedSkills(score.getUnmatchedSkills());
        dto.setTotalRequiredSkills(score.getTotalRequiredSkills());
        dto.setTotalCandidateSkills(score.getTotalCandidateSkills());
        dto.setCompletedMissionsCount(score.getCompletedMissionsCount());
        dto.setTotalCertificatesCount(score.getTotalCertificatesCount());
        dto.setTotalVerifiedSkillsCount(score.getTotalVerifiedSkillsCount());
        dto.setRelevantProjectsCount(score.getRelevantProjectsCount());
        dto.setRelevantCertificatesCount(score.getRelevantCertificatesCount());
        dto.setRelevantMissionsCount(score.getRelevantMissionsCount());
        dto.setAverageMissionRating(score.getAverageMissionRating());
        dto.setIsVerified(score.isVerified());
        dto.setFitExplanation(score.getFitExplanation());
        dto.setFitAnalysis(score.getAnalysis());

        return dto;
    }

    private CandidateSummaryDTO safelyCalculateHybridScore(
            PortfolioExtendedProfile profile,
            JobPosting job,
            ShortTermJob shortTermJob,
            CandidateSearchRequest request
    ) {
        try {
            return calculateHybridScore(profile, job, shortTermJob, request);
        } catch (Exception exception) {
            Long candidateId = profile != null ? profile.getUserId() : null;
            log.warn("Skipping candidate {} because hybrid scoring failed: {}", candidateId, exception.getMessage());
            return null;
        }
    }

    private void sortCandidates(
            List<CandidateSummaryDTO> candidates,
            Map<Long, PortfolioExtendedProfile> profileIndex,
            CandidateSearchRequest request
    ) {
        String sortBy = request.getSortBy() != null ? request.getSortBy().trim().toLowerCase() : "totalscore";
        boolean ascending = "ASC".equalsIgnoreCase(request.getSortOrder());

        Comparator<CandidateSummaryDTO> comparator;
        switch (sortBy) {
            case "skillmatchpercent":
            case "skillfit":
                comparator = Comparator.comparingInt(
                        candidate -> candidate.getSkillMatchPercent() != null ? candidate.getSkillMatchPercent() : 0
                );
                break;
            case "experiencefit":
                comparator = Comparator.comparingDouble(
                        candidate -> getFitComponentScore(candidate, "experienceFit")
                );
                break;
            case "evidencefit":
                comparator = Comparator.comparingDouble(
                        candidate -> getFitComponentScore(candidate, "evidenceFit")
                );
                break;
            case "deliveryfit":
                comparator = Comparator.comparingDouble(
                        candidate -> getFitComponentScore(candidate, "deliveryFit")
                );
                break;
            case "logisticsfit":
                comparator = Comparator.comparingDouble(
                        candidate -> getFitComponentScore(candidate, "logisticsFit")
                );
                break;
            case "confidencefit":
                comparator = Comparator.comparingDouble(
                        candidate -> getFitComponentScore(candidate, "confidenceFit")
                );
                break;
            case "risk":
            case "riskpenalty":
                comparator = Comparator.comparingDouble(
                        candidate -> candidate.getRiskPenaltyScore() != null ? candidate.getRiskPenaltyScore() : 0.0
                );
                break;
            case "totalprojects":
                comparator = Comparator.comparingInt(
                        candidate -> candidate.getTotalProjects() != null ? candidate.getTotalProjects() : 0
                );
                break;
            case "hourlyrate":
                comparator = Comparator.comparingDouble(
                        candidate -> candidate.getHourlyRate() != null ? candidate.getHourlyRate() : 0.0
                );
                break;
            case "fullname":
                comparator = Comparator.comparing(
                        candidate -> candidate.getFullName() != null ? candidate.getFullName().toLowerCase() : ""
                );
                break;
            case "matchquality":
                comparator = Comparator.comparingInt(
                        candidate -> getMatchQualityRank(candidate.getMatchQuality())
                );
                break;
            case "lastactive":
            case "updatedat":
                comparator = Comparator.comparing(
                        candidate -> {
                            PortfolioExtendedProfile profile = profileIndex.get(candidate.getUserId());
                            return profile != null && profile.getUpdatedAt() != null
                                    ? profile.getUpdatedAt()
                                    : LocalDateTime.MIN;
                        }
                );
                break;
            case "matchscore":
            case "totalscore":
            case "score":
            default:
                comparator = Comparator.comparingDouble(
                        candidate -> candidate.getMatchScore() != null ? candidate.getMatchScore() : 0.0
                );
                break;
        }

        if (!ascending) {
            comparator = comparator.reversed();
        }

        Comparator<CandidateSummaryDTO> fallback = Comparator
                .comparingDouble((CandidateSummaryDTO candidate) ->
                        candidate.getMatchScore() != null ? candidate.getMatchScore() : 0.0
                )
                .reversed()
                .thenComparing(
                        Comparator.comparingInt((CandidateSummaryDTO candidate) ->
                                candidate.getSkillMatchPercent() != null ? candidate.getSkillMatchPercent() : 0
                        ).reversed()
                );

        candidates.sort(comparator.thenComparing(fallback));
    }

    private int getMatchQualityRank(String matchQuality) {
        if (matchQuality == null) {
            return 0;
        }

        switch (matchQuality.toUpperCase()) {
            case "EXCELLENT":
                return 4;
            case "GOOD":
                return 3;
            case "FAIR":
                return 2;
            case "POOR":
                return 1;
            default:
                return 0;
        }
    }

    private double getFitComponentScore(CandidateSummaryDTO candidate, String key) {
        if (candidate == null || candidate.getFitAnalysis() == null || candidate.getFitAnalysis().getComponents() == null) {
            return 0.0;
        }

        return candidate.getFitAnalysis().getComponents().stream()
                .filter(component -> key.equalsIgnoreCase(component.getKey()))
                .map(component -> component.getScore() != null ? component.getScore() : 0.0)
                .findFirst()
                .orElse(0.0);
    }

    private boolean matchesCandidateFilters(
            PortfolioExtendedProfile profile,
            CandidateSummaryDTO dto,
            CandidateSearchRequest request
    ) {
        if (profile == null || dto == null) {
            return false;
        }

        if (!matchesQuery(profile, dto, request.getQuery())) {
            return false;
        }

        if (!matchesRequestedSkills(profile, request.getSkills())) {
            return false;
        }

        Integer yearsOfExperience = profile.getYearsOfExperience();
        if (request.getMinExperience() != null && (yearsOfExperience == null || yearsOfExperience < request.getMinExperience())) {
            return false;
        }
        if (request.getMaxExperience() != null && yearsOfExperience != null && yearsOfExperience > request.getMaxExperience()) {
            return false;
        }
        if (!matchesExperienceLevel(yearsOfExperience, request.getExperienceLevel())) {
            return false;
        }

        if (request.getMinHourlyRate() != null &&
                (profile.getHourlyRate() == null || profile.getHourlyRate() < request.getMinHourlyRate())) {
            return false;
        }
        if (request.getMaxHourlyRate() != null &&
                profile.getHourlyRate() != null &&
                profile.getHourlyRate() > request.getMaxHourlyRate()) {
            return false;
        }

        if (Boolean.TRUE.equals(request.getHasCertificates()) &&
                (profile.getTotalCertificates() == null || profile.getTotalCertificates() <= 0)) {
            return false;
        }

        if (Boolean.TRUE.equals(request.getHasPortfolio()) &&
                (profile.getCustomUrlSlug() == null || profile.getCustomUrlSlug().isBlank())) {
            return false;
        }

        if (Boolean.TRUE.equals(request.getIsVerified()) && !Boolean.TRUE.equals(dto.getIsVerified())) {
            return false;
        }

        if (Boolean.TRUE.equals(request.getIsPremium()) && !dto.isHighlighted()) {
            return false;
        }

        if (Boolean.TRUE.equals(request.getHasRelevantProjects()) &&
                (dto.getRelevantProjectsCount() == null || dto.getRelevantProjectsCount() <= 0)) {
            return false;
        }

        if (Boolean.TRUE.equals(request.getHasCompletedMissions()) &&
                (dto.getCompletedMissionsCount() == null || dto.getCompletedMissionsCount() <= 0)) {
            return false;
        }

        if (Boolean.TRUE.equals(request.getMustMatchPrimarySkill()) &&
                !Boolean.TRUE.equals(dto.getPrimarySkillMatch())) {
            return false;
        }

        if (request.getMinOverallScore() != null) {
            double scorePercent = (dto.getMatchScore() != null ? dto.getMatchScore() : 0.0) * 100.0;
            if (scorePercent < request.getMinOverallScore()) {
                return false;
            }
        }

        if (request.getMinSkillFit() != null) {
            int skillFit = dto.getSkillMatchPercent() != null ? dto.getSkillMatchPercent() : 0;
            if (skillFit < request.getMinSkillFit()) {
                return false;
            }
        }

        if (request.getLocation() != null && !request.getLocation().isBlank()) {
            String locationQuery = request.getLocation().trim();
            if (!containsIgnoreCase(profile.getLocation(), locationQuery)
                    && !containsIgnoreCase(profile.getRegion(), locationQuery)
                    && !containsIgnoreCase(profile.getAddress(), locationQuery)) {
                return false;
            }
        }

        return !Boolean.FALSE.equals(request.getOpenToOffers());
    }

    private boolean matchesQuery(PortfolioExtendedProfile profile, CandidateSummaryDTO dto, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }

        String normalizedQuery = query.trim().toLowerCase();
        List<String> textFields = Arrays.asList(
                dto.getFullName(),
                dto.getProfessionalTitle(),
                profile.getBio(),
                profile.getTagline(),
                profile.getKeywords(),
                profile.getLocation(),
                profile.getRegion(),
                profile.getAddress()
        );

        boolean matchedText = textFields.stream()
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .anyMatch(value -> value.contains(normalizedQuery));

        if (matchedText) {
            return true;
        }

        return parseTextList(profile.getTopSkills()).stream()
                .map(String::toLowerCase)
                .anyMatch(skill -> skill.contains(normalizedQuery));
    }

    private boolean matchesRequestedSkills(PortfolioExtendedProfile profile, String requestedSkills) {
        if (requestedSkills == null || requestedSkills.isBlank()) {
            return true;
        }

        List<String> candidateSkills = parseTextList(profile.getTopSkills()).stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        if (candidateSkills.isEmpty()) {
            return false;
        }

        List<String> filters = Arrays.stream(requestedSkills.split(","))
                .map(String::trim)
                .filter(skill -> !skill.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        if (filters.isEmpty()) {
            return true;
        }

        return filters.stream().anyMatch(filter ->
                candidateSkills.stream().anyMatch(skill -> skill.contains(filter))
        );
    }

    private boolean matchesExperienceLevel(Integer yearsOfExperience, String experienceLevel) {
        if (experienceLevel == null || experienceLevel.isBlank()) {
            return true;
        }

        int years = yearsOfExperience != null ? yearsOfExperience : 0;
        String normalized = experienceLevel.trim().toUpperCase();

        switch (normalized) {
            case "ENTRY":
                return years <= 1;
            case "JUNIOR":
                return years >= 1 && years <= 2;
            case "MIDDLE":
            case "MID":
                return years >= 3 && years <= 5;
            case "SENIOR":
                return years >= 5;
            case "EXPERT":
                return years >= 8;
            default:
                return true;
        }
    }

    private List<String> parseTextList(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Collections.emptyList();
        }

        try {
            List<String> parsed = objectMapper.readValue(rawValue, List.class);
            return parsed.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.toList());
        } catch (JsonProcessingException ignored) {
            return Arrays.stream(rawValue.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .collect(Collectors.toList());
        }
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase().contains(query.toLowerCase());
    }

    private double calculateSkillScore(PortfolioExtendedProfile profile, JobPosting job, ShortTermJob shortTermJob, CandidateSearchRequest request, CandidateSummaryDTO dto) {
        String primarySkill = null;
        String requiredSkillsRaw = null;

        if (shortTermJob != null) {
            primarySkill = shortTermJob.getPrimarySkill();
            requiredSkillsRaw = shortTermJob.getRequiredSkills();
        } else if (job != null) {
            primarySkill = job.getPrimarySkill();
            requiredSkillsRaw = job.getRequiredSkills();
        }

        List<String> candidateSkills = Collections.emptyList();
        if (profile.getTopSkills() != null && !profile.getTopSkills().isBlank()) {
            try {
                candidateSkills = objectMapper.readValue(profile.getTopSkills(), List.class);
                candidateSkills = candidateSkills.stream()
                        .filter(Objects::nonNull)
                        .map(String::toLowerCase)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("Error parsing candidate skills", e);
            }
        }

        boolean primaryMatch = false;
        dto.setPrimarySkillMatch(false);
        if (primarySkill != null && !primarySkill.isBlank()) {
            String pSkill = primarySkill.toLowerCase().trim();
            primaryMatch = candidateSkills.stream().anyMatch(s -> s.contains(pSkill));
            dto.setPrimarySkillMatch(primaryMatch);
        }

        double score = 0.0;
        
        // Primary skill gives 40% of the total skill score
        if (primaryMatch) {
            score += 0.4;
        }

        // Other skills give up to 60% of the total skill score
        if (requiredSkillsRaw != null && !requiredSkillsRaw.isBlank()) {
            try {
                List<String> requiredSkills = objectMapper.readValue(requiredSkillsRaw, List.class);
                if (!requiredSkills.isEmpty() && !candidateSkills.isEmpty()) {
                    List<String> finalRequiredSkills = requiredSkills.stream()
                            .filter(Objects::nonNull)
                            .map(String::toLowerCase)
                            .collect(Collectors.toList());

                    long matchCount = candidateSkills.stream()
                            .filter(s -> finalRequiredSkills.stream().anyMatch(s::contains))
                            .count();

                    score += 0.6 * Math.min(1.0, (double) matchCount / finalRequiredSkills.size());
                } else if (requiredSkills.isEmpty()) {
                    score += 0.6; // No other skills required, free points
                }
            } catch (JsonProcessingException e) {
                log.warn("Error parsing skills for scoring: {}", e.getMessage());
            }
        } else {
            score += 0.6; // No other skills required, free points
        }

        // If no skills are specified at all for the job, default to 1.0 (free points)
        if ((primarySkill == null || primarySkill.isBlank()) && (requiredSkillsRaw == null || requiredSkillsRaw.isBlank())) {
            return 1.0;
        }

        return score;
    }

    private double calculateProjectScore(PortfolioExtendedProfile profile, CandidateSummaryDTO dto) {
        int projects = profile.getTotalProjects() != null ? profile.getTotalProjects() : 0;
        // Cap at 3 projects for 100% score (3 projects = 25% overall weight)
        return Math.min(1.0, projects / 3.0);
    }

    private double calculateCertificateScore(PortfolioExtendedProfile profile, CandidateSummaryDTO dto) {
        int certs = profile.getTotalCertificates() != null ? profile.getTotalCertificates() : 0;
        // Cap at 2 certificates for 100% score (2 certs = 10% overall weight)
        return Math.min(1.0, certs / 2.0);
    }

    private double calculateMissionScore(PortfolioExtendedProfile profile, CandidateSummaryDTO dto) {
        long completedMissions = 0;
        try {
            completedMissions = journeyRepository.countByUserIdAndStatus(
                    profile.getUser().getId(), 
                    Journey.JourneyStatus.COMPLETED
            );
        } catch (Exception e) {
            log.warn("Error counting missions", e);
        }
        // Cap at 3 missions for 100% score (3 missions = 15% overall weight)
        return Math.min(1.0, completedMissions / 3.0);
    }

    /**
     * Populate detailed breakdown context for rich UI explanations.
     * Computes matchedSkills, unmatchedSkills, counts, and auto-generates fitExplanation.
     */
    private void populateDetailedBreakdown(
            CandidateSummaryDTO dto,
            PortfolioExtendedProfile profile,
            JobPosting job,
            ShortTermJob shortTermJob,
            double skillScore,
            double projectScore,
            double certScore,
            double missionScore
    ) {
        // Parse candidate skills
        List<String> candidateSkills = Collections.emptyList();
        if (profile.getTopSkills() != null && !profile.getTopSkills().isBlank()) {
            try {
                candidateSkills = objectMapper.readValue(profile.getTopSkills(), List.class);
                candidateSkills = candidateSkills.stream()
                        .filter(Objects::nonNull)
                        .map(String::valueOf)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("Error parsing candidate skills for breakdown", e);
            }
        }
        dto.setTotalCandidateSkills(candidateSkills.size());

        // Parse required skills from job
        List<String> requiredSkills = Collections.emptyList();
        String requiredSkillsRaw = null;
        String primarySkill = null;
        if (shortTermJob != null) {
            requiredSkillsRaw = shortTermJob.getRequiredSkills();
            primarySkill = shortTermJob.getPrimarySkill();
        } else if (job != null) {
            requiredSkillsRaw = job.getRequiredSkills();
            primarySkill = job.getPrimarySkill();
        }

        if (requiredSkillsRaw != null && !requiredSkillsRaw.isBlank()) {
            try {
                requiredSkills = objectMapper.readValue(requiredSkillsRaw, List.class);
                requiredSkills = requiredSkills.stream()
                        .filter(Objects::nonNull)
                        .map(String::valueOf)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("Error parsing required skills for breakdown", e);
            }
        }
        dto.setTotalRequiredSkills(requiredSkills.size());

        // Compute matched / unmatched skills
        List<String> candidateSkillsLower = candidateSkills.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList());

        List<String> matched = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        for (String reqSkill : requiredSkills) {
            String reqLower = reqSkill.toLowerCase().trim();
            boolean found = candidateSkillsLower.stream().anyMatch(cs -> cs.contains(reqLower));
            if (found) {
                matched.add(reqSkill);
            } else {
                unmatched.add(reqSkill);
            }
        }
        dto.setMatchedSkills(matched);
        dto.setUnmatchedSkills(unmatched);

        // Counts
        int projects = profile.getTotalProjects() != null ? profile.getTotalProjects() : 0;
        int certs = profile.getTotalCertificates() != null ? profile.getTotalCertificates() : 0;
        long missions = 0;
        try {
            missions = journeyRepository.countByUserIdAndStatus(
                    profile.getUser().getId(),
                    Journey.JourneyStatus.COMPLETED
            );
        } catch (Exception e) {
            log.warn("Error counting missions for breakdown", e);
        }
        dto.setCompletedMissionsCount((int) missions);
        dto.setTotalCertificatesCount(certs);

        // Build fitExplanation
        StringBuilder explanation = new StringBuilder();

        // Skill explanation
        if (!requiredSkills.isEmpty()) {
            explanation.append("Kỹ năng: Khớp ")
                    .append(matched.size()).append("/").append(requiredSkills.size())
                    .append(" kỹ năng yêu cầu");
            if (!matched.isEmpty()) {
                explanation.append(" (").append(String.join(", ", matched.subList(0, Math.min(matched.size(), 4)))).append(")");
            }
            explanation.append(".");
            if (!unmatched.isEmpty()) {
                explanation.append(" Thiếu: ").append(String.join(", ", unmatched.subList(0, Math.min(unmatched.size(), 3)))).append(".");
            }
            if (Boolean.TRUE.equals(dto.getPrimarySkillMatch()) && primarySkill != null) {
                explanation.append(" ⭐ Có kỹ năng quan trọng nhất: ").append(primarySkill).append(".");
            }
        } else {
            explanation.append("Kỹ năng: Không có yêu cầu kỹ năng cụ thể cho vị trí này.");
        }

        // Project explanation
        explanation.append(" | Dự án: ").append(projects).append(" dự án trong portfolio");
        if (projects == 0) {
            explanation.append(" — chưa có dự án nào, cần bổ sung.");
        } else if (projects < 3) {
            explanation.append(" — khá ít, nên bổ sung thêm dự án liên quan.");
        } else {
            explanation.append(" — portfolio phong phú.");
        }

        // Certificate explanation
        explanation.append(" | Chứng chỉ: ").append(certs).append(" chứng chỉ");
        if (certs == 0) {
            explanation.append(" — chưa có chứng chỉ, khuyến khích bổ sung.");
        } else if (certs < 2) {
            explanation.append(" — có nhưng còn ít.");
        } else {
            explanation.append(" — đầy đủ chứng chỉ chuyên môn.");
        }

        // Mission explanation
        explanation.append(" | Missions: ").append(missions).append(" nhiệm vụ hoàn thành");
        if (missions == 0) {
            explanation.append(" — chưa hoàn thành nhiệm vụ nào trên hệ thống.");
        } else if (missions < 3) {
            explanation.append(" — đã có kinh nghiệm thực chiến cơ bản.");
        } else {
            explanation.append(" — kinh nghiệm thực chiến phong phú.");
        }

        dto.setFitExplanation(explanation.toString());
    }

    private double parseEstimatedHours(String estimatedDuration) {
        if (estimatedDuration == null || estimatedDuration.isBlank()) {
            return 40.0;
        }
        String duration = estimatedDuration.toLowerCase().trim();
        // Parse patterns like "2 hours", "1 day", "3 days", "1 week"
        java.util.regex.Pattern hourPattern = java.util.regex.Pattern.compile("([\\d.]+)\\s*h(our)?s?");
        java.util.regex.Pattern dayPattern = java.util.regex.Pattern.compile("([\\d.]+)\\s*d(ay)?s?");
        java.util.regex.Pattern weekPattern = java.util.regex.Pattern.compile("([\\d.]+)\\s*w(eek)?s?");

        java.util.regex.Matcher hourMatcher = hourPattern.matcher(duration);
        if (hourMatcher.find()) {
            return Double.parseDouble(hourMatcher.group(1));
        }
        java.util.regex.Matcher dayMatcher = dayPattern.matcher(duration);
        if (dayMatcher.find()) {
            return Double.parseDouble(dayMatcher.group(1)) * 8; // 8 hours per day
        }
        java.util.regex.Matcher weekMatcher = weekPattern.matcher(duration);
        if (weekMatcher.find()) {
            return Double.parseDouble(weekMatcher.group(1)) * 40; // 40 hours per week
        }
        return 40.0; // Default fallback
    }

    private int parseExperienceLevel(String level) {
        if (level == null) return 3;
        level = level.toLowerCase();
        if (level.contains("intern") || level.contains("entry") || level.contains("junior")) return 1;
        if (level.contains("senior") || level.contains("lead")) return 5;
        if (level.contains("mid") || level.contains("middle")) return 3;
        return 3;
    }

    private double getExperienceMatchScore(int candidateYears, int requiredYears) {
        if (candidateYears >= requiredYears) return 1.0;
        if (candidateYears >= requiredYears * 0.7) return 0.7;
        if (candidateYears >= requiredYears * 0.5) return 0.5;
        return 0.3;
    }

    private String determineMatchQuality(double score) {
        if (score >= EXCELLENT_THRESHOLD) return "EXCELLENT";
        if (score >= GOOD_THRESHOLD) return "GOOD";
        if (score >= FAIR_THRESHOLD) return "FAIR";
        return "POOR";
    }

    private List<CandidateSummaryDTO> applyAIMatching(List<CandidateSummaryDTO> candidates, Long jobId) {
        if (jobId == null || candidates.isEmpty()) {
            return candidates;
        }

        // Get top candidates for AI enhancement (limit to avoid rate limiting)
        List<CandidateSummaryDTO> topCandidates = candidates.stream()
                .limit(10)
                .collect(Collectors.toList());

        for (CandidateSummaryDTO candidate : topCandidates) {
            try {
                AICandidateMatchResponse aiResponse = aiSearchService.generateMatchExplanation(
                        jobId, candidate.getUserId());

                if (aiResponse != null && aiResponse.getFitSummary() != null) {
                    candidate.setAiFitSummary(aiResponse.getFitSummary());
                    if (aiResponse.getConfidenceScore() != null) {
                        // Blend AI score with rule-based score
                        double blendedScore = (candidate.getMatchScore() * 0.7) + (aiResponse.getConfidenceScore() * 0.3);
                        candidate.setMatchScore(Math.round(blendedScore * 100.0) / 100.0);
                    }
                }
            } catch (Exception e) {
                log.warn("AI matching failed for candidate {}: {}", candidate.getUserId(), e.getMessage());
            }
        }

        return candidates;
    }

    // ==================== Mapping Methods ====================

    private CandidateSummaryDTO mapProfileToDTO(PortfolioExtendedProfile profile) {
        if (profile == null) {
            return null;
        }

        // Check premium status
        boolean isPremium = false;
        try {
            var result = usageLimitService.canUseFeature(profile.getUserId(), FeatureType.PRIORITY_SUPPORT);
            isPremium = Boolean.TRUE.equals(result.getAllowed());
        } catch (Exception e) {
            log.debug("Error checking premium: {}", e.getMessage());
        }

        return CandidateSummaryDTO.builder()
                .userId(profile.getUserId())
                .fullName(profile.getFullName())
                .professionalTitle(profile.getProfessionalTitle())
                .avatarUrl(profile.getAvatarUrl())
                .customUrlSlug(profile.getCustomUrlSlug())
                .topSkills(profile.getTopSkills()) // Keep as JSON string
                .isHighlighted(isPremium)
                .isVerified(false)
                .hourlyRate(profile.getHourlyRate())
                .preferredCurrency(profile.getPreferredCurrency())
                .totalProjects(profile.getTotalProjects())
                .yearsOfExperience(profile.getYearsOfExperience())
                .location(profile.getLocation())
                .availabilityStatus(profile.getAvailabilityStatus())
                .build();
    }

    private CandidateSummaryDTO mapMatchScoreToDTO(CandidateMatchScore matchScore, JobPosting job) {
        Long candidateUserId = matchScore.getCandidate().getId();
        PortfolioExtendedProfile profile = portfolioRepository.findById(candidateUserId).orElse(null);
        CandidateSummaryDTO dto = mapProfileToDTO(profile);

        if (dto != null) {
            dto.setMatchScore(matchScore.getTotalScore().doubleValue());
            dto.setSkillMatchPercent((int) Math.round(matchScore.getSkillMatchScore().doubleValue() * 100));
        }

        return dto;
    }

    private CandidateSummaryDTO mapShortlistToDTO(RecruiterShortlist shortlist) {
        PortfolioExtendedProfile profile = portfolioRepository.findById(shortlist.getCandidate().getId()).orElse(null);
        if (profile == null) return null;

        CandidateSummaryDTO dto = mapProfileToDTO(profile);
        if (dto != null) {
            dto.setShortlistId(shortlist.getId());
            dto.setShortlistStatus(shortlist.getShortlistStatus().name());
            dto.setShortlistNotes(shortlist.getNotes());
        }
        return dto;
    }

    // Helper method kept for potential future use
    @SuppressWarnings("unused")
    private List<String> parseJsonSkills(String skillsJson) {
        if (skillsJson == null || skillsJson.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(skillsJson, List.class);
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }
}
