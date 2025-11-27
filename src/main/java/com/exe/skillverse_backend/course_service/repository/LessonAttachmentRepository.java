package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.LessonAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

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
}
