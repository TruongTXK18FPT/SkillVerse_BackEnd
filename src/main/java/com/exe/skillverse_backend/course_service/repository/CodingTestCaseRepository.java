package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.CodingTestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface CodingTestCaseRepository extends JpaRepository<CodingTestCase, Long> {

    /**
     * Find test cases by exercise ID ordered by order index ascending
     */
    @Transactional(readOnly = true)
    @Query("SELECT ctc FROM CodingTestCase ctc WHERE ctc.exercise.id = :exerciseId ORDER BY ctc.orderIndex ASC")
    List<CodingTestCase> findByExerciseIdOrderByOrderIndexAsc(@Param("exerciseId") Long exerciseId);

    /**
     * Delete all test cases by exercise ID
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM CodingTestCase ctc WHERE ctc.exercise.id = :exerciseId")
    int deleteByExerciseId(@Param("exerciseId") Long exerciseId);

    /**
     * Find test cases by exercise ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT ctc FROM CodingTestCase ctc WHERE ctc.exercise.id = :exerciseId")
    List<CodingTestCase> findByExerciseId(@Param("exerciseId") Long exerciseId);

    /**
     * Find public test cases (visible to students)
     */
    @Transactional(readOnly = true)
    @Query("SELECT ctc FROM CodingTestCase ctc WHERE ctc.exercise.id = :exerciseId AND ctc.kind = 'PUBLIC' ORDER BY ctc.orderIndex ASC")
    List<CodingTestCase> findPublicTestCasesByExerciseId(@Param("exerciseId") Long exerciseId);

    /**
     * Find hidden test cases (used for evaluation only)
     */
    @Transactional(readOnly = true)
    @Query("SELECT ctc FROM CodingTestCase ctc WHERE ctc.exercise.id = :exerciseId AND ctc.kind = 'HIDDEN' ORDER BY ctc.orderIndex ASC")
    List<CodingTestCase> findHiddenTestCasesByExerciseId(@Param("exerciseId") Long exerciseId);

    /**
     * Count test cases for an exercise
     */
    @Transactional(readOnly = true)
    @Query("SELECT COUNT(ctc) FROM CodingTestCase ctc WHERE ctc.exercise.id = :exerciseId")
    long countByExerciseId(@Param("exerciseId") Long exerciseId);

    /**
     * Count public test cases for an exercise
     */
    @Transactional(readOnly = true)
    @Query("SELECT COUNT(ctc) FROM CodingTestCase ctc WHERE ctc.exercise.id = :exerciseId AND ctc.kind = 'PUBLIC'")
    long countPublicTestCasesByExerciseId(@Param("exerciseId") Long exerciseId);

    /**
     * Calculate total score weight for exercise
     */
    @Transactional(readOnly = true)
    @Query("SELECT SUM(ctc.scoreWeight) FROM CodingTestCase ctc WHERE ctc.exercise.id = :exerciseId")
    java.math.BigDecimal sumScoreWeightByExerciseId(@Param("exerciseId") Long exerciseId);

    /**
     * Find test cases by kind
     */
    @Transactional(readOnly = true)
    @Query("SELECT ctc FROM CodingTestCase ctc WHERE ctc.exercise.id = :exerciseId AND ctc.kind = :kind ORDER BY ctc.orderIndex ASC")
    List<CodingTestCase> findByExerciseIdAndKind(@Param("exerciseId") Long exerciseId, @Param("kind") String kind);
}
