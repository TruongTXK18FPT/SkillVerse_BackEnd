package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.LessonAttachment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for LessonAttachment entity
 * Handles database operations for lesson attachments (PDFs, links, etc.)
 */
@Repository
public interface LessonAttachmentRepository extends JpaRepository<LessonAttachment, Long> {

    /**
     * Find all attachments for a lesson, ordered by orderIndex
     */
    List<LessonAttachment> findByLessonIdOrderByOrderIndexAsc(Long lessonId);

    /**
     * Delete all attachments for a lesson
     */
    void deleteByLessonId(Long lessonId);

    /**
     * Count attachments for a lesson
     */
    long countByLessonId(Long lessonId);

    /**
     * Bulk update order index for a single attachment.
     * Eliminates N+1: replaces individual findById + save per attachment.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE LessonAttachment la SET la.orderIndex = :orderIndex WHERE la.id = :id")
    int updateOrderIndex(@Param("id") Long id, @Param("orderIndex") Integer orderIndex);
}
