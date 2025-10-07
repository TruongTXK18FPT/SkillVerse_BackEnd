package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.CodingExercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface CodingExerciseRepository extends JpaRepository<CodingExercise, Long> {

    /**
     * Find coding exercise by ID and module ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT ce FROM CodingExercise ce WHERE ce.id = :id AND ce.module.id = :moduleId")
    Optional<CodingExercise> findByIdAndModuleId(@Param("id") Long id, @Param("moduleId") Long moduleId);

    /**
     * Find coding exercises by module ID ordered by creation time
     */
    @Transactional(readOnly = true)
    @Query("SELECT ce FROM CodingExercise ce WHERE ce.module.id = :moduleId ORDER BY ce.id ASC")
    List<CodingExercise> findByModuleIdOrderByIdAsc(@Param("moduleId") Long moduleId);

    /**
     * Find coding exercise by module ID (assuming one exercise per module)
     */
    @Transactional(readOnly = true)
    @Query("SELECT ce FROM CodingExercise ce WHERE ce.module.id = :moduleId")
    Optional<CodingExercise> findByModuleId(@Param("moduleId") Long moduleId);

    /**
     * Find coding exercises by course ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT ce FROM CodingExercise ce WHERE ce.module.course.id = :courseId")
    List<CodingExercise> findByCourseId(@Param("courseId") Long courseId);

    /**
     * Find coding exercises by programming language
     */
    @Transactional(readOnly = true)
    @Query("SELECT ce FROM CodingExercise ce WHERE ce.language = :language")
    List<CodingExercise> findByLanguage(@Param("language") String language);

    /**
     * Count coding exercises in a course
     */
    @Transactional(readOnly = true)
    @Query("SELECT COUNT(ce) FROM CodingExercise ce WHERE ce.module.course.id = :courseId")
    long countByCourseId(@Param("courseId") Long courseId);

    /**
     * Check if coding exercise exists for module
     */
    @Transactional(readOnly = true)
    @Query("SELECT CASE WHEN COUNT(ce) > 0 THEN true ELSE false END FROM CodingExercise ce WHERE ce.module.id = :moduleId")
    boolean existsByModuleId(@Param("moduleId") Long moduleId);

    /**
     * Find coding exercises by course ID and language
     */
    @Transactional(readOnly = true)
    @Query("SELECT ce FROM CodingExercise ce WHERE ce.module.course.id = :courseId AND ce.language = :language")
    List<CodingExercise> findByCourseIdAndLanguage(@Param("courseId") Long courseId, @Param("language") String language);
}
