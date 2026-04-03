package com.exe.skillverse_backend.ai_search_service.impl;

import com.exe.skillverse_backend.ai_search_service.config.AISearchConfig;
import com.exe.skillverse_backend.ai_search_service.dto.AICandidateMatchResponse;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.ShortTermJob;
import com.exe.skillverse_backend.business_service.repository.JobPostingRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobRepository;
import com.exe.skillverse_backend.portfolio_service.entity.PortfolioExtendedProfile;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioExtendedProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AISearchServiceImplTest {

    @Mock
    private JobPostingRepository jobPostingRepository;

    @Mock
    private ShortTermJobRepository shortTermJobRepository;

    @Mock
    private PortfolioExtendedProfileRepository portfolioRepository;

    @Mock
    private RestTemplate restTemplate;

    private AISearchConfig config;
    private AISearchServiceImpl service;

    @BeforeEach
    void setUp() {
        config = new AISearchConfig();
        config.setEnabled(true);
        config.setApiKey("test-api-key");
        config.setCacheEnabled(true);
        config.setBaseUrl("https://mistral.test/api");
        config.setMaxRequestsPerMinute(10);
        config.setMaxTokensPerDay(100000);
        service = new AISearchServiceImpl(
                config,
                jobPostingRepository,
                shortTermJobRepository,
                portfolioRepository,
                new ObjectMapper(),
                restTemplate);
    }

    @Test
    @DisplayName("generateMatchExplanation should use rule-based fallback when AI is disabled")
    void generateMatchExplanation_ShouldUseFallbackWhenAiIsDisabled() {
        config.setEnabled(false);
        JobPosting job = fullTimeJob(10L);
        PortfolioExtendedProfile profile = profile(20L, 5, 80000d, "[\"Java\",\"React\"]");

        when(jobPostingRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(portfolioRepository.findById(profile.getUserId())).thenReturn(Optional.of(profile));

        AICandidateMatchResponse response = service.generateMatchExplanation(job.getId(), profile.getUserId());

        assertTrue(response.getIsFallback());
        assertEquals("rule-based-fallback", response.getModelUsed());
        assertEquals(AICandidateMatchResponse.MatchQuality.GOOD, response.getMatchQuality());
        assertEquals(1, response.getSkillSignals().size());
    }

    @Test
    @DisplayName("generateMatchExplanation should cache API responses and skip duplicate outbound calls")
    void generateMatchExplanation_ShouldCacheApiResponses() {
        JobPosting job = fullTimeJob(10L);
        PortfolioExtendedProfile profile = profile(20L, 5, 80000d, "[\"Java\",\"React\"]");

        when(jobPostingRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(portfolioRepository.findById(profile.getUserId())).thenReturn(Optional.of(profile));
        when(restTemplate.postForObject(eq(config.getBaseUrl()), any(), eq(String.class)))
                .thenReturn("{\"choices\":[{\"message\":{\"content\":\"\"}}]}");

        AICandidateMatchResponse first = service.generateMatchExplanation(job.getId(), profile.getUserId());
        AICandidateMatchResponse second = service.generateMatchExplanation(job.getId(), profile.getUserId());

        assertTrue(first.getIsFallback());
        assertEquals(first, second);
        verify(restTemplate, times(1)).postForObject(eq(config.getBaseUrl()), any(), eq(String.class));
    }

    @Test
    @DisplayName("generateMatchExplanation should fall back gracefully when the AI call fails")
    void generateMatchExplanation_ShouldFallBackGracefullyWhenApiFails() {
        JobPosting job = fullTimeJob(10L);
        PortfolioExtendedProfile profile = profile(20L, 5, 80000d, "[\"Java\",\"React\"]");

        when(jobPostingRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(portfolioRepository.findById(profile.getUserId())).thenReturn(Optional.of(profile));
        when(restTemplate.postForObject(eq(config.getBaseUrl()), any(), eq(String.class)))
                .thenThrow(new RuntimeException("Mistral down"));

        AICandidateMatchResponse response = service.generateMatchExplanation(job.getId(), profile.getUserId());

        assertTrue(response.getIsFallback());
        assertEquals("rule-based-fallback", response.getModelUsed());
        assertNotNull(response.getFitSummary());
    }

    @Test
    @DisplayName("canMakeRequest should reject calls after the per-minute limit is reached")
    void canMakeRequest_ShouldRejectCallsAfterPerMinuteLimit() {
        config.setMaxRequestsPerMinute(1);
        AtomicInteger requestCounter = (AtomicInteger) ReflectionTestUtils.getField(service, "requestsThisMinute");
        requestCounter.set(1);

        assertFalse(service.canMakeRequest());
    }

    @Test
    @DisplayName("canMakeRequest should reject calls after the daily token budget is exhausted")
    void canMakeRequest_ShouldRejectCallsAfterDailyTokenBudgetIsExhausted() {
        config.setMaxTokensPerDay(50);
        AtomicInteger tokenCounter = (AtomicInteger) ReflectionTestUtils.getField(service, "tokensThisDay");
        tokenCounter.set(50);

        assertFalse(service.canMakeRequest());
    }

    @Test
    @DisplayName("generateShortTermJobMatchExplanation should return the basic fallback when profile data is missing")
    void generateShortTermJobMatchExplanation_ShouldReturnBasicFallbackWhenProfileMissing() {
        config.setEnabled(false);
        ShortTermJob shortTermJob = shortTermJob(30L);
        when(shortTermJobRepository.findById(shortTermJob.getId())).thenReturn(Optional.of(shortTermJob));
        when(portfolioRepository.findById(99L)).thenReturn(Optional.empty());

        AICandidateMatchResponse response = service.generateShortTermJobMatchExplanation(shortTermJob.getId(), 99L);

        assertTrue(response.getIsFallback());
        assertEquals(AICandidateMatchResponse.MatchQuality.FAIR, response.getMatchQuality());
        assertTrue(response.getFitSummary().contains("gig"));
    }

    @Test
    @DisplayName("reset counter helpers should zero out internal rate-limit state")
    void resetCounterHelpers_ShouldZeroOutInternalRateLimitState() {
        AtomicInteger requestCounter = (AtomicInteger) ReflectionTestUtils.getField(service, "requestsThisMinute");
        AtomicInteger tokenCounter = (AtomicInteger) ReflectionTestUtils.getField(service, "tokensThisDay");
        requestCounter.set(4);
        tokenCounter.set(1234);

        service.resetMinuteCounters();
        service.resetDailyCounters();

        assertEquals(0, requestCounter.get());
        assertEquals(0, tokenCounter.get());
    }

    private JobPosting fullTimeJob(Long id) {
        return JobPosting.builder()
                .id(id)
                .title("Senior Java Developer")
                .description("Build backend services with Spring Boot")
                .requiredSkills("[\"Java\",\"Spring\"]")
                .minBudget(new BigDecimal("10000000"))
                .maxBudget(new BigDecimal("20000000"))
                .deadline(LocalDate.now().plusDays(30))
                .experienceLevel("Senior")
                .jobType("FULL_TIME")
                .build();
    }

    private ShortTermJob shortTermJob(Long id) {
        return ShortTermJob.builder()
                .id(id)
                .title("Build landing page")
                .description("Need a responsive landing page")
                .requiredSkills("[\"HTML\",\"CSS\"]")
                .budget(new BigDecimal("3000000"))
                .deadline(LocalDateTime.now().plusDays(5))
                .estimatedDuration("2 days")
                .build();
    }

    private PortfolioExtendedProfile profile(Long userId, Integer yearsOfExperience, Double hourlyRate, String topSkills) {
        User user = User.builder()
                .id(userId)
                .email("candidate@skillverse.vn")
                .build();
        return PortfolioExtendedProfile.builder()
                .userId(userId)
                .user(user)
                .fullName("Candidate " + userId)
                .professionalTitle("Backend Developer")
                .yearsOfExperience(yearsOfExperience)
                .hourlyRate(hourlyRate)
                .topSkills(topSkills)
                .totalProjects(8)
                .totalCertificates(3)
                .build();
    }
}
