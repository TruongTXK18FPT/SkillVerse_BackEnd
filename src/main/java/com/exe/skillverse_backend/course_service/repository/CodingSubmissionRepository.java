package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.CodingSubmission;
import com.exe.skillverse_backend.course_service.entity.enums.CodeSubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface CodingSubmissionRepository extends JpaRepository<CodingSubmission, Long> {

    /**
     * Find submissions by exercise ID with pagination
     */
    @Transactional(readOnly = true)
    @Query("SELECT cs FROM CodingSubmission cs WHERE cs.exercise.id = :exerciseId")
    Page<CodingSubmission> findByExerciseId(@Param("exerciseId") Long exerciseId, Pageable pageable);

    /**
     * Find submissions by user ID and exercise ID ordered by submission time descending
     */
    @Transactional(readOnly = true)
    @Query("SELECT cs FROM CodingSubmission cs WHERE cs.user.id = :userId AND cs.exercise.id = :exerciseId ORDER BY cs.submittedAt DESC")
    List<CodingSubmission> findByUserIdAndExerciseIdOrderBySubmittedAtDesc(@Param("userId") Long userId, @Param("exerciseId") Long exerciseId);

    /**
     * Find best score for user on specific exercise
     */
    @Transactional(readOnly = true)
    @Query("SELECT MAX(cs.score) FROM CodingSubmission cs WHERE cs.exercise.id = :exerciseId AND cs.user.id = :userId AND cs.score IS NOT NULL")
    Optional<BigDecimal> bestScore(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    /**
     * Find submissions by user ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT cs FROM CodingSubmission cs WHERE cs.user.id = :userId")
    Page<CodingSubmission> findByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * Find submissions by status
     */
    @Transactional(readOnly = true)
    @Query("SELECT cs FROM CodingSubmission cs WHERE cs.status = :status")
    Page<CodingSubmission> findByStatus(@Param("status") CodeSubmissionStatus status, Pageable pageable);

    /**
     * Find latest submission by user for exercise
     */
    @Transactional(readOnly = true)
    @Query("SELECT cs FROM CodingSubmission cs WHERE cs.user.id = :userId AND cs.exercise.id = :exerciseId ORDER BY cs.submittedAt DESC")
    Optional<CodingSubmission> findLatestByUserIdAndExerciseId(@Param("userId") Long userId, @Param("exerciseId") Long exerciseId);

    /**
     * Count submissions for exercise
     */
    @Transactional(readOnly = true)
    @Query("SELECT COUNT(cs) FROM CodingSubmission cs WHERE cs.exercise.id = :exerciseId")
    long countByExerciseId(@Param("exerciseId") Long exerciseId);

    /**
     * Count successful submissions for exercise
     */
    @Transactional(readOnly = true)
    @Query("SELECT COUNT(cs) FROM CodingSubmission cs WHERE cs.exercise.id = :exerciseId AND cs.status = 'ACCEPTED'")
    long countSuccessfulSubmissionsByExerciseId(@Param("exerciseId") Long exerciseId);

    /**
     * Find submissions that need evaluation (queued or running)
     */
    @Transactional(readOnly = true)
    @Query("SELECT cs FROM CodingSubmission cs WHERE cs.status IN ('QUEUED', 'RUNNING') ORDER BY cs.submittedAt ASC")
    List<CodingSubmission> findPendingEvaluation();

    /**
     * Find best submissions per user for exercise
     */
    @Transactional(readOnly = true)
    @Query("SELECT cs FROM CodingSubmission cs WHERE cs.exercise.id = :exerciseId AND cs.score = " +
           "(SELECT MAX(cs2.score) FROM CodingSubmission cs2 WHERE cs2.user.id = cs.user.id AND cs2.exercise.id = :exerciseId)")
    List<CodingSubmission> findBestSubmissionsByExerciseId(@Param("exerciseId") Long exerciseId);

    /**
     * Check if user has any successful submission for exercise
     */
    @Transactional(readOnly = true)
    @Query("SELECT CASE WHEN COUNT(cs) > 0 THEN true ELSE false END " +
           "FROM CodingSubmission cs WHERE cs.user.id = :userId AND cs.exercise.id = :exerciseId AND cs.status = 'ACCEPTED'")
    boolean hasUserPassedExercise(@Param("userId") Long userId, @Param("exerciseId") Long exerciseId);
}
