package com.exe.skillverse_backend.shared.repository;

import com.exe.skillverse_backend.shared.entity.Media;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface MediaRepository extends JpaRepository<Media, Long>, JpaSpecificationExecutor<Media> {

    /**
     * Find media files uploaded by a specific user
     */
    @Transactional(readOnly = true)
    Page<Media> findByUploadedByUser_Id(Long userId, Pageable pageable);

    /**
     * Find media files associated with a course, ordered by creation date descending
     * TODO: Add courseId field to Media entity for direct association
     */
    @Transactional(readOnly = true)
    @Query("SELECT m FROM Media m JOIN m.coursesAsThumbnail c WHERE c.id = :courseId ORDER BY m.uploadedAt DESC")
    List<Media> findByCourseIdOrderByCreatedAtDesc(@Param("courseId") Long courseId);

    /**
     * Find media files associated with a lesson, ordered by creation date descending
     * TODO: Add lessonId field to Media entity for direct association
     */
    @Transactional(readOnly = true)
    @Query("SELECT m FROM Media m JOIN m.lessonsAsVideo l WHERE l.id = :lessonId ORDER BY m.uploadedAt DESC")
    List<Media> findByLessonIdOrderByCreatedAtDesc(@Param("lessonId") Long lessonId);

    /**
     * Search media files by filename (case-insensitive)
     * TODO: Add deletedAt support when soft delete is implemented
     */
    @Transactional(readOnly = true)
    @Query("SELECT m FROM Media m WHERE lower(m.fileName) LIKE lower(concat('%', :q, '%')) ORDER BY m.uploadedAt DESC")
    Page<Media> searchByFileName(@Param("q") String query, Pageable pageable);

    /**
     * Check if a URL already exists
     */
    @Transactional(readOnly = true)
    boolean existsByUrl(String url);

    /**
     * Find media files by uploaded user ID, ordered by upload date descending
     */
    @Transactional(readOnly = true)
    List<Media> findByUploadedByOrderByUploadedAtDesc(Long uploadedBy);

    /**
     * Soft delete media by ID and owner (when soft delete is implemented)
     * TODO: Implement when deletedAt field is added to Media entity
     */
    @Modifying
    @Transactional
    @Query("UPDATE Media m SET m.uploadedAt = CURRENT_TIMESTAMP WHERE m.id = :id AND m.uploadedBy = :ownerId")
    int deleteByIdAndUploadedByUser_Id(@Param("id") Long id, @Param("ownerId") Long ownerId);

    /**
     * Find all media files by type
     */
    @Transactional(readOnly = true)
    List<Media> findByTypeOrderByUploadedAtDesc(String type);

    /**
     * Count media files by uploaded user
     */
    @Transactional(readOnly = true)
    long countByUploadedBy(Long uploadedBy);

    /**
     * Find media files larger than specified size
     */
    @Transactional(readOnly = true)
    List<Media> findByFileSizeGreaterThanOrderByFileSizeDesc(Long fileSize);

    /**
     * Find media by ID with user relationship loaded
     */
    @Transactional(readOnly = true)
    @Query("SELECT m FROM Media m LEFT JOIN FETCH m.uploadedByUser WHERE m.id = :id")
    Media findByIdWithUser(@Param("id") Long id);
}
