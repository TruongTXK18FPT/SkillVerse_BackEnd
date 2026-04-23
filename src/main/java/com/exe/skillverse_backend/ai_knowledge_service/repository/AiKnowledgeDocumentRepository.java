package com.exe.skillverse_backend.ai_knowledge_service.repository;

import com.exe.skillverse_backend.ai_knowledge_service.entity.AiKnowledgeDocument;
import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeApprovalStatus;
import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeIngestionStatus;
import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeUseCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiKnowledgeDocumentRepository extends JpaRepository<AiKnowledgeDocument, Long> {

    /**
     * Find active document by ID (not archived).
     */
    Optional<AiKnowledgeDocument> findByIdAndArchivedAtIsNull(Long id);

    /**
     * List all active documents for admin with optional filters.
     */
    @Query("""
        SELECT d FROM AiKnowledgeDocument d
        WHERE d.archivedAt IS NULL
        AND (:useCase IS NULL OR d.useCase = :useCase)
        AND (:approvalStatus IS NULL OR d.approvalStatus = :approvalStatus)
        AND (:ingestionStatus IS NULL OR d.ingestionStatus = :ingestionStatus)
        AND (:skillSlug IS NULL OR d.skillSlug = :skillSlug)
        AND (:courseId IS NULL OR d.courseId = :courseId)
        AND (:moduleId IS NULL OR d.moduleId = :moduleId)
        AND (:assignmentId IS NULL OR d.assignmentId = :assignmentId)
        ORDER BY d.createdAt DESC
        """)
    Page<AiKnowledgeDocument> findAllActiveWithFilters(
            @Param("useCase") AiKnowledgeUseCase useCase,
            @Param("approvalStatus") AiKnowledgeApprovalStatus approvalStatus,
            @Param("ingestionStatus") AiKnowledgeIngestionStatus ingestionStatus,
            @Param("skillSlug") String skillSlug,
            @Param("courseId") Long courseId,
            @Param("moduleId") Long moduleId,
            @Param("assignmentId") Long assignmentId,
            Pageable pageable);

    /**
     * List documents uploaded by a specific mentor.
     */
    Page<AiKnowledgeDocument> findByMentorIdAndArchivedAtIsNullOrderByCreatedAtDesc(
            Long mentorId, Pageable pageable);
}
