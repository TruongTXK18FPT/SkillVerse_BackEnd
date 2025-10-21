package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment.CourseEnrollmentId;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, CourseEnrollmentId> {

    /**
     * Find enrollment by course ID and user ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT ce FROM CourseEnrollment ce WHERE ce.course.id = :courseId AND ce.user.id = :userId")
    Optional<CourseEnrollment> findByCourseIdAndUserId(@Param("courseId") Long courseId, @Param("userId") Long userId);

    /**
     * Find enrollments by user ID with pagination
     */
    @Transactional(readOnly = true)
    @Query("SELECT ce FROM CourseEnrollment ce WHERE ce.user.id = :userId")
    Page<CourseEnrollment> findByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * Check if user is enrolled in course
     */
    @Transactional(readOnly = true)
    @Query("SELECT CASE WHEN COUNT(ce) > 0 THEN true ELSE false END " +
            "FROM CourseEnrollment ce WHERE ce.course.id = :courseId AND ce.user.id = :userId")
    boolean existsByCourseIdAndUserId(@Param("courseId") Long courseId, @Param("userId") Long userId);

    /**
     * Count enrollments for a course
     */
    @Transactional(readOnly = true)
    @Query("SELECT COUNT(ce) FROM CourseEnrollment ce WHERE ce.course.id = :courseId")
    long countByCourseId(@Param("courseId") Long courseId);

    /**
     * Find enrollments by course ID with pagination
     */
    @Transactional(readOnly = true)
    @Query("SELECT ce FROM CourseEnrollment ce WHERE ce.course.id = :courseId")
    Page<CourseEnrollment> findByCourseId(@Param("courseId") Long courseId, Pageable pageable);

    /**
     * Find enrollments by status
     */
    @Transactional(readOnly = true)
    @Query("SELECT ce FROM CourseEnrollment ce WHERE ce.status = :status")
    Page<CourseEnrollment> findByStatus(@Param("status") EnrollmentStatus status, Pageable pageable);

    /**
     * Find active enrollments by user ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT ce FROM CourseEnrollment ce WHERE ce.user.id = :userId AND ce.status = 'ENROLLED'")
    List<CourseEnrollment> findActiveEnrollmentsByUserId(@Param("userId") Long userId);

    /**
     * Count active enrollments by course
     */
    @Transactional(readOnly = true)
    @Query("SELECT COUNT(ce) FROM CourseEnrollment ce WHERE ce.course.id = :courseId AND ce.status = 'ENROLLED'")
    long countActiveEnrollmentsByCourseId(@Param("courseId") Long courseId);

    /**
     * Find user enrollments with progress above threshold
     */
    @Transactional(readOnly = true)
    @Query("SELECT ce FROM CourseEnrollment ce WHERE ce.user.id = :userId AND ce.progressPercent >= :minProgress")
    List<CourseEnrollment> findUserEnrollmentsWithMinProgress(@Param("userId") Long userId,
            @Param("minProgress") Integer minProgress);
}
