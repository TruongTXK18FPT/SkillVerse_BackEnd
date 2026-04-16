package com.exe.skillverse_backend.ai_search_service.impl;

import com.exe.skillverse_backend.ai_search_service.AISearchService;
import com.exe.skillverse_backend.ai_search_service.config.AISearchConfig;
import com.exe.skillverse_backend.ai_search_service.dto.AICandidateMatchRequest;
import com.exe.skillverse_backend.ai_search_service.dto.AICandidateMatchResponse;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.ShortTermJob;
import com.exe.skillverse_backend.business_service.repository.JobPostingRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobRepository;
import com.exe.skillverse_backend.portfolio_service.dto.CompletedMissionDTO;
import com.exe.skillverse_backend.portfolio_service.entity.PortfolioExtendedProfile;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioExtendedProfileRepository;
import com.exe.skillverse_backend.portfolio_service.service.PortfolioService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Implementation of AI Search Service using Mistral API.
 * Provides AI-assisted candidate matching with fallback to rule-based matching.
 */
@Service
@Slf4j
public class AISearchServiceImpl implements AISearchService {

    private final AISearchConfig aiSearchConfig;
    private final JobPostingRepository jobPostingRepository;
    private final ShortTermJobRepository shortTermJobRepository;
    private final PortfolioExtendedProfileRepository portfolioRepository;
    private final PortfolioService portfolioService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public AISearchServiceImpl(
            AISearchConfig aiSearchConfig,
            JobPostingRepository jobPostingRepository,
            ShortTermJobRepository shortTermJobRepository,
            PortfolioExtendedProfileRepository portfolioRepository,
            PortfolioService portfolioService,
            ObjectMapper objectMapper,
            @Qualifier("aiSearchRestTemplate") RestTemplate restTemplate) {
        this.aiSearchConfig = aiSearchConfig;
        this.jobPostingRepository = jobPostingRepository;
        this.shortTermJobRepository = shortTermJobRepository;
        this.portfolioRepository = portfolioRepository;
        this.portfolioService = portfolioService;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    // Rate limiting: track requests per minute and tokens per day
    private final AtomicInteger requestsThisMinute = new AtomicInteger(0);
    private final AtomicInteger tokensThisDay = new AtomicInteger(0);
    private volatile LocalDateTime minuteWindowStart = LocalDateTime.now();
    private volatile LocalDateTime dayWindowStart = LocalDateTime.now().toLocalDate().atStartOfDay();

    // Cache for match results (jobId + candidateId -> result)
    private final Map<String, AICandidateMatchResponse> matchCache = new ConcurrentHashMap<>();


    @Override
    public AICandidateMatchResponse generateMatchExplanation(Long jobId, Long candidateId) {
        long startTime = System.currentTimeMillis();

        // Check cache first
        String cacheKey = jobId + "_" + candidateId;
        if (aiSearchConfig.isCacheEnabled() && matchCache.containsKey(cacheKey)) {
            AICandidateMatchResponse cached = matchCache.get(cacheKey);
            if (cached != null && !Boolean.TRUE.equals(cached.getIsFallback())) {
                log.debug("Using cached match result for job {} candidate {}", jobId, candidateId);
                return cached;
            }

            // Try to refresh cached fallback responses to avoid stale 50% confidence outputs.
            if (cached != null && Boolean.TRUE.equals(cached.getIsFallback()) && isEnabled()) {
                log.debug("Refreshing cached fallback result for job {} candidate {}", jobId, candidateId);
                matchCache.remove(cacheKey);
            }
        }

        // Check if AI is enabled
        if (!isEnabled()) {
            return generateFallbackMatch(jobId, candidateId, startTime);
        }

        // Check rate limit
        if (!canMakeRequest()) {
            log.warn("Rate limit exceeded, using fallback matching");
            return generateFallbackMatch(jobId, candidateId, startTime);
        }

        try {
            // Get job and candidate data
            JobPosting job = jobPostingRepository.findById(jobId)
                    .orElseThrow(() -> new RuntimeException("Job not found"));

            User candidate = portfolioRepository.findById(candidateId)
                    .map(profile -> profile.getUser())
                    .orElseThrow(() -> new RuntimeException("Candidate not found"));

            PortfolioExtendedProfile profile = portfolioRepository.findById(candidateId).orElse(null);

            // Build request
            AICandidateMatchRequest request = buildMatchRequest(job, candidate, profile);

            // Call Mistral API
            AICandidateMatchResponse response = callMistralAPI(request);

            // Update rate limiting
            resetRateLimitWindowsIfNeeded();
            requestsThisMinute.incrementAndGet();
            tokensThisDay.addAndGet(estimateTokens(response.getFitSummary() + response.getReasoning()));

            // Cache result
            if (aiSearchConfig.isCacheEnabled() && !Boolean.TRUE.equals(response.getIsFallback())) {
                matchCache.put(cacheKey, response);
            }

            // Log for observability
            log.info("AI match generated: job={}, candidate={}, quality={}, time={}ms",
                    jobId, candidateId, response.getMatchQuality(),
                    System.currentTimeMillis() - startTime);

            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            return response;

        } catch (Exception e) {
            log.error("Error generating AI match for job {} candidate {}: {}",
                    jobId, candidateId, e.getMessage(), e);
            return generateFallbackMatch(jobId, candidateId, startTime);
        }
    }

    @Override
    public List<AICandidateMatchResponse> generateBulkMatchExplanations(Long jobId, List<Long> candidateIds) {
        return candidateIds.stream()
                .map(candidateId -> generateMatchExplanation(jobId, candidateId))
                .collect(Collectors.toList());
    }

    @Override
    public AICandidateMatchResponse generateShortTermJobMatchExplanation(Long shortTermJobId, Long candidateId) {
        long startTime = System.currentTimeMillis();

        // Check cache first
        String cacheKey = "st_" + shortTermJobId + "_" + candidateId;
        if (aiSearchConfig.isCacheEnabled() && matchCache.containsKey(cacheKey)) {
            AICandidateMatchResponse cached = matchCache.get(cacheKey);
            if (cached != null && !Boolean.TRUE.equals(cached.getIsFallback())) {
                log.debug("Using cached short-term match result for job {} candidate {}", shortTermJobId, candidateId);
                return cached;
            }

            if (cached != null && Boolean.TRUE.equals(cached.getIsFallback()) && isEnabled()) {
                log.debug("Refreshing cached fallback short-term result for job {} candidate {}", shortTermJobId, candidateId);
                matchCache.remove(cacheKey);
            }
        }

        // Check if AI is enabled
        if (!isEnabled()) {
            return generateShortTermFallbackMatch(shortTermJobId, candidateId, startTime);
        }

        // Check rate limit
        if (!canMakeRequest()) {
            log.warn("Rate limit exceeded for short-term matching, using fallback");
            return generateShortTermFallbackMatch(shortTermJobId, candidateId, startTime);
        }

        try {
            // Get short-term job and candidate data
            ShortTermJob shortTermJob =
                    shortTermJobRepository.findById(shortTermJobId)
                    .orElseThrow(() -> new RuntimeException("Short-term job not found"));

            User candidate = portfolioRepository.findById(candidateId)
                    .map(profile -> profile.getUser())
                    .orElseThrow(() -> new RuntimeException("Candidate not found"));

            PortfolioExtendedProfile profile = portfolioRepository.findById(candidateId).orElse(null);

            // Build request for short-term job
            AICandidateMatchRequest request = buildShortTermMatchRequest(shortTermJob, candidate, profile);

            // Call Mistral API
            AICandidateMatchResponse response = callMistralAPI(request);

            // Update rate limiting
            resetRateLimitWindowsIfNeeded();
            requestsThisMinute.incrementAndGet();
            tokensThisDay.addAndGet(estimateTokens(response.getFitSummary() + response.getReasoning()));

            // Cache result
            if (aiSearchConfig.isCacheEnabled() && !Boolean.TRUE.equals(response.getIsFallback())) {
                matchCache.put(cacheKey, response);
            }

            log.info("AI short-term match generated: job={}, candidate={}, quality={}, time={}ms",
                    shortTermJobId, candidateId, response.getMatchQuality(),
                    System.currentTimeMillis() - startTime);

            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            return response;

        } catch (Exception e) {
            log.error("Error generating AI short-term match for job {} candidate {}: {}",
                    shortTermJobId, candidateId, e.getMessage(), e);
            return generateShortTermFallbackMatch(shortTermJobId, candidateId, startTime);
        }
    }

    private AICandidateMatchRequest buildShortTermMatchRequest(
            ShortTermJob shortTermJob,
            User candidate,
            PortfolioExtendedProfile profile) {
        CandidateMissionInsights missionInsights = buildMissionInsights(candidate.getId());

        return AICandidateMatchRequest.builder()
                .jobId(shortTermJob.getId())
                .candidateId(candidate.getId())
                .jobTitle(shortTermJob.getTitle())
                .jobDescription(shortTermJob.getDescription())
                .requiredSkills(shortTermJob.getRequiredSkills())
                .minBudget(shortTermJob.getBudget().toString())
                .maxBudget(shortTermJob.getBudget().toString())
                .experienceLevel(null) // Short-term jobs don't have experience level
                .jobType("SHORT_TERM_GIG")
                .candidateName(profile != null ? profile.getFullName() : candidate.getEmail())
                .professionalTitle(profile != null ? profile.getProfessionalTitle() : null)
                .bio(profile != null ? profile.getBio() : null)
                .topSkills(profile != null ? profile.getTopSkills() : null)
                .yearsOfExperience(profile != null ? profile.getYearsOfExperience() : null)
                .hourlyRate(profile != null && profile.getHourlyRate() != null
                        ? profile.getHourlyRate().toString() : null)
                .totalProjects(profile != null ? profile.getTotalProjects() : null)
                .totalCertificates(profile != null ? profile.getTotalCertificates() : null)
                .completedMissionCount(missionInsights.completedMissionCount())
                .averageMissionRating(missionInsights.averageMissionRating())
                .missionSkills(missionInsights.missionSkillsJson())
                .recentMissionHighlights(missionInsights.recentMissionHighlights())
                .build();
    }

    private AICandidateMatchResponse generateShortTermFallbackMatch(Long shortTermJobId, Long candidateId, long startTime) {
        log.debug("Using fallback rule-based matching for short-term job {} candidate {}", shortTermJobId, candidateId);

        try {
            ShortTermJob shortTermJob =
                    shortTermJobRepository.findById(shortTermJobId).orElse(null);
            PortfolioExtendedProfile profile = portfolioRepository.findById(candidateId).orElse(null);

            if (shortTermJob == null || profile == null) {
                return createShortTermBasicResponse(shortTermJobId, candidateId);
            }

            // Calculate rule-based scores
            double skillScore = calculateSkillMatchForShortTermJob(shortTermJob, profile);
            double expScore = calculateExperienceMatch(shortTermJob, profile);
            double budgetScore = calculateBudgetMatch(shortTermJob, profile);
                double missionScore = calculateMissionScore(buildMissionInsights(candidateId));

                double totalScore = (skillScore * 0.45) + (expScore * 0.25) + (budgetScore * 0.15) + (missionScore * 0.15);

                String summary = String.format("Ứng viên có kinh nghiệm %s, kỹ năng %s và lịch sử nhiệm vụ %s cho công việc gig. Mức lương: %s.",
                    getExperienceLabelVi(expScore),
                    getSkillLabelVi(skillScore),
                    getMissionLabelVi(missionScore),
                    getBudgetLabelVi(budgetScore));

            return AICandidateMatchResponse.builder()
                    .jobId(shortTermJobId)
                    .candidateId(candidateId)
                    .fitSummary(summary)
                    .skillSignals(extractMatchingSkillsForShortTermJob(shortTermJob, profile))
                    .reasoning("Đánh giá dựa trên quy tắc cho gig - Kỹ năng: " + Math.round(skillScore * 100) + "% khớp, "
                            + "Kinh nghiệm: " + Math.round(expScore * 100) + "% phù hợp, "
                            + "Mức lương: " + Math.round(budgetScore * 100) + "% hợp lý, "
                            + "Nhiệm vụ đã hoàn thành: " + Math.round(missionScore * 100) + "%")
                    .confidenceScore(totalScore)
                    .matchQuality(determineMatchQuality(totalScore))
                    .modelUsed("rule-based-fallback")
                    .isFallback(true)
                        .processingTimeMs(calculateProcessingTime(startTime))
                    .build();

        } catch (Exception e) {
            log.error("Error in short-term fallback matching: {}", e.getMessage());
            return createShortTermBasicResponse(shortTermJobId, candidateId);
        }
    }

    private double calculateSkillMatchForShortTermJob(
            ShortTermJob shortTermJob,
            PortfolioExtendedProfile profile) {
        if (shortTermJob == null || shortTermJob.getRequiredSkills() == null || shortTermJob.getRequiredSkills().isBlank()) {
            return 0.5;
        }
        if (profile == null || profile.getTopSkills() == null) {
            return 0.3;
        }
        try {
            List<String> requiredSkills = parseStringList(shortTermJob.getRequiredSkills());
            List<String> candidateSkills = parseStringList(profile.getTopSkills());
            if (requiredSkills.isEmpty() || candidateSkills.isEmpty()) {
                return 0.5;
            }
            requiredSkills = requiredSkills.stream().map(String::toLowerCase).collect(Collectors.toList());
            candidateSkills = candidateSkills.stream().map(String::toLowerCase).collect(Collectors.toList());
            long matchCount = candidateSkills.stream().filter(requiredSkills::contains).count();
            return Math.min(1.0, (double) matchCount / requiredSkills.size());
        } catch (JsonProcessingException e) {
            log.warn("Error parsing skills for short-term scoring: {}", e.getMessage());
            return 0.5;
        }
    }

    private List<AICandidateMatchResponse.SkillSignal> extractMatchingSkillsForShortTermJob(
            ShortTermJob shortTermJob,
            PortfolioExtendedProfile profile) {
        List<AICandidateMatchResponse.SkillSignal> signals = new ArrayList<>();
        if (shortTermJob == null || profile == null) return signals;
        try {
            List<String> jobSkills = shortTermJob.getRequiredSkills() != null
                ? parseStringList(shortTermJob.getRequiredSkills())
                    : Collections.emptyList();
            List<String> profileSkills = profile.getTopSkills() != null
                ? parseStringList(profile.getTopSkills())
                    : Collections.emptyList();
            jobSkills = jobSkills.stream().map(String::toLowerCase).collect(Collectors.toList());
            profileSkills = profileSkills.stream().map(String::toLowerCase).collect(Collectors.toList());
            for (String skill : profileSkills) {
                if (jobSkills.contains(skill)) {
                    signals.add(AICandidateMatchResponse.SkillSignal.builder()
                            .skill(skill)
                            .evidence("Found in profile top skills")
                            .isRequired(true)
                            .relevanceScore(1.0)
                            .build());
                }
            }
        } catch (JsonProcessingException e) {
            log.error("Error extracting matching skills for short-term: {}", e.getMessage());
        }
        return signals;
    }

    private AICandidateMatchResponse createShortTermBasicResponse(Long shortTermJobId, Long candidateId) {
        return AICandidateMatchResponse.builder()
                .jobId(shortTermJobId)
                .candidateId(candidateId)
                .fitSummary("Unable to generate detailed match analysis for this gig.")
                .confidenceScore(0.5)
                .matchQuality(AICandidateMatchResponse.MatchQuality.FAIR)
                .isFallback(true)
                .build();
    }

    private double calculateExperienceMatch(ShortTermJob shortTermJob, PortfolioExtendedProfile profile) {
        if (profile.getYearsOfExperience() == null) {
            return 0.5;
        }
        int candidateYears = profile.getYearsOfExperience();
        // Short-term jobs favor experienced candidates who can deliver quickly
        if (candidateYears >= 2) return 1.0;
        if (candidateYears >= 1) return 0.7;
        return 0.4;
    }

    private double calculateBudgetMatch(ShortTermJob shortTermJob, PortfolioExtendedProfile profile) {
        if (profile.getHourlyRate() == null) {
            return 0.5;
        }
        double candidateRate = profile.getHourlyRate();
        double fixedBudget = shortTermJob.getBudget().doubleValue();
        double estimatedHours = 40.0;
        if (shortTermJob.getEstimatedDuration() != null) {
            estimatedHours = parseEstimatedHours(shortTermJob.getEstimatedDuration());
        }
        double impliedHourlyRate = fixedBudget / estimatedHours;
        if (candidateRate <= impliedHourlyRate) {
            return 1.0;
        } else if (candidateRate <= impliedHourlyRate * 1.2) {
            return 0.7;
        } else if (candidateRate <= impliedHourlyRate * 1.5) {
            return 0.4;
        }
        return 0.2;
    }

    @Override
    public boolean isEnabled() {
        return aiSearchConfig.isEnabled()
                && aiSearchConfig.getApiKey() != null
                && !aiSearchConfig.getApiKey().isEmpty();
    }

    @Override
    public boolean canMakeRequest() {
        if (!isEnabled()) return false;

        resetRateLimitWindowsIfNeeded();

        // Check per-minute limit
        if (requestsThisMinute.get() >= aiSearchConfig.getMaxRequestsPerMinute()) {
            return false;
        }

        // Check daily token limit
        if (tokensThisDay.get() >= aiSearchConfig.getMaxTokensPerDay()) {
            return false;
        }

        return true;
    }

    private synchronized void resetRateLimitWindowsIfNeeded() {
        LocalDateTime now = LocalDateTime.now();

        if (now.isAfter(minuteWindowStart.plusMinutes(1))) {
            requestsThisMinute.set(0);
            minuteWindowStart = now;
        }

        if (!now.toLocalDate().equals(dayWindowStart.toLocalDate())) {
            tokensThisDay.set(0);
            dayWindowStart = now.toLocalDate().atStartOfDay();
        }
    }

    // ==================== Private Methods ====================

    private CandidateMissionInsights buildMissionInsights(Long candidateId) {
        try {
            List<CompletedMissionDTO> missions = portfolioService.getPublicCompletedMissions(candidateId);
            if (missions == null || missions.isEmpty()) {
                return CandidateMissionInsights.empty();
            }

            long ratedCount = missions.stream().filter(m -> m.getRating() != null).count();
            Double avgRating = ratedCount > 0
                    ? missions.stream()
                    .filter(m -> m.getRating() != null)
                    .mapToDouble(CompletedMissionDTO::getRating)
                    .average()
                    .orElse(0.0)
                    : null;

            Set<String> missionSkills = missions.stream()
                    .filter(m -> m.getRequiredSkills() != null)
                    .flatMap(m -> m.getRequiredSkills().stream())
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(skill -> !skill.isEmpty())
                    .map(String::toLowerCase)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            String missionSkillsJson = objectMapper.writeValueAsString(missionSkills);
            String highlights = missions.stream()
                    .sorted(Comparator.comparing(
                            CompletedMissionDTO::getCompletedAt,
                            Comparator.nullsLast(Comparator.reverseOrder())
                    ))
                    .limit(3)
                    .map(this::formatMissionHighlight)
                    .collect(Collectors.joining(" | "));

            return new CandidateMissionInsights(
                    missions.size(),
                    avgRating,
                    missionSkillsJson,
                    highlights.isBlank() ? "Không có dữ liệu nhiệm vụ nổi bật" : highlights
            );
        } catch (Exception e) {
            log.debug("Unable to build mission insights for candidate {}: {}", candidateId, e.getMessage());
            return CandidateMissionInsights.empty();
        }
    }

    private String formatMissionHighlight(CompletedMissionDTO mission) {
        if (mission == null) {
            return "Nhiệm vụ không xác định";
        }

        StringBuilder highlight = new StringBuilder();
        highlight.append(safeText(mission.getJobTitle(), "Nhiệm vụ đã hoàn thành"));

        if (mission.getRating() != null) {
            highlight.append(" (rating ").append(String.format("%.1f", mission.getRating())).append("/5)");
        }

        if (mission.getRequiredSkills() != null && !mission.getRequiredSkills().isEmpty()) {
            String joinedSkills = mission.getRequiredSkills().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(skill -> !skill.isEmpty())
                    .limit(4)
                    .collect(Collectors.joining(", "));
            if (!joinedSkills.isBlank()) {
                highlight.append(" - skills: ").append(joinedSkills);
            }
        }

        return highlight.toString();
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private List<String> parseStringList(String rawValue) throws JsonProcessingException {
        if (rawValue == null || rawValue.isBlank()) {
            return Collections.emptyList();
        }
        return objectMapper.readValue(
                rawValue,
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
        );
    }

    private record CandidateMissionInsights(
            int completedMissionCount,
            Double averageMissionRating,
            String missionSkillsJson,
            String recentMissionHighlights
    ) {
        static CandidateMissionInsights empty() {
            return new CandidateMissionInsights(0, null, "[]", "Không có dữ liệu nhiệm vụ đã hoàn thành");
        }
    }

    /**
     * Parse estimated duration string to hours.
     * Handles formats like: "2 hours", "1 day", "3 weeks", "1 month"
     */
    private double parseEstimatedHours(String duration) {
        if (duration == null || duration.isBlank()) {
            return 40.0; // default
        }
        String lower = duration.toLowerCase().trim();
        try {
            if (lower.contains("hour")) {
                return Double.parseDouble(lower.replaceAll("[^0-9.]", ""));
            } else if (lower.contains("day")) {
                double days = Double.parseDouble(lower.replaceAll("[^0-9.]", ""));
                return days * 8;
            } else if (lower.contains("week")) {
                double weeks = Double.parseDouble(lower.replaceAll("[^0-9.]", ""));
                return weeks * 40;
            } else if (lower.contains("month")) {
                double months = Double.parseDouble(lower.replaceAll("[^0-9.]", ""));
                return months * 160;
            } else {
                return Double.parseDouble(lower);
            }
        } catch (NumberFormatException e) {
            return 40.0;
        }
    }

    private AICandidateMatchRequest buildMatchRequest(JobPosting job, User candidate, PortfolioExtendedProfile profile) {
        CandidateMissionInsights missionInsights = buildMissionInsights(candidate.getId());

        return AICandidateMatchRequest.builder()
                .jobId(job.getId())
                .candidateId(candidate.getId())
                .jobTitle(job.getTitle())
                .jobDescription(job.getDescription())
                .requiredSkills(job.getRequiredSkills())
                .minBudget(job.getMinBudget().toString())
                .maxBudget(job.getMaxBudget().toString())
                .experienceLevel(job.getExperienceLevel())
                .jobType(job.getJobType())
                .candidateName(profile != null ? profile.getFullName() : candidate.getEmail())
                .professionalTitle(profile != null ? profile.getProfessionalTitle() : null)
                .bio(profile != null ? profile.getBio() : null)
                .topSkills(profile != null ? profile.getTopSkills() : null)
                .yearsOfExperience(profile != null ? profile.getYearsOfExperience() : null)
                .hourlyRate(profile != null && profile.getHourlyRate() != null
                        ? profile.getHourlyRate().toString() : null)
                .totalProjects(profile != null ? profile.getTotalProjects() : null)
                .totalCertificates(profile != null ? profile.getTotalCertificates() : null)
                .completedMissionCount(missionInsights.completedMissionCount())
                .averageMissionRating(missionInsights.averageMissionRating())
                .missionSkills(missionInsights.missionSkillsJson())
                .recentMissionHighlights(missionInsights.recentMissionHighlights())
                .build();
    }

    private AICandidateMatchResponse callMistralAPI(AICandidateMatchRequest request) {
        // Build prompt
        String prompt = buildPrompt(request);

        // Prepare API request (simplified - actual implementation would use proper HTTP client)
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", aiSearchConfig.getModel());
        payload.put("messages", List.of(
                Map.of("role", "system", "content", "You are an expert HR assistant. Analyze job requirements and candidate profiles to provide match explanations."),
                Map.of("role", "user", "content", prompt)
        ));
        payload.put("temperature", aiSearchConfig.getTemperature());
        payload.put("max_tokens", aiSearchConfig.getMaxTokens());

        try {
            // Call Mistral API
            String url = aiSearchConfig.getBaseUrl();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + aiSearchConfig.getApiKey());

            org.springframework.http.HttpEntity<Map<String, Object>> entity =
                    new org.springframework.http.HttpEntity<>(payload, headers);

            String response = restTemplate.postForObject(url, entity, String.class);

            // Parse response
            return parseMistralResponse(response, request.getJobId(), request.getCandidateId());

        } catch (Exception e) {
            log.error("Mistral API call failed: {}", e.getMessage());
            throw new RuntimeException("AI service unavailable", e);
        }
    }

    private String buildPrompt(AICandidateMatchRequest request) {
        return String.format("""
            Bạn là chuyên gia HR giàu kinh nghiệm trong việc đánh giá ứng viên cho thị trường Việt Nam.
            Hãy phân tích công việc và hồ sơ ứng viên dưới đây để đưa ra đánh giá chi tiết bằng TIẾNG VIỆT.

            CÔNG VIỆC:
            - Vị trí: %s
            - Mô tả: %s
            - Kỹ năng yêu cầu: %s
            - Mức lương: %s - %s VND/tháng
            - Cấp bậc kinh nghiệm: %s
            - Loại công việc: %s

            ỨNG VIÊN:
            - Tên: %s
            - Chức danh: %s
            - Giới thiệu bản thân: %s
            - Kỹ năng nổi bật: %s
            - Số năm kinh nghiệm: %s
            - Mức lương theo giờ kỳ vọng: %s VND/giờ
            - Tổng số dự án đã hoàn thành: %s
            - Tổng số chứng chỉ: %s
            - Số nhiệm vụ đã hoàn thành (short-term): %s
            - Điểm đánh giá trung bình nhiệm vụ: %s/5
            - Kỹ năng thể hiện qua nhiệm vụ đã hoàn thành: %s
            - Nhiệm vụ tiêu biểu gần đây: %s

            Yêu cầu bắt buộc khi phân tích:
            - Luôn đối chiếu JD với kỹ năng rút ra từ nhiệm vụ đã hoàn thành.
            - Nếu ứng viên có lịch sử hoàn thành nhiệm vụ tốt, hãy nêu rõ bằng chứng cụ thể trong phần nhận định.
            - Nếu dữ liệu nhiệm vụ ít hoặc chưa đủ, phải ghi rõ mức độ thiếu dữ liệu.

            Hãy phân tích và trả về markdown đẹp mắt, giàu thông tin, với cấu trúc rõ ràng bằng TIẾNG VIỆT.
            **CHỈ trả về markdown text, không có JSON, không có code block, không có backtick.**

            Output format bắt buộc (dùng đúng emoji và cấu trúc này):

            ## 🧠 Đánh giá tổng quan

            [Viết 2-3 câu tổng kết về mức độ phù hợp tổng thể. Nêu rõ điểm mạnh nổi bật nhất, điểm cần lưu ý, và khuyến nghị sơ bộ cho recruiter. Dùng **text** để nhấn mạnh từ khóa quan trọng.]

            ---

            ## 🔧 Phân tích kỹ năng

            [Với MỖI kỹ năng trong danh sách yêu cầu của job, viết một section riêng theo format sau:]

            ### {index}. {Tên kỹ năng} ({isRequired ? 'Quan trọng' : 'Ưu tiên'})
            - **Mức độ phù hợp:** ⭐⭐⭐⭐☆ ({relevanceScore}/1.0)
            - **Trạng thái:** {status emoji} {status text}
            - **Bằng chứng:** {evidence từ hồ sơ ứng viên}
            - **Nhận định:** {2-3 câu phân tích chi tiết về kỹ năng này}

            [Status emoji mapping: relevanceScore >= 0.8 → ✅ Rất tốt, >= 0.6 → ⚠️ Khá, >= 0.4 → ❗ Cần xác minh, < 0.4 → ❌ Thiếu]

            ---

            ## 📊 So sánh điểm mạnh & điểm yếu

            ### ✅ Điểm mạnh của ứng viên
            [Liệt kê 2-4 điểm mạnh nổi bật, dùng bullet points]

            ### ⚠️ Điểm cần xem xét
            [Liệt kê 2-4 điểm yếu hoặc thiếu sót, dùng bullet points]

            ---

            ## 📌 Gợi ý cho recruiter

            ### Câu hỏi phỏng vấn gợi ý
            [Liệt kê 2-3 câu hỏi cụ thể nên hỏi ứng viên]

            ### Hành động khuyến nghị
            [Liệt kê 1-2 hành động cụ thể recruiter nên làm (ví dụ: yêu cầu portfolio, test kỹ năng, v.v.)]

            ---

            ## ⚠️ Kết luận

            [Viết 2-3 câu kết luận tổng hợp: mức độ phù hợp chung, đề xuất hành động tiếp theo rõ ràng cho recruiter.]

            ---

            **Confidence: {confidenceScore}/1.0** | Phân tích bởi AI
            """,
                request.getJobTitle(),
                request.getJobDescription(),
                request.getRequiredSkills(),
                request.getMinBudget(),
                request.getMaxBudget(),
                request.getExperienceLevel(),
                request.getJobType(),
                request.getCandidateName(),
                request.getProfessionalTitle(),
                request.getBio(),
                request.getTopSkills(),
                request.getYearsOfExperience(),
                request.getHourlyRate(),
                request.getTotalProjects(),
                request.getTotalCertificates(),
                request.getCompletedMissionCount(),
                request.getAverageMissionRating() != null
                    ? String.format("%.2f", request.getAverageMissionRating())
                    : "N/A",
                request.getMissionSkills(),
                request.getRecentMissionHighlights()
        );
    }

    private AICandidateMatchResponse parseMistralResponse(String jsonResponse, Long jobId, Long candidateId) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode choices = root.path("choices");

            if (!choices.isArray() || choices.isEmpty()) {
                return generateFallbackMatch(jobId, candidateId, System.currentTimeMillis());
            }

            String content = choices.get(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                return generateFallbackMatch(jobId, candidateId, System.currentTimeMillis());
            }

            // AI returns rich markdown text now — extract structured fields
            return buildResponseFromMarkdown(content, jobId, candidateId);

        } catch (Exception e) {
            log.error("Error parsing Mistral response: {}", e.getMessage());
            return generateFallbackMatch(jobId, candidateId, System.currentTimeMillis());
        }
    }

    private AICandidateMatchResponse buildResponseFromMarkdown(String markdown, Long jobId, Long candidateId) {
        // Extract fit_summary from the "Đánh giá tổng quan" section (first 2-3 sentences after heading)
        String fitSummary = extractFitSummary(markdown);

        // Extract skill signals from "Phân tích kỹ năng" section
        List<AICandidateMatchResponse.SkillSignal> skillSignals = extractSkillSignals(markdown);

        // Prefer explicit AI confidence if present, then fallback to parsed signal averages.
        Double explicitConfidence = extractConfidenceFromMarkdown(markdown);
        double confidence;
        if (explicitConfidence != null) {
            confidence = clamp01(explicitConfidence);
        } else if (!skillSignals.isEmpty()) {
            confidence = estimateConfidence(skillSignals);
        } else {
            confidence = estimateConfidenceFromMarkdownScores(markdown).orElse(0.5);
        }

        AICandidateMatchResponse.MatchQuality quality = determineMatchQuality(confidence);

        return AICandidateMatchResponse.builder()
                .jobId(jobId)
                .candidateId(candidateId)
                .fitSummary(fitSummary)
                .skillSignals(skillSignals)
                .reasoning(markdown) // Full markdown content for rich rendering
                .confidenceScore(confidence)
                .matchQuality(quality)
                .modelUsed(aiSearchConfig.getModel())
                .isFallback(false)
                .build();
    }

    private String extractFitSummary(String markdown) {
        // Extract text from "## 🧠 Đánh giá tổng quan" section
        // Find the section, skip the heading, collect text until the next --- or ##
        int startIdx = markdown.indexOf("## 🧠");
        if (startIdx == -1) {
            startIdx = markdown.indexOf("## Đánh giá tổng quan");
        }
        if (startIdx == -1) {
            // Fallback: return first non-heading paragraph
            String[] lines = markdown.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("-") && !trimmed.startsWith("*")) {
                    return trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed;
                }
            }
            return "Phân tích chi tiết về mức độ phù hợp của ứng viên với công việc.";
        }

        int contentStart = markdown.indexOf("\n", startIdx);
        if (contentStart == -1) return "";

        int sectionEnd = markdown.indexOf("\n---", contentStart);
        if (sectionEnd == -1) sectionEnd = markdown.indexOf("\n## ", contentStart);
        if (sectionEnd == -1) sectionEnd = markdown.length();

        String section = markdown.substring(contentStart, sectionEnd).trim();
        // Remove markdown heading markers and clean up
        section = section.replaceAll("#+\\s*", "").replaceAll("\\*+", "").trim();

        // Get first 2 sentences or up to 250 chars
        String[] sentences = section.split("[.!?]+\\s*");
        StringBuilder summary = new StringBuilder();
        for (String s : sentences) {
            if (summary.length() + s.length() > 250) break;
            if (!s.trim().isEmpty()) {
                if (summary.length() > 0) summary.append(". ");
                summary.append(s.trim());
            }
        }
        String result = summary.toString();
        return result.isEmpty() ? section.substring(0, Math.min(section.length(), 200)) : result;
    }

    private List<AICandidateMatchResponse.SkillSignal> extractSkillSignals(String markdown) {
        List<AICandidateMatchResponse.SkillSignal> signals = new ArrayList<>();

        // Pattern: ### {index}. {SkillName} (Quan trọng/Ưu tiên)
        // Then: - **Mức độ phù hợp:** ... (X/1.0)
        java.util.regex.Pattern skillPattern = java.util.regex.Pattern.compile(
            "###\\s*(?:\\d+\\.)?\\s*([^\\n(]+?)(?:\\s*\\((Quan trọng|Ưu tiên)\\))?\\s*$",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.MULTILINE
        );
        java.util.regex.Pattern genericScorePattern = java.util.regex.Pattern.compile(
            "([0-9](?:\\.[0-9]+)?)/1\\.0",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Pattern scorePattern = java.util.regex.Pattern.compile(
            "Mức độ phù hợp[^:]*:\\s*[⭐✅⚠️❗❌\\s]*\\(?([0-9.]+)/1\\.0\\)?",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Pattern evidencePattern = java.util.regex.Pattern.compile(
            "\\*\\*Bằng chứng:\\*\\*\\s*([^\\n-]+)",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );

        java.util.regex.Matcher skillMatcher = skillPattern.matcher(markdown);
        while (skillMatcher.find()) {
            String skillName = skillMatcher.group(1).trim();
            String requiredGroup = skillMatcher.group(2);
            boolean isRequired = requiredGroup == null || "Quan trọng".equalsIgnoreCase(requiredGroup.trim());

            // Look for score in next 200 chars after skill name
            int searchStart = skillMatcher.end();
            int searchEnd = Math.min(searchStart + 600, markdown.length());
            String nearby = markdown.substring(searchStart, searchEnd);

            double relevanceScore = 0.5; // default
            java.util.regex.Matcher scoreMatcher = scorePattern.matcher(nearby);
            if (scoreMatcher.find()) {
                try {
                    relevanceScore = Double.parseDouble(scoreMatcher.group(1).trim());
                } catch (NumberFormatException ignored) {}
            } else {
                java.util.regex.Matcher genericScoreMatcher = genericScorePattern.matcher(nearby);
                if (genericScoreMatcher.find()) {
                    try {
                        relevanceScore = Double.parseDouble(genericScoreMatcher.group(1).trim());
                    } catch (NumberFormatException ignored) {}
                }
            }

            String evidence = "";
            java.util.regex.Matcher evidenceMatcher = evidencePattern.matcher(nearby);
            if (evidenceMatcher.find()) {
                evidence = evidenceMatcher.group(1).trim();
            } else {
                // Try to find evidence in bullet list
                java.util.regex.Pattern altEvidence = java.util.regex.Pattern.compile(
                    "Bằng chứng[^:]*:\\s*([^\\n]+)", java.util.regex.Pattern.CASE_INSENSITIVE
                );
                java.util.regex.Matcher alt = altEvidence.matcher(nearby);
                if (alt.find()) {
                    evidence = alt.group(1).trim();
                }
            }

            if (!skillName.isEmpty()) {
                signals.add(AICandidateMatchResponse.SkillSignal.builder()
                        .skill(skillName)
                        .evidence(evidence.isEmpty() ? "Không có bằng chứng cụ thể" : evidence)
                        .isRequired(isRequired)
                        .relevanceScore(Math.max(0, Math.min(1, relevanceScore)))
                        .build());
            }
        }

        return signals;
    }

    private Double extractConfidenceFromMarkdown(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return null;
        }

        java.util.regex.Pattern confidencePattern = java.util.regex.Pattern.compile(
                "(?i)confidence\\s*[:=]?\\s*([0-9](?:\\.[0-9]+)?)\\s*/\\s*1\\.0"
        );
        java.util.regex.Matcher matcher = confidencePattern.matcher(markdown);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Optional<Double> estimateConfidenceFromMarkdownScores(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return Optional.empty();
        }

        java.util.regex.Pattern scorePattern = java.util.regex.Pattern.compile("([0-9](?:\\.[0-9]+)?)/1\\.0");
        java.util.regex.Matcher matcher = scorePattern.matcher(markdown);

        double total = 0;
        int count = 0;
        while (matcher.find()) {
            try {
                double score = Double.parseDouble(matcher.group(1));
                if (score >= 0 && score <= 1) {
                    total += score;
                    count++;
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed values and continue.
            }
        }

        if (count == 0) {
            return Optional.empty();
        }
        return Optional.of(clamp01(total / count));
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double estimateConfidence(List<AICandidateMatchResponse.SkillSignal> signals) {
        if (signals.isEmpty()) return 0.5;
        double total = signals.stream()
                .mapToDouble(s -> s.getRelevanceScore() != null ? s.getRelevanceScore() : 0)
                .sum();
        return Math.min(1.0, total / signals.size());
    }

    /**
     * Generate fallback match when AI is unavailable
     */
    private AICandidateMatchResponse generateFallbackMatch(Long jobId, Long candidateId, long startTime) {
        log.debug("Using fallback rule-based matching for job {} candidate {}", jobId, candidateId);

        try {
            JobPosting job = jobPostingRepository.findById(jobId).orElse(null);
            PortfolioExtendedProfile profile = portfolioRepository.findById(candidateId).orElse(null);

            if (job == null || profile == null) {
                return createBasicResponse(jobId, candidateId);
            }

            // Calculate rule-based scores
            double skillScore = calculateSkillMatch(job, profile);
            double expScore = calculateExperienceMatch(job, profile);
            double budgetScore = calculateBudgetMatch(job, profile);
                double missionScore = calculateMissionScore(buildMissionInsights(candidateId));

                double totalScore = (skillScore * 0.45) + (expScore * 0.25) + (budgetScore * 0.15) + (missionScore * 0.15);

                String summary = String.format("Ứng viên có kinh nghiệm %s, kỹ năng %s và lịch sử nhiệm vụ %s so với yêu cầu công việc. Mức lương: %s.",
                    getExperienceLabelVi(expScore),
                    getSkillLabelVi(skillScore),
                    getMissionLabelVi(missionScore),
                    getBudgetLabelVi(budgetScore));

            return AICandidateMatchResponse.builder()
                    .jobId(jobId)
                    .candidateId(candidateId)
                    .fitSummary(summary)
                    .skillSignals(extractMatchingSkills(job, profile))
                    .reasoning("Đánh giá dựa trên quy tắc - Kỹ năng: " + Math.round(skillScore * 100) + "% khớp, "
                            + "Kinh nghiệm: " + Math.round(expScore * 100) + "% phù hợp, "
                            + "Mức lương: " + Math.round(budgetScore * 100) + "% hợp lý, "
                            + "Nhiệm vụ đã hoàn thành: " + Math.round(missionScore * 100) + "%")
                    .confidenceScore(totalScore)
                    .matchQuality(determineMatchQuality(totalScore))
                    .modelUsed("rule-based-fallback")
                    .isFallback(true)
                        .processingTimeMs(calculateProcessingTime(startTime))
                    .build();

        } catch (Exception e) {
            log.error("Error in fallback matching: {}", e.getMessage());
            return createBasicResponse(jobId, candidateId);
        }
    }

    private AICandidateMatchResponse createBasicResponse(Long jobId, Long candidateId) {
        return AICandidateMatchResponse.builder()
                .jobId(jobId)
                .candidateId(candidateId)
                .fitSummary("Unable to generate detailed match analysis.")
                .confidenceScore(0.5)
                .matchQuality(AICandidateMatchResponse.MatchQuality.FAIR)
                .isFallback(true)
                .build();
    }

    // Rule-based matching helpers
    private double calculateSkillMatch(JobPosting job, PortfolioExtendedProfile profile) {
        if (profile.getTopSkills() == null || job.getRequiredSkills() == null) {
            return 0.0;
        }

        try {
            List<String> jobSkills = parseStringList(job.getRequiredSkills());
            List<String> profileSkills = parseStringList(profile.getTopSkills());

            if (jobSkills.isEmpty() || profileSkills.isEmpty()) {
                return 0.5;
            }

            // Normalize to lowercase
            jobSkills = jobSkills.stream().map(String::toLowerCase).collect(Collectors.toList());
            profileSkills = profileSkills.stream().map(String::toLowerCase).collect(Collectors.toList());

            long matchCount = profileSkills.stream()
                    .filter(jobSkills::contains)
                    .count();

            return Math.min(1.0, (double) matchCount / jobSkills.size());

        } catch (JsonProcessingException e) {
            return 0.5;
        }
    }

    private double calculateExperienceMatch(JobPosting job, PortfolioExtendedProfile profile) {
        if (profile.getYearsOfExperience() == null) {
            return 0.5;
        }

        // Parse experience level from job
        String expLevel = job.getExperienceLevel();
        int requiredYears = parseExperienceLevel(expLevel);

        int candidateYears = profile.getYearsOfExperience();

        if (candidateYears >= requiredYears) {
            return 1.0;
        } else if (candidateYears >= requiredYears * 0.7) {
            return 0.7;
        } else if (candidateYears >= requiredYears * 0.5) {
            return 0.5;
        }
        return 0.3;
    }

    private double calculateBudgetMatch(JobPosting job, PortfolioExtendedProfile profile) {
        if (profile.getHourlyRate() == null) {
            return 0.5;
        }

        double candidateRate = profile.getHourlyRate();
        double minBudget = job.getMinBudget().doubleValue();
        double maxBudget = job.getMaxBudget().doubleValue();

        // Assuming monthly budget, convert to hourly (approx 160 hours/month)
        double hourlyMin = minBudget / 160;
        double hourlyMax = maxBudget / 160;

        if (candidateRate >= hourlyMin && candidateRate <= hourlyMax) {
            return 1.0;
        } else if (candidateRate < hourlyMin) {
            return 1.0; // Under budget is good
        } else if (candidateRate <= hourlyMax * 1.2) {
            return 0.7; // Slightly over budget
        }
        return 0.3;
    }

    private int parseExperienceLevel(String level) {
        if (level == null) return 3; // Default 3 years

        level = level.toLowerCase();
        if (level.contains("intern") || level.contains("entry") || level.contains("junior")) {
            return 1;
        } else if (level.contains("senior") || level.contains("lead")) {
            return 5;
        } else if (level.contains("mid") || level.contains("middle")) {
            return 3;
        }
        return 3;
    }

    private String getExperienceLabelVi(double score) {
        if (score >= 0.8) return "vững";
        if (score >= 0.5) return "tương đối";
        return "hạn chế";
    }

    private String getSkillLabelVi(double score) {
        if (score >= 0.8) return "xuất sắc";
        if (score >= 0.5) return "tốt";
        return "cơ bản";
    }

    private String getBudgetLabelVi(double score) {
        if (score >= 0.8) return "phù hợp";
        if (score >= 0.5) return "chấp nhận được";
        return "chưa đạt";
    }

    private String getMissionLabelVi(double score) {
        if (score >= 0.8) return "rất tốt";
        if (score >= 0.6) return "khá";
        if (score >= 0.4) return "trung bình";
        return "hạn chế";
    }

    private double calculateMissionScore(CandidateMissionInsights missionInsights) {
        if (missionInsights == null || missionInsights.completedMissionCount() <= 0) {
            return 0.4;
        }

        double countScore = Math.min(1.0, missionInsights.completedMissionCount() / 5.0);
        double ratingScore = missionInsights.averageMissionRating() != null
                ? clamp01(missionInsights.averageMissionRating() / 5.0)
                : 0.6;

        int skillCount = 0;
        try {
            List<String> missionSkills = parseStringList(missionInsights.missionSkillsJson());
            skillCount = missionSkills != null ? missionSkills.size() : 0;
        } catch (Exception ignored) {
            // Keep skillCount at 0 when mission skills are unavailable.
        }
        double skillCoverageScore = Math.min(1.0, skillCount / 8.0);

        return clamp01((countScore * 0.4) + (ratingScore * 0.4) + (skillCoverageScore * 0.2));
    }

    private long calculateProcessingTime(long startTime) {
        if (startTime <= 0) {
            return 0L;
        }
        return Math.max(0L, System.currentTimeMillis() - startTime);
    }

    private List<AICandidateMatchResponse.SkillSignal> extractMatchingSkills(JobPosting job, PortfolioExtendedProfile profile) {
        List<AICandidateMatchResponse.SkillSignal> signals = new ArrayList<>();

        try {
            List<String> jobSkills = parseStringList(job.getRequiredSkills());
            List<String> profileSkills = parseStringList(profile.getTopSkills());

            jobSkills = jobSkills.stream().map(String::toLowerCase).collect(Collectors.toList());
            profileSkills = profileSkills.stream().map(String::toLowerCase).collect(Collectors.toList());

            for (String skill : profileSkills) {
                if (jobSkills.contains(skill)) {
                    signals.add(AICandidateMatchResponse.SkillSignal.builder()
                            .skill(skill)
                            .evidence("Found in profile top skills")
                            .isRequired(true)
                            .relevanceScore(1.0)
                            .build());
                }
            }

        } catch (JsonProcessingException e) {
            log.error("Error extracting matching skills: {}", e.getMessage());
        }

        return signals;
    }

    private AICandidateMatchResponse.MatchQuality determineMatchQuality(double score) {
        if (score >= 0.8) return AICandidateMatchResponse.MatchQuality.EXCELLENT;
        if (score >= 0.6) return AICandidateMatchResponse.MatchQuality.GOOD;
        if (score >= 0.4) return AICandidateMatchResponse.MatchQuality.FAIR;
        return AICandidateMatchResponse.MatchQuality.POOR;
    }

    private int estimateTokens(String text) {
        // Rough estimate: 1 token ≈ 4 characters
        return text != null ? text.length() / 4 : 0;
    }

    /**
     * Reset daily token counter (called by scheduler)
     */
    public void resetDailyCounters() {
        tokensThisDay.set(0);
        dayWindowStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        log.info("Daily AI search token counter reset");
    }

    /**
     * Reset minute request counter (called by scheduler)
     */
    public void resetMinuteCounters() {
        requestsThisMinute.set(0);
        minuteWindowStart = LocalDateTime.now();
    }
}
