package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.CourseEnrollment.CourseEnrollmentId;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, CourseEnrollmentId> {

        /**
         * Find enrollment by course ID and user ID
         */
        @Transactional(readOnly = true)
        @Query("SELECT ce FROM CourseEnrollment ce WHERE ce.course.id = :courseId AND ce.user.id = :userId")
        Optional<CourseEnrollment> findByCourseIdAndUserId(
                        @Param("courseId") Long courseId,
                        @Param("userId") Long userId
        );

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT ce FROM CourseEnrollment ce WHERE ce.course.id = :courseId AND ce.user.id = :userId")
        Optional<CourseEnrollment> findByCourseIdAndUserIdForUpdate(
                        @Param("courseId") Long courseId,
                        @Param("userId") Long userId
        );

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

        @Transactional(readOnly = true)
        @Query(
                value = """
                        SELECT COUNT(*)
                        FROM course_enrollment ce
                        WHERE ce.learning_revision_id IS NULL
                        """,
                nativeQuery = true
        )
        long countLearningRevisionBackfillRemaining();

        @Transactional(readOnly = true)
        @Query(
                value = """
                        SELECT COUNT(*)
                        FROM course_enrollment ce
                        JOIN courses c ON c.id = ce.course_id
                        WHERE ce.learning_revision_id IS NULL
                          AND COALESCE(c.active_revision_id, c.latest_revision_id) IS NULL
                        """,
                nativeQuery = true
        )
        long countLearningRevisionBackfillNoTarget();

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query(
                value = """
                        WITH candidates AS (
                            SELECT ce2.user_id,
                                   ce2.course_id,
                                   COALESCE(c2.active_revision_id, c2.latest_revision_id) AS target_revision_id
                            FROM course_enrollment ce2
                            JOIN courses c2 ON c2.id = ce2.course_id
                            WHERE ce2.learning_revision_id IS NULL
                              AND COALESCE(c2.active_revision_id, c2.latest_revision_id) IS NOT NULL
                            ORDER BY ce2.course_id, ce2.user_id
                            LIMIT :batchSize
                        )
                        UPDATE course_enrollment ce
                        SET learning_revision_id = candidates.target_revision_id,
                            upgrade_policy_snapshot = COALESCE(ce.upgrade_policy_snapshot, c.upgrade_policy),
                            last_upgraded_at = COALESCE(ce.last_upgraded_at, NOW())
                        FROM candidates
                        JOIN courses c ON c.id = candidates.course_id
                        WHERE ce.user_id = candidates.user_id
                          AND ce.course_id = candidates.course_id
                        """,
                nativeQuery = true
        )
        int backfillLearningRevisionBatch(@Param("batchSize") int batchSize);

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

        /**
         * Count enrollments for a user since a specific date
         */
        @Transactional(readOnly = true)
        @Query("SELECT COUNT(ce) FROM CourseEnrollment ce WHERE ce.user.id = :userId AND ce.enrollDate >= :since")
        long countEnrollmentsSince(@Param("userId") Long userId, @Param("since") Instant since);

        @Transactional(readOnly = true)
        @Query("SELECT COALESCE(SUM(l.durationSec), 0) FROM Lesson l " +
                        "JOIN l.module m " +
                        "JOIN m.course c " +
                        "JOIN c.enrollments e " +
                        "WHERE e.user.id = :userId AND e.status = 'ENROLLED'")
        long sumTotalCourseDurationByUserId(@Param("userId") Long userId);

        /**
         * Count total students across all courses taught by a specific mentor
         */
        @Transactional(readOnly = true)
        @Query("SELECT COUNT(DISTINCT ce.user.id) FROM CourseEnrollment ce " +
                        "WHERE ce.course.author.id = :mentorId")
        long countTotalStudentsByMentorId(@Param("mentorId") Long mentorId);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("""
                        UPDATE CourseEnrollment ce
                        SET ce.learningRevisionId = :targetRevisionId,
                            ce.upgradePolicySnapshot = :policySnapshot,
                            ce.lastUpgradedAt = :upgradedAt
                        WHERE ce.course.id = :courseId
                          AND ce.learningRevisionId = :sourceRevisionId
                          AND ce.upgradePolicySnapshot = :policySnapshot
                          AND ce.status = :requiredStatus
                          AND NOT EXISTS (
                              SELECT 1
                              FROM AssignmentSubmission s
                              JOIN s.assignment a
                              JOIN a.module m
                              WHERE m.course.id = :courseId
                                AND s.user.id = ce.user.id
                                AND s.isNewest = true
                                AND s.score IS NULL
                          )
                          AND NOT EXISTS (
                              SELECT 1
                              FROM QuizAttemptSession qas
                              JOIN qas.quiz q
                              JOIN q.module qm
                              WHERE qm.course.id = :courseId
                                AND qas.userId = ce.user.id
                                AND qas.status = com.exe.skillverse_backend.course_service.entity.enums.QuizAttemptSessionStatus.IN_PROGRESS
                                AND qas.expiresAt > CURRENT_TIMESTAMP
                          )
                        """)
        int autoUpgradePinnedRevisionForEligibleEnrollments(
                        @Param("courseId") Long courseId,
                        @Param("sourceRevisionId") Long sourceRevisionId,
                        @Param("targetRevisionId") Long targetRevisionId,
                        @Param("policySnapshot") String policySnapshot,
                        @Param("upgradedAt") Instant upgradedAt,
                        @Param("requiredStatus") EnrollmentStatus requiredStatus);

        @Transactional(readOnly = true)
        @Query("""
                        SELECT COUNT(ce)
                        FROM CourseEnrollment ce
                        WHERE ce.course.id = :courseId
                          AND ce.learningRevisionId = :sourceRevisionId
                          AND ce.upgradePolicySnapshot = :policySnapshot
                          AND ce.status = :requiredStatus
                          AND NOT EXISTS (
                              SELECT 1
                              FROM AssignmentSubmission s
                              JOIN s.assignment a
                              JOIN a.module m
                              WHERE m.course.id = :courseId
                                AND s.user.id = ce.user.id
                                AND s.isNewest = true
                                AND s.score IS NULL
                          )
                          AND NOT EXISTS (
                              SELECT 1
                              FROM QuizAttemptSession qas
                              JOIN qas.quiz q
                              JOIN q.module qm
                              WHERE qm.course.id = :courseId
                                AND qas.userId = ce.user.id
                                AND qas.status = com.exe.skillverse_backend.course_service.entity.enums.QuizAttemptSessionStatus.IN_PROGRESS
                                AND qas.expiresAt > CURRENT_TIMESTAMP
                          )
                        """)
        long countEligibleEnrollmentsForAutoUpgrade(
                        @Param("courseId") Long courseId,
                        @Param("sourceRevisionId") Long sourceRevisionId,
                        @Param("policySnapshot") String policySnapshot,
                        @Param("requiredStatus") EnrollmentStatus requiredStatus);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("""
                        UPDATE CourseEnrollment ce
                        SET ce.upgradePolicySnapshot = :policySnapshot
                        WHERE ce.course.id = :courseId
                          AND ce.status = :requiredStatus
                          AND (ce.upgradePolicySnapshot IS NULL OR ce.upgradePolicySnapshot <> :policySnapshot)
                        """)
        int syncUpgradePolicySnapshotByStatus(
                        @Param("courseId") Long courseId,
                        @Param("policySnapshot") String policySnapshot,
                        @Param("requiredStatus") EnrollmentStatus requiredStatus);
}
