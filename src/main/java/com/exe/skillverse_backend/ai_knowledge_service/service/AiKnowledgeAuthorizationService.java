package com.exe.skillverse_backend.ai_knowledge_service.service;

import com.exe.skillverse_backend.ai_knowledge_service.entity.AiKnowledgeDocument;
import com.exe.skillverse_backend.auth_service.entity.User;

/**
 * Authorization service for AI knowledge document operations.
 * Enforces mentor verified skill and course ownership rules.
 */
public interface AiKnowledgeAuthorizationService {

    /**
     * Assert that the mentor has verified the given skill.
     * 
     * @param mentor the mentor user
     * @param skillName the skill name to check
     * @throws com.exe.skillverse_backend.shared.exception.ApiException if skill not verified
     */
    void assertMentorVerifiedSkill(User mentor, String skillName);

    /**
     * Assert that the mentor owns the course (course.author.id == mentorId).
     * 
     * @param mentor the mentor user
     * @param courseId the course ID
     * @throws com.exe.skillverse_backend.shared.exception.ApiException if not owner
     */
    void assertMentorOwnsCourse(User mentor, Long courseId);

    /**
     * Assert that the mentor owns the module's course.
     * 
     * @param mentor the mentor user
     * @param moduleId the module ID
     * @throws com.exe.skillverse_backend.shared.exception.ApiException if not owner
     */
    void assertMentorOwnsModule(User mentor, Long moduleId);

    /**
     * Assert that the mentor owns the assignment's course.
     * 
     * @param mentor the mentor user
     * @param assignmentId the assignment ID
     * @throws com.exe.skillverse_backend.shared.exception.ApiException if not owner
     */
    void assertMentorOwnsAssignment(User mentor, Long assignmentId);

    /**
     * Assert that the mentor can access the document (owns it).
     * 
     * @param mentor the mentor user
     * @param document the document to check
     * @throws com.exe.skillverse_backend.shared.exception.ApiException if not owner
     */
    void assertMentorCanAccessDocument(User mentor, AiKnowledgeDocument document);
}
