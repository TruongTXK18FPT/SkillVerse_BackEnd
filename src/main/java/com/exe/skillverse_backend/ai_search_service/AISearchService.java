package com.exe.skillverse_backend.ai_search_service;

import com.exe.skillverse_backend.ai_search_service.dto.AICandidateMatchResponse;

import java.util.List;

/**
 * Service interface for AI-powered candidate matching
 */
public interface AISearchService {

    /**
     * Generate AI-enhanced match explanation for a job-candidate pair
     * Uses Mistral API to analyze JD and candidate profile
     *
     * @param jobId The job posting ID
     * @param candidateId The candidate user ID
     * @return AI match response with fit summary, skill signals, and reasoning
     */
    AICandidateMatchResponse generateMatchExplanation(Long jobId, Long candidateId);

    /**
     * Generate match explanations for multiple candidates for a job
     *
     * @param jobId The job posting ID
     * @param candidateIds List of candidate IDs to analyze
     * @return List of AI match responses
     */
    List<AICandidateMatchResponse> generateBulkMatchExplanations(Long jobId, List<Long> candidateIds);

    /**
     * Check if AI search is available
     */
    boolean isEnabled();

    /**
     * Generate AI-enhanced match explanation for a short-term job (gig/freelance)-candidate pair
     * Uses Mistral API to analyze gig description and candidate profile
     *
     * @param shortTermJobId The short-term job ID
     * @param candidateId The candidate user ID
     * @return AI match response with fit summary, skill signals, and reasoning
     */
    AICandidateMatchResponse generateShortTermJobMatchExplanation(Long shortTermJobId, Long candidateId);

    /**
     * Check rate limit status
     */
    boolean canMakeRequest();
}
