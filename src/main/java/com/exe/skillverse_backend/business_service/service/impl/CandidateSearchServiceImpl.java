package com.exe.skillverse_backend.business_service.service.impl;

import com.exe.skillverse_backend.ai_search_service.AISearchService;
import com.exe.skillverse_backend.ai_search_service.dto.AICandidateMatchResponse;
import com.exe.skillverse_backend.business_service.dto.request.CandidateSearchRequest;
import com.exe.skillverse_backend.business_service.dto.response.RecruitmentSessionResponse;
import com.exe.skillverse_backend.business_service.entity.CandidateMatchScore;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.RecruiterShortlist;
import com.exe.skillverse_backend.business_service.entity.ShortTermJob;
import com.exe.skillverse_backend.business_service.entity.enums.RecruitmentJobContextType;
import com.exe.skillverse_backend.business_service.entity.enums.RecruitmentSessionSource;
import com.exe.skillverse_backend.business_service.repository.CandidateMatchScoreRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobRepository;
import com.exe.skillverse_backend.business_service.service.SearchAnalyticsService;
import com.exe.skillverse_backend.business_service.repository.CandidateSearchSessionRepository;
import com.exe.skillverse_backend.business_service.repository.JobPostingRepository;
import com.exe.skillverse_backend.business_service.repository.RecruiterShortlistRepository;
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
    private final ObjectMapper objectMapper;

    // Default scoring weights
    private static final BigDecimal SKILL_WEIGHT = new BigDecimal("0.40");
    private static final BigDecimal EXPERIENCE_WEIGHT = new BigDecimal("0.25");
    private static final BigDecimal BUDGET_WEIGHT = new BigDecimal("0.20");
    private static final BigDecimal PREMIUM_WEIGHT = new BigDecimal("0.10");
    private static final BigDecimal ACTIVITY_WEIGHT = new BigDecimal("0.05");

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
        int size = request.getSize() != null && request.getSize() > 0 ? request.getSize() : 20;
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<PortfolioExtendedProfile> profiles = portfolioRepository.findPortfoliosOpenToOffers(Pageable.unpaged());

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

        // Apply AI matching if enabled and available
        if (Boolean.TRUE.equals(request.getEnableAIMatching()) && aiSearchService.isEnabled()) {
            scoredCandidates = applyAIMatching(scoredCandidates, request.getJobId());
        }

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
            throw new ForbiddenException("Bạn cần gói Premium Recruiter để sử dụng tính năng AI matching.");
        }

        // Check AI is available
        if (!aiSearchService.isEnabled()) {
            throw new BadRequestException("AI matching hiện không khả dụng. Vui lòng thử lại sau.");
        }

        return aiSearchService.generateMatchExplanation(jobId, candidateId);
    }

    @Override
    @Transactional(readOnly = true)
    public Object getShortTermJobMatchExplanation(Long recruiterId, Long shortTermJobId, Long candidateId) {
        // Check permission
        if (!hasCandidateAccess(recruiterId)) {
            throw new ForbiddenException("Bạn cần gói Premium Recruiter để sử dụng tính năng AI matching.");
        }

        // Check AI is available
        if (!aiSearchService.isEnabled()) {
            throw new BadRequestException("AI matching hiện không khả dụng. Vui lòng thử lại sau.");
        }

        // Verify short-term job ownership
        var shortTermJob = shortTermJobRepository.findById(shortTermJobId).orElse(null);
        if (shortTermJob == null) {
            throw new BadRequestException("Không tìm thấy công việc ngắn hạn.");
        }
        if (!shortTermJob.getRecruiterProfile().getUser().getId().equals(recruiterId)) {
            throw new ForbiddenException("Bạn không có quyền truy cập công việc này.");
        }

        return aiSearchService.generateShortTermJobMatchExplanation(shortTermJobId, candidateId);
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

        // Get candidates with match scores
        Pageable pageable = PageRequest.of(page, size, Sort.by("totalScore").descending());
        Page<CandidateMatchScore> matchScores = matchScoreRepository.findByJobPostingIdOrderByScoreDesc(jobId, pageable);

        // Map to DTO
        List<CandidateSummaryDTO> candidates = matchScores.getContent().stream()
                .map(ms -> mapMatchScoreToDTO(ms, job))
                .collect(Collectors.toList());

        return new PageImpl<>(candidates, pageable, matchScores.getTotalElements());
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
            com.exe.skillverse_backend.business_service.dto.request.UpdateRecruitmentStatusRequest statusRequest =
                    com.exe.skillverse_backend.business_service.dto.request.UpdateRecruitmentStatusRequest.builder()
                            .status(com.exe.skillverse_backend.business_service.entity.enums.RecruitmentSessionStatus.INVITED)
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

        // Calculate component scores — pass both job types
        double skillScore = calculateSkillScore(profile, job, shortTermJob, request);
        double experienceScore = calculateExperienceScore(profile, job, shortTermJob, request);
        double budgetScore = calculateBudgetScore(profile, job, shortTermJob, request);
        double premiumScore = calculatePremiumScore(profile);
        double activityScore = calculateActivityScore(profile);

        // Calculate total weighted score
        double totalScore = (skillScore * SKILL_WEIGHT.doubleValue())
                + (experienceScore * EXPERIENCE_WEIGHT.doubleValue())
                + (budgetScore * BUDGET_WEIGHT.doubleValue())
                + (premiumScore * PREMIUM_WEIGHT.doubleValue())
                + (activityScore * ACTIVITY_WEIGHT.doubleValue());

        dto.setMatchScore(Math.round(totalScore * 100.0) / 100.0);
        dto.setMatchQuality(determineMatchQuality(totalScore));

        // Set skill match percentage
        dto.setSkillMatchPercent((int) Math.round(skillScore * 100));

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
                comparator = Comparator.comparingInt(
                        candidate -> candidate.getSkillMatchPercent() != null ? candidate.getSkillMatchPercent() : 0
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

    private double calculateSkillScore(PortfolioExtendedProfile profile, JobPosting job, ShortTermJob shortTermJob, CandidateSearchRequest request) {
        double baseScore = 0.5; // Default if no specific skills to match

        // Try short-term job first, then long-term job
        String requiredSkillsRaw = null;
        if (shortTermJob != null && shortTermJob.getRequiredSkills() != null) {
            requiredSkillsRaw = shortTermJob.getRequiredSkills();
        } else if (job != null && job.getRequiredSkills() != null) {
            requiredSkillsRaw = job.getRequiredSkills();
        }

        if (requiredSkillsRaw != null && !requiredSkillsRaw.isBlank()) {
            try {
                List<String> requiredSkills = objectMapper.readValue(requiredSkillsRaw, List.class);
                List<String> candidateSkills = profile.getTopSkills() != null
                        ? objectMapper.readValue(profile.getTopSkills(), List.class)
                        : Collections.emptyList();

                if (!requiredSkills.isEmpty() && !candidateSkills.isEmpty()) {
                    // Normalize to lowercase
                    requiredSkills = requiredSkills.stream().map(String::toLowerCase).collect(Collectors.toList());
                    candidateSkills = candidateSkills.stream().map(String::toLowerCase).collect(Collectors.toList());

                    long matchCount = candidateSkills.stream()
                            .filter(requiredSkills::contains)
                            .count();

                    baseScore = Math.min(1.0, (double) matchCount / requiredSkills.size());
                }
            } catch (JsonProcessingException e) {
                log.warn("Error parsing skills for scoring: {}", e.getMessage());
            }
        }

        // Apply query filter if present
        if (request.getSkills() != null && !request.getSkills().isEmpty()) {
            List<String> querySkills = Arrays.asList(request.getSkills().split(","));
            querySkills = querySkills.stream().map(String::toLowerCase).map(String::trim).collect(Collectors.toList());

            if (profile.getTopSkills() != null) {
                try {
                    List<String> candidateSkills = objectMapper.readValue(profile.getTopSkills(), List.class);
                    candidateSkills = candidateSkills.stream().map(String::toLowerCase).collect(Collectors.toList());

                    long queryMatchCount = candidateSkills.stream()
                            .filter(querySkills::contains)
                            .count();

                    // Blend with job skill score
                    baseScore = (baseScore + Math.min(1.0, (double) queryMatchCount / querySkills.size())) / 2;
                } catch (JsonProcessingException e) {
                    log.warn("Error parsing candidate skills: {}", e.getMessage());
                }
            }
        }

        return baseScore;
    }

    private double calculateExperienceScore(PortfolioExtendedProfile profile, JobPosting job, ShortTermJob shortTermJob, CandidateSearchRequest request) {
        double score = 0.5; // Default

        Integer candidateExp = profile.getYearsOfExperience();
        if (candidateExp == null) {
            return score;
        }

        // Check against short-term job requirements (preferred) or long-term job
        if (shortTermJob != null) {
            // Short-term jobs use urgency to infer experience expectations
            if (shortTermJob.getUrgency() != null) {
                switch (shortTermJob.getUrgency()) {
                    case ASAP:
                        score = 0.9; // Needs experienced person immediately
                        break;
                    case VERY_URGENT:
                        score = 0.8;
                        break;
                    case URGENT:
                        score = 0.7;
                        break;
                    default:
                        // NORMAL: flexible, favor moderately experienced
                        score = getExperienceMatchScore(candidateExp, 1);
                        break;
                }
            }
        } else if (job != null && job.getExperienceLevel() != null) {
            int requiredYears = parseExperienceLevel(job.getExperienceLevel());
            score = getExperienceMatchScore(candidateExp, requiredYears);
        }

        // Apply filter
        if (request.getMinExperience() != null && candidateExp < request.getMinExperience()) {
            return 0.0;
        }
        if (request.getMaxExperience() != null && candidateExp > request.getMaxExperience()) {
            return 0.0;
        }

        return score;
    }

    private double calculateBudgetScore(PortfolioExtendedProfile profile, JobPosting job, ShortTermJob shortTermJob, CandidateSearchRequest request) {
        double score = 0.5; // Default

        if (profile.getHourlyRate() == null) {
            return score;
        }

        if (shortTermJob != null && shortTermJob.getBudget() != null) {
            // Short-term jobs have a fixed budget — estimate hours from estimatedDuration
            double candidateRate = profile.getHourlyRate();
            double fixedBudget = shortTermJob.getBudget().doubleValue();

            // Rough estimation: if no estimated duration, assume 40 hours
            double estimatedHours = 40.0;
            if (shortTermJob.getEstimatedDuration() != null) {
                estimatedHours = parseEstimatedHours(shortTermJob.getEstimatedDuration());
            }

            double impliedHourlyRate = fixedBudget / estimatedHours;

            if (candidateRate <= impliedHourlyRate) {
                score = 1.0; // Candidate is within or under budget
            } else if (candidateRate <= impliedHourlyRate * 1.2) {
                score = 0.7; // Slightly over
            } else if (candidateRate <= impliedHourlyRate * 1.5) {
                score = 0.4;
            } else {
                score = 0.2;
            }
        } else if (job != null && job.getMinBudget() != null && job.getMaxBudget() != null) {
            double candidateRate = profile.getHourlyRate();
            double minBudget = job.getMinBudget().doubleValue() / 160; // Convert monthly to hourly
            double maxBudget = job.getMaxBudget().doubleValue() / 160;

            if (candidateRate >= minBudget && candidateRate <= maxBudget) {
                score = 1.0;
            } else if (candidateRate < minBudget) {
                score = 1.0; // Under budget is good
            } else if (candidateRate <= maxBudget * 1.2) {
                score = 0.7; // Slightly over
            } else {
                score = 0.3;
            }
        }

        // Apply rate filter
        if (request.getMinHourlyRate() != null && profile.getHourlyRate() < request.getMinHourlyRate()) {
            return 0.0;
        }
        if (request.getMaxHourlyRate() != null && profile.getHourlyRate() > request.getMaxHourlyRate()) {
            return 0.0;
        }

        return score;
    }

    private double calculatePremiumScore(PortfolioExtendedProfile profile) {
        // Check if candidate has premium features
        try {
            var result = usageLimitService.canUseFeature(profile.getUserId(), FeatureType.PRIORITY_SUPPORT);
            if (Boolean.TRUE.equals(result.getAllowed())) {
                return 1.0; // Premium candidate gets bonus
            }
        } catch (Exception e) {
            log.debug("Error checking premium status: {}", e.getMessage());
        }
        return 0.0;
    }

    private double calculateActivityScore(PortfolioExtendedProfile profile) {
        // Based on profile update time
        LocalDateTime updatedAt = profile.getUpdatedAt();
        if (updatedAt == null) {
            return 0.5;
        }

        long daysSinceUpdate = java.time.Duration.between(updatedAt, LocalDateTime.now()).toDays();

        if (daysSinceUpdate < 7) return 1.0;
        if (daysSinceUpdate < 30) return 0.8;
        if (daysSinceUpdate < 90) return 0.6;
        if (daysSinceUpdate < 180) return 0.4;
        return 0.2;
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
                .hourlyRate(profile.getHourlyRate())
                .preferredCurrency(profile.getPreferredCurrency())
                .totalProjects(profile.getTotalProjects())
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
