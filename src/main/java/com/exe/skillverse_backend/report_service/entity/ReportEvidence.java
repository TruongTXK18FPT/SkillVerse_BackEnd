package com.exe.skillverse_backend.report_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entity representing evidence/attachments for a violation report.
 * Can be screenshots, documents, links, recordings, etc.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "report_evidences")
public class ReportEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The violation report this evidence belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "violation_report_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ViolationReport violationReport;

    /**
     * Type of evidence
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false)
    private EvidenceType evidenceType;

    /**
     * URL to the evidence file (if uploaded to cloud storage)
     */
    @Column(name = "file_url")
    private String fileUrl;

    /**
     * Original filename of the uploaded file
     */
    @Column(name = "file_name")
    private String fileName;

    /**
     * Description or caption for this evidence
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * External link URL (for link type evidence)
     */
    @Column(name = "external_link")
    private String externalLink;

    /**
     * File size in bytes
     */
    @Column(name = "file_size")
    private Long fileSize;

    /**
     * MIME type of the file
     */
    @Column(name = "mime_type")
    private String mimeType;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Types of evidence that can be submitted
     */
    public enum EvidenceType {
        SCREENSHOT,     // Ảnh chụp màn hình
        DOCUMENT,       // Tài liệu
        VIDEO,          // Video recording
        AUDIO,          // Audio recording
        LINK,           // External link
        CHAT_LOG,       // Chat history
        OTHER           // Khác
    }
}
