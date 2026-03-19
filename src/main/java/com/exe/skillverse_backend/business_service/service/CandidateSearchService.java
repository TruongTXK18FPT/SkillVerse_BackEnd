package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.business_service.dto.request.CandidateSearchRequest;
import com.exe.skillverse_backend.business_service.dto.response.RecruitmentSessionResponse;
import com.exe.skillverse_backend.portfolio_service.dto.CandidateSummaryDTO;
import org.springframework.data.domain.Page;

/**
 * Service interface for candidate search by recruiters
 */
public interface CandidateSearchService {

    /**
     * Search candidates with filters, sorting, and pagination
     *
     * @param recruiterId The recruiter performing the search
     * @param request Search request with filters and options
     * @return Page of candidate summaries with match scores
     */
    Page<CandidateSummaryDTO> searchCandidates(Long recruiterId, CandidateSearchRequest request);

    /**
     * Get AI match explanation for a specific job-candidate pair
     *
     * @param recruiterId The recruiter requesting the match
     * @param jobId The job posting ID
     * @param candidateId The candidate user ID
     * @return AI match response with explanation
     */
    Object getCandidateMatchExplanation(Long recruiterId, Long jobId, Long candidateId);

    /**
     * Get candidates matching a specific job
     *
     * @param recruiterId The recruiter
     * @param jobId The job posting ID
     * @param page Page number
     * @param size Page size
     * @return Page of matching candidates
     */
    Page<CandidateSummaryDTO> getMatchingCandidatesForJob(Long recruiterId, Long jobId, int page, int size);

    /**
     * Shortlist a candidate
     *
     * @param recruiterId The recruiter
     * @param candidateId The candidate to shortlist
     * @param jobId Optional job ID (for job-specific shortlist)
     * @param notes Optional notes
     */
    void shortlistCandidate(Long recruiterId, Long candidateId, Long jobId, String notes);

    /**
     * Remove candidate from shortlist
     *
     * @param recruiterId The recruiter
     * @param candidateId The candidate to remove
     * @param jobId Optional job ID
     */
    void removeFromShortlist(Long recruiterId, Long candidateId, Long jobId);

    /**
     * Get recruiter's shortlists
     *
     * @param recruiterId The recruiter
     * @param status Optional filter by status
     * @return List of shortlisted candidates
     */
    Page<CandidateSummaryDTO> getShortlistedCandidates(Long recruiterId, String status, int page, int size);

    /**
     * Connect candidate to a job (create recruitment session and optionally send invite)
     * This is the key integration point between candidate search and recruitment chat
     *
     * @param recruiterId The recruiter
     * @param candidateId The candidate to connect
     * @param jobId The job to connect to (optional)
     * @return The created recruitment session
     */
    RecruitmentSessionResponse connectCandidateToJob(Long recruiterId, Long candidateId, Long jobId);

    /**
     * Start chat with a candidate (creates recruitment session if not exists)
     *
     * @param recruiterId The recruiter
     * @param candidateId The candidate to chat with
     * @param jobId Optional job ID
     * @return The recruitment session
     */
    RecruitmentSessionResponse startChatWithCandidate(Long recruiterId, Long candidateId, Long jobId);
}
