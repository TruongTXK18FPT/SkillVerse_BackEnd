package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
     * Find courses by status with eager loading of author (avoid N+1)
     */
    @Transactional(readOnly = true)
    @Query("SELECT c FROM Course c LEFT JOIN FETCH c.author WHERE c.status = :status")
    Page<Course> findByStatusWithAuthor(@Param("status") CourseStatus status, Pageable pageable);
    
    /**
     * Find all courses with eager loading of author (avoid N+1)
     */
    @Transactional(readOnly = true)
    @Query(value = "SELECT c FROM Course c LEFT JOIN FETCH c.author",
           countQuery = "SELECT COUNT(c) FROM Course c")
    Page<Course> findAllWithAuthor(Pageable pageable);

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
     * Count courses by author and status.
     */
    @Transactional(readOnly = true)
    long countByAuthorIdAndStatus(Long authorId, CourseStatus status);

    /**
     * Find courses by author with pagination
     */
    @Transactional(readOnly = true)
    Page<Course> findByAuthorId(Long authorId, Pageable pageable);

    /**
     * Find courses by author with eager loading of author (avoid N+1 and LazyInitializationException)
     */
    @Transactional(readOnly = true)
    @Query("SELECT c FROM Course c LEFT JOIN FETCH c.author WHERE c.author.id = :authorId")
    Page<Course> findByAuthorIdWithAuthor(@Param("authorId") Long authorId, Pageable pageable);

    /**
     * Find courses by author with a specific status (for tab filtering by single status)
     */
    @Transactional(readOnly = true)
    @Query("SELECT c FROM Course c LEFT JOIN FETCH c.author WHERE c.author.id = :authorId AND c.status = :status")
    Page<Course> findByAuthorIdAndStatus(@Param("authorId") Long authorId, @Param("status") CourseStatus status, Pageable pageable);

    /**
     * Find courses by author excluding ARCHIVED (for "All" tab — non-archived only)
     */
    @Transactional(readOnly = true)
    @Query(value = "SELECT c FROM Course c LEFT JOIN FETCH c.author WHERE c.author.id = :authorId AND c.status <> com.exe.skillverse_backend.course_service.entity.enums.CourseStatus.ARCHIVED",
           countQuery = "SELECT COUNT(c) FROM Course c WHERE c.author.id = :authorId AND c.status <> com.exe.skillverse_backend.course_service.entity.enums.CourseStatus.ARCHIVED")
    Page<Course> findByAuthorIdNonArchived(@Param("authorId") Long authorId, Pageable pageable);

    /**
     * Find courses by status with pagination
     */
    @Transactional(readOnly = true)
    Page<Course> findByStatus(CourseStatus status, Pageable pageable);
    
    /**
     * Find course by ID with eager loading of author (avoid LazyInitializationException)
     */
    @Transactional(readOnly = true)
    @Query("SELECT c FROM Course c LEFT JOIN FETCH c.author WHERE c.id = :id")
    Course findByIdWithAuthor(@Param("id") Long id);
    
    /**
     * Find course by ID with eager loading of author and modules (avoid LazyInitializationException)
     */
    @Transactional(readOnly = true)
    @Query("SELECT c FROM Course c LEFT JOIN FETCH c.author LEFT JOIN FETCH c.modules WHERE c.id = :id")
    Course findByIdWithAuthorAndModules(@Param("id") Long id);

    /**
     * Lock course row so enrollment creation can read a consistent active revision/policy snapshot.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT c FROM Course c WHERE c.id = :id")
    Optional<Course> findByIdForEnrollmentSnapshot(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Course c WHERE c.id = :id")
    Optional<Course> findByIdForRevisionApproval(@Param("id") Long id);

    /**
     * Find course by title
     */
    @Transactional(readOnly = true)
    Optional<Course> findByTitle(String title);
    
    /**
     * Find courses by thumbnail media ID - for detaching media
     * ✅ OPTIMIZED: Better than findAll().filter() which loads all courses
     */
    @Transactional(readOnly = true)
    List<Course> findByThumbnailId(Long thumbnailId);

    /**
     * Count courses by status (used for admin dashboard stats)
     */
    @Transactional(readOnly = true)
    long countByStatus(CourseStatus status);

    /**
     * Batch fetch courses by IDs and status (GAP-7: replaces N parallel getCourse() calls
     * in frontend's useRoadmapMappedCourses hook).
     */
    @Transactional(readOnly = true)
    List<Course> findByIdInAndStatus(@Param("ids") List<Long> ids, @Param("status") CourseStatus status);

    /**
     * Lightweight projection of all PUBLIC courses for the course catalog index.
     * Returns only the fields needed for scoring — no nested entities.
     *
     * <p>Used by {@link com.exe.skillverse_backend.ai_service.service.impl.AiCourseCatalogServiceImpl}
     * to build the in-memory course catalog index (Phase A pre-selection).
     *
     * <p>Returns Object[]:
     * [id, title, description, shortDescription, category, level, createdAt, enrollmentCount]
     */
    @Transactional(readOnly = true)
    @Query("""
        SELECT c.id, c.title, c.description, c.shortDescription, c.category, c.level,
               c.createdAt, COUNT(e.id)
        FROM Course c
        LEFT JOIN c.enrollments e
        WHERE c.status = com.exe.skillverse_backend.course_service.entity.enums.CourseStatus.PUBLIC
        GROUP BY c.id, c.title, c.description, c.shortDescription, c.category, c.level, c.createdAt
        """)
    List<Object[]> findAllPublicCourseProjections();

    /**
     * V2: includes learningObjectives, requirements, and courseSkillTags for BM25 index.
     * Returns Object[] (11 columns):
     * [id, title, description, shortDescription, category, level,
     *  createdAt, enrollmentCount, learningObjectives, requirements, courseSkillTags]
     *
     * <p>Filters to:
     * - Course.status = PUBLIC (only live courses)
     * - Only the APPROVED revision via Course.active_revision_id
     * - Metadata from CourseRevision (not Course) for correctness after revision publishes
     *
     * <p>Metadata columns are JSONB on CourseRevision. Cast to TEXT so AiCourseCatalogServiceImpl
     * can parse them as JSON arrays and join into searchable text.
     */
    @Transactional(readOnly = true)
    @Query(value = """
        SELECT c.id, c.title, c.description, c.short_description, c.category, c.level,
               c.created_at, COUNT(e.user_id),
               cr.learning_objectives_json::TEXT,
               cr.requirements_json::TEXT,
               cr.course_skill_tags_json::TEXT
        FROM courses c
        INNER JOIN course_revisions cr ON cr.id = c.active_revision_id
        LEFT JOIN course_enrollment e ON c.id = e.course_id
        WHERE c.status = 'PUBLIC'
        GROUP BY c.id, c.title, c.short_description, c.category, c.level, c.created_at, cr.id,
                 cr.learning_objectives_json, cr.requirements_json, cr.course_skill_tags_json
        """,
        nativeQuery = true)
    List<Object[]> findAllPublicCourseProjectionsV2();

    /**
     * Fetch a single PUBLIC course by ID with its active revision metadata.
     * Used for incremental BM25 catalog refresh on course revision approval.
     */
    @Transactional(readOnly = true)
    @Query(value = """
        SELECT c.id, c.title, c.description, c.short_description, c.category, c.level,
               c.created_at, COUNT(e.user_id),
               cr.learning_objectives_json::TEXT,
               cr.requirements_json::TEXT,
               cr.course_skill_tags_json::TEXT
        FROM courses c
        INNER JOIN course_revisions cr ON cr.id = c.active_revision_id
        LEFT JOIN course_enrollment e ON c.id = e.course_id
        WHERE c.status = 'PUBLIC' AND c.id = :courseId
        GROUP BY c.id, c.title, c.short_description, c.category, c.level, c.created_at, cr.id,
                 cr.learning_objectives_json, cr.requirements_json, cr.course_skill_tags_json
        """,
        nativeQuery = true)
    List<Object[]> findPublicCourseById(@Param("courseId") Long courseId);
}
