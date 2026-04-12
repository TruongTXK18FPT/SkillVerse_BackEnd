package com.exe.skillverse_backend.assignment_ai_service.service;

import com.exe.skillverse_backend.assignment_ai_service.dto.AiGradingResultDTO;

/**
 * Service for AI-based assignment grading.
 */
public interface AssignmentAiGradingService {

    /**
     * Generate AI grade for a submission.
     *
     * @param submissionId the submission ID to grade
     * @param mentorId     the mentor triggering the grade (for audit logging)
     * @return the AI grading result with criteria scores, total, feedback and confidence
     * @throws IllegalStateException    if AI grading is disabled on the assignment
     * @throws IllegalArgumentException if submission exceeds AI grade attempt cap (3)
     * @throws RuntimeException        if AI call fails after 1 retry
     */
    AiGradingResultDTO generateAiGrade(Long submissionId, Long mentorId);

    /**
     * Get AI grade result for a submission (already generated).
     *
     * @param submissionId the submission ID
     * @return the AI grading result
     * @throws IllegalStateException if no AI grade exists for this submission
     */
    AiGradingResultDTO getAiGradeResult(Long submissionId);

    /**
     * Toggle Trust AI setting for an assignment.
     *
     * @param assignmentId the assignment ID
     * @param enabled      true to auto-confirm high-confidence AI grades
     */
    void toggleTrustAi(Long assignmentId, boolean enabled);

    /**
     * Student requests mentor to review an AI-graded submission (dispute).
     *
     * @param submissionId the submission ID
     * @param studentId    the student's user ID
     * @param reason       optional reason text
     */
    void requestMentorReview(Long submissionId, Long studentId, String reason);
}
