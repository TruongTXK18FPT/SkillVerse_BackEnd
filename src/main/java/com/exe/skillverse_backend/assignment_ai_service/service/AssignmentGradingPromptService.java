package com.exe.skillverse_backend.assignment_ai_service.service;

import com.exe.skillverse_backend.course_service.entity.Assignment;

/**
 * Service for building AI grading prompts.
 */
public interface AssignmentGradingPromptService {

    /**
     * Build the user prompt for AI grading.
     *
     * @param assignment              Assignment with criteria
     * @param submissionTextExtracted Text extracted from submitted file
     * @param gradingStyle           STANDARD | STRICT | LENIENT
     * @param customPrompt          Mentor custom prompt (nullable)
     * @return the full prompt string to send to the AI model
     */
    String buildGradingPrompt(Assignment assignment, String submissionTextExtracted,
            String gradingStyle, String customPrompt);
}
