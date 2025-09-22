package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long>, JpaSpecificationExecutor<Course> {

    /**
     * Find courses by status and title containing search query (case insensitive)
     */
    @Transactional(readOnly = true)
    Page<Course> findByStatusAndTitleContainingIgnoreCase(CourseStatus status, String q, Pageable pageable);

    /**
     * Find courses by title containing search query (case insensitive)
     */
    @Transactional(readOnly = true)
    Page<Course> findByTitleContainingIgnoreCase(String q, Pageable pageable);

    /**
     * Check if course with given slug exists
     */
    @Transactional(readOnly = true)
    boolean existsBySlug(String slug);

    /**
     * Search courses by tags
     */
    @Transactional(readOnly = true)
    @Query("SELECT c FROM Course c JOIN c.courseSkills cs JOIN cs.skill s WHERE s.name IN :tags")
    Page<Course> searchByTags(@Param("tags") Set<String> tags, Pageable pageable);

    /**
     * Count courses by author
     */
    @Transactional(readOnly = true)
    long countByAuthorId(Long authorId);

    /**
     * Find courses by author with pagination
     */
    @Transactional(readOnly = true)
    Page<Course> findByAuthorId(Long authorId, Pageable pageable);

    /**
     * Find courses by status with pagination
     */
    @Transactional(readOnly = true)
    Page<Course> findByStatus(CourseStatus status, Pageable pageable);
}
