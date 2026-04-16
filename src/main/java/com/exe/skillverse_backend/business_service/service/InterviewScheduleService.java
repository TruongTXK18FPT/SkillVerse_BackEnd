package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.business_service.dto.request.CreateInterviewRequest;
import com.exe.skillverse_backend.business_service.dto.response.InterviewScheduleResponse;

import java.util.List;

public interface InterviewScheduleService {

    /**
     * Schedule an interview for an accepted application.
     * Validates: application is ACCEPTED, job is REMOTE.
     * Generates meeting link for GOOGLE_MEET type.
     * Updates application status to INTERVIEW_SCHEDULED.
     * Sends email to candidate.
     */
    InterviewScheduleResponse scheduleInterview(Long userId, CreateInterviewRequest request);

    /**
     * Get interview schedule by application ID.
     */
    InterviewScheduleResponse getInterviewByApplicationId(Long applicationId);

    /**
     * Get all interview schedules for a job posting.
     */
    List<InterviewScheduleResponse> getInterviewsByJobPostingId(Long jobPostingId);

    /**
     * Candidate confirms interview participation.
     */
    InterviewScheduleResponse confirmInterview(Long userId, Long interviewId);

    /**
     * Candidate declines interview participation.
     * Candidate is permanently rejected from the application.
     */
    InterviewScheduleResponse declineInterview(Long userId, Long interviewId, String reason);

    /**
     * Mark interview as completed.
     * Updates interview status to COMPLETED and application status to INTERVIEWED.
     */
    InterviewScheduleResponse completeInterview(Long userId, Long interviewId, String notes);

    /**
     * Cancel an interview.
     * Updates interview status to CANCELLED.
     */
    InterviewScheduleResponse cancelInterview(Long userId, Long interviewId);

    /**
     * Get all interview schedules for the current candidate (user).
     */
    List<InterviewScheduleResponse> getMyInterviews(Long userId);
}