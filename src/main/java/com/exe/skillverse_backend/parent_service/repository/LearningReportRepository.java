package com.exe.skillverse_backend.parent_service.repository;

import com.exe.skillverse_backend.parent_service.entity.LearningReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LearningReportRepository extends JpaRepository<LearningReport, Long> {

    /**
     * Find all reports for a specific parent-student pair
     */
    List<LearningReport> findByParentIdAndStudentIdOrderByGeneratedAtDesc(Long parentId, Long studentId);

    /**
     * Find all reports for a specific parent
     */
    Page<LearningReport> findByParentIdOrderByGeneratedAtDesc(Long parentId, Pageable pageable);

    /**
     * Get the most recent report for a parent-student pair
     */
    Optional<LearningReport> findFirstByParentIdAndStudentIdOrderByGeneratedAtDesc(Long parentId, Long studentId);

    /**
     * Count total reports for a parent-student pair
     */
    long countByParentIdAndStudentId(Long parentId, Long studentId);

    /**
     * Check if a report was generated within the last N hours
     */
    @Query("SELECT COUNT(lr) > 0 FROM LearningReport lr WHERE lr.parent.id = :parentId AND lr.student.id = :studentId AND lr.generatedAt > :since")
    boolean existsRecentReport(Long parentId, Long studentId, java.time.LocalDateTime since);
}
