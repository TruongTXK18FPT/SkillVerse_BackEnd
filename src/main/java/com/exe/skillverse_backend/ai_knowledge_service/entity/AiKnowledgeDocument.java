package com.exe.skillverse_backend.ai_knowledge_service.entity;

import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeApprovalStatus;
import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeIngestionStatus;
import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeUseCase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing an AI knowledge document.
 * Stores metadata for documents uploaded by admin/mentor for RAG ingestion.
 */
@Entity
@Table(name = "ai_knowledge_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiKnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "media_id", nullable = false)
    private Long mediaId;

    @Column(nullable = false)
    private String title;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "use_case", nullable = false, length = 50)
    private AiKnowledgeUseCase useCase;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private AiKnowledgeApprovalStatus approvalStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "ingestion_status", nullable = false, length = 20)
    private AiKnowledgeIngestionStatus ingestionStatus;

    @Column(name = "uploaded_by_user_id", nullable = false)
    private Long uploadedByUserId;

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column(name = "mentor_id")
    private Long mentorId;

    @Column(name = "skill_name")
    private String skillName;

    @Column(name = "skill_slug")
    private String skillSlug;

    @Column(length = 100)
    private String industry;

    @Column(length = 100)
    private String level;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "module_id")
    private Long moduleId;

    @Column(name = "assignment_id")
    private Long assignmentId;

    @Column(name = "doc_type", nullable = false, length = 20)
    private String docType;

    @Column(name = "rag_doc_id", length = 100, unique = true)
    private String ragDocId;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "original_file_name", nullable = false, length = 500)
    private String originalFileName;

    @Column(name = "storage_folder", nullable = false, length = 1000)
    private String storageFolder;

    @Column(name = "storage_url", nullable = false, length = 2000)
    private String storageUrl;

    @Lob
    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Lob
    @Column(name = "extract_error", columnDefinition = "TEXT")
    private String extractError;

    @Lob
    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "indexed_at")
    private LocalDateTime indexedAt;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
