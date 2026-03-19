package com.exe.skillverse_backend.ai_search_service.impl;

import com.exe.skillverse_backend.ai_search_service.AISearchService;
import com.exe.skillverse_backend.ai_search_service.config.AISearchConfig;
import com.exe.skillverse_backend.ai_search_service.dto.AICandidateMatchRequest;
import com.exe.skillverse_backend.ai_search_service.dto.AICandidateMatchResponse;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.repository.JobPostingRepository;
import com.exe.skillverse_backend.portfolio_service.entity.PortfolioExtendedProfile;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioExtendedProfileRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
    private final PortfolioExtendedProfileRepository portfolioRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public AISearchServiceImpl(
            AISearchConfig aiSearchConfig,
            JobPostingRepository jobPostingRepository,
            PortfolioExtendedProfileRepository portfolioRepository,
            ObjectMapper objectMapper,
            @Qualifier("aiSearchRestTemplate") RestTemplate restTemplate) {
        this.aiSearchConfig = aiSearchConfig;
        this.jobPostingRepository = jobPostingRepository;
        this.portfolioRepository = portfolioRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    // Rate limiting: track requests per minute and tokens per day
    private final AtomicInteger requestsThisMinute = new AtomicInteger(0);
    private final AtomicInteger tokensThisDay = new AtomicInteger(0);
    private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();

    // Cache for match results (jobId + candidateId -> result)
    private final Map<String, AICandidateMatchResponse> matchCache = new ConcurrentHashMap<>();

    @Value("${ai-search.api-key:}")
    private String apiKey;

    @Override
    public AICandidateMatchResponse generateMatchExplanation(Long jobId, Long candidateId) {
        long startTime = System.currentTimeMillis();

        // Check cache first
        String cacheKey = jobId + "_" + candidateId;
        if (aiSearchConfig.isCacheEnabled() && matchCache.containsKey(cacheKey)) {
            log.debug("Using cached match result for job {} candidate {}", jobId, candidateId);
            return matchCache.get(cacheKey);
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
            requestsThisMinute.incrementAndGet();
            tokensThisDay.addAndGet(estimateTokens(response.getFitSummary() + response.getReasoning()));

            // Cache result
            if (aiSearchConfig.isCacheEnabled()) {
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
    public boolean isEnabled() {
        return aiSearchConfig.isEnabled() && apiKey != null && !apiKey.isEmpty();
    }

    @Override
    public boolean canMakeRequest() {
        if (!isEnabled()) return false;

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

    // ==================== Private Methods ====================

    private AICandidateMatchRequest buildMatchRequest(JobPosting job, User candidate, PortfolioExtendedProfile profile) {
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
            headers.set("Authorization", "Bearer " + apiKey);

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
            Analyze the following job and candidate to determine fit:

            JOB:
            - Title: %s
            - Description: %s
            - Required Skills: %s
            - Budget: %s - %s
            - Experience Level: %s
            - Job Type: %s

            CANDIDATE:
            - Name: %s
            - Professional Title: %s
            - Bio: %s
            - Skills: %s
            - Years of Experience: %s
            - Hourly Rate: %s
            - Total Projects: %s
            - Total Certificates: %s

            Provide a JSON response with:
            1. fit_summary: 1-2 sentence explanation of why this candidate fits (or doesn't fit) the job
            2. skill_signals: Array of skills found in candidate profile that match job requirements, with evidence and relevance score (0-1)
            3. reasoning: Brief reasoning for the match (2-3 sentences)
            4. confidence_score: Overall confidence (0-1)

            Return ONLY valid JSON, no other text.
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
                request.getTotalCertificates()
        );
    }

    private AICandidateMatchResponse parseMistralResponse(String jsonResponse, Long jobId, Long candidateId) {
        try {
            // Simplified JSON parsing - actual implementation would properly parse the response
            // Mistral response format: { choices: [{ message: { content: "..." } }] }

            Map<String, Object> response = objectMapper.readValue(jsonResponse, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");

            if (choices == null || choices.isEmpty()) {
                return generateFallbackMatch(jobId, candidateId, 0L);
            }

            String content = (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");

            // Try to parse as JSON, fallback if not valid
            try {
                Map<String, Object> parsed = objectMapper.readValue(content, Map.class);
                return buildResponseFromParsed(parsed, jobId, candidateId);
            } catch (JsonProcessingException e) {
                // Not valid JSON, create response from text
                return createResponseFromText(content, jobId, candidateId);
            }

        } catch (Exception e) {
            log.error("Error parsing Mistral response: {}", e.getMessage());
            return generateFallbackMatch(jobId, candidateId, 0L);
        }
    }

    private AICandidateMatchResponse buildResponseFromParsed(Map<String, Object> parsed, Long jobId, Long candidateId) {
        // Extract skill signals
        List<AICandidateMatchResponse.SkillSignal> skillSignals = new ArrayList<>();
        if (parsed.get("skill_signals") instanceof List) {
            List<Map<String, Object>> signals = (List<Map<String, Object>>) parsed.get("skill_signals");
            for (Map<String, Object> signal : signals) {
                skillSignals.add(AICandidateMatchResponse.SkillSignal.builder()
                        .skill((String) signal.get("skill"))
                        .evidence((String) signal.get("evidence"))
                        .isRequired((Boolean) signal.get("isRequired"))
                        .relevanceScore(((Number) signal.getOrDefault("relevanceScore", 0.0)).doubleValue())
                        .build());
            }
        }

        // Determine match quality
        double confidence = ((Number) parsed.getOrDefault("confidence_score", 0.5)).doubleValue();
        AICandidateMatchResponse.MatchQuality quality = determineMatchQuality(confidence);

        return AICandidateMatchResponse.builder()
                .jobId(jobId)
                .candidateId(candidateId)
                .fitSummary((String) parsed.get("fit_summary"))
                .skillSignals(skillSignals)
                .reasoning((String) parsed.get("reasoning"))
                .confidenceScore(confidence)
                .matchQuality(quality)
                .modelUsed(aiSearchConfig.getModel())
                .isFallback(false)
                .build();
    }

    private AICandidateMatchResponse createResponseFromText(String text, Long jobId, Long candidateId) {
        // Fallback when AI returns non-JSON text
        return AICandidateMatchResponse.builder()
                .jobId(jobId)
                .candidateId(candidateId)
                .fitSummary("AI analysis available. Please review candidate profile for details.")
                .reasoning(text.substring(0, Math.min(text.length(), 500)))
                .confidenceScore(0.5)
                .matchQuality(AICandidateMatchResponse.MatchQuality.FAIR)
                .modelUsed(aiSearchConfig.getModel())
                .isFallback(false)
                .build();
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

            double totalScore = (skillScore * 0.5) + (expScore * 0.3) + (budgetScore * 0.2);

            String summary = String.format("Candidate has %s experience and %s skills matching job requirements. Budget match: %s.",
                    getExperienceLabel(expScore),
                    getSkillLabel(skillScore),
                    getBudgetLabel(budgetScore));

            return AICandidateMatchResponse.builder()
                    .jobId(jobId)
                    .candidateId(candidateId)
                    .fitSummary(summary)
                    .skillSignals(extractMatchingSkills(job, profile))
                    .reasoning("Rule-based matching: Skills (" + Math.round(skillScore * 100) + "%), "
                            + "Experience (" + Math.round(expScore * 100) + "%), "
                            + "Budget (" + Math.round(budgetScore * 100) + "%)")
                    .confidenceScore(totalScore)
                    .matchQuality(determineMatchQuality(totalScore))
                    .modelUsed("rule-based-fallback")
                    .isFallback(true)
                    .processingTimeMs(System.currentTimeMillis() - startTime)
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
            List<String> jobSkills = objectMapper.readValue(job.getRequiredSkills(), List.class);
            List<String> profileSkills = objectMapper.readValue(profile.getTopSkills(), List.class);

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

    private String getExperienceLabel(double score) {
        if (score >= 0.8) return "strong";
        if (score >= 0.5) return "moderate";
        return "limited";
    }

    private String getSkillLabel(double score) {
        if (score >= 0.8) return "excellent";
        if (score >= 0.5) return "good";
        return "basic";
    }

    private String getBudgetLabel(double score) {
        if (score >= 0.8) return "excellent";
        if (score >= 0.5) return "acceptable";
        return "below expectations";
    }

    private List<AICandidateMatchResponse.SkillSignal> extractMatchingSkills(JobPosting job, PortfolioExtendedProfile profile) {
        List<AICandidateMatchResponse.SkillSignal> signals = new ArrayList<>();

        try {
            List<String> jobSkills = objectMapper.readValue(job.getRequiredSkills(), List.class);
            List<String> profileSkills = objectMapper.readValue(profile.getTopSkills(), List.class);

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
        log.info("Daily AI search token counter reset");
    }

    /**
     * Reset minute request counter (called by scheduler)
     */
    public void resetMinuteCounters() {
        requestsThisMinute.set(0);
    }
}
