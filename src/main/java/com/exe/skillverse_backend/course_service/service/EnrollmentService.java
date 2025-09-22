package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.course_service.dto.enrollmentdto.EnrollRequestDTO;
import com.exe.skillverse_backend.course_service.dto.enrollmentdto.EnrollmentDetailDTO;
import com.exe.skillverse_backend.course_service.dto.enrollmentdto.EnrollmentStatsDTO;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for managing course enrollments.
 * Handles enrollment operations, progress tracking, and enrollment analytics.
 */
public interface EnrollmentService {

    /**
     * Enroll a user in a course
     * 
     * @param dto enrollment details
     * @param userId ID of the user enrolling
     * @return enrollment details
     * @throws NotFoundException if course not found
     * @throws IllegalArgumentException if already enrolled or invalid enrollment
     */
    EnrollmentDetailDTO enrollUser(EnrollRequestDTO dto, Long userId);

    /**
     * Unenroll a user from a course
     * 
     * @param courseId ID of the course
     * @param userId ID of the user
     * @throws NotFoundException if enrollment not found
     * @throws AccessDeniedException if not authorized
     */
    void unenrollUser(Long courseId, Long userId);

    /**
     * Get enrollment details for a user in a course
     * 
     * @param courseId ID of the course
     * @param userId ID of the user
     * @return enrollment details
     * @throws NotFoundException if enrollment not found
     */
    EnrollmentDetailDTO getEnrollment(Long courseId, Long userId);

    /**
     * Check if a user is enrolled in a course
     * 
     * @param courseId ID of the course
     * @param userId ID of the user
     * @return true if enrolled, false otherwise
     */
    boolean isUserEnrolled(Long courseId, Long userId);

    /**
     * Get all enrollments for a user with pagination
     * 
     * @param userId ID of the user
     * @param pageable pagination parameters
     * @return paginated enrollments
     */
    PageResponse<EnrollmentDetailDTO> getUserEnrollments(Long userId, Pageable pageable);

    /**
     * Get all enrollments for a course with pagination
     * 
     * @param courseId ID of the course
     * @param pageable pagination parameters
     * @param actorId ID of the requesting user (for authorization)
     * @return paginated enrollments
     * @throws AccessDeniedException if not course author or admin
     */
    PageResponse<EnrollmentDetailDTO> getCourseEnrollments(Long courseId, Pageable pageable, Long actorId);

    /**
     * Update enrollment completion status
     * 
     * @param courseId ID of the course
     * @param userId ID of the user
     * @param completed whether the course is completed
     * @throws NotFoundException if enrollment not found
     */
    void updateCompletionStatus(Long courseId, Long userId, boolean completed);

    /**
     * Update enrollment progress percentage
     * 
     * @param courseId ID of the course
     * @param userId ID of the user
     * @param progressPercentage progress percentage (0-100)
     * @throws NotFoundException if enrollment not found
     * @throws IllegalArgumentException if invalid progress percentage
     */
    void updateProgress(Long courseId, Long userId, Integer progressPercentage);

    /**
     * Get enrollment statistics for a course
     * 
     * @param courseId ID of the course
     * @param actorId ID of the requesting user (for authorization)
     * @return enrollment statistics
     * @throws NotFoundException if course not found
     * @throws AccessDeniedException if not course author or admin
     */
    EnrollmentStatsDTO getEnrollmentStats(Long courseId, Long actorId);

    /**
     * Get recent enrollments across all courses (admin only)
     * 
     * @param pageable pagination parameters
     * @param actorId ID of the requesting user (for authorization)
     * @return paginated recent enrollments
     * @throws AccessDeniedException if not admin
     */
    PageResponse<EnrollmentDetailDTO> getRecentEnrollments(Pageable pageable, Long actorId);
}