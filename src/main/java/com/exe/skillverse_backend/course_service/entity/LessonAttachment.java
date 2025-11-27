package com.exe.skillverse_backend.course_service.entity;

import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.course_service.entity.enums.AttachmentType;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * Represents attachments (PDFs, links, etc.) for Reading lessons
 * Allows mentors to provide supplementary materials like Coursera
 */
@Entity
@Table(name = "lesson_attachments", indexes = {
        @Index(name = "idx_lesson_attachments_lesson_id", columnList = "lesson_id"),
        @Index(name = "idx_lesson_attachments_order", columnList = "lesson_id, order_index")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Lesson lesson;

    // For uploaded files (PDF, DOCX, etc.) - stored in Cloudinary
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Media media;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String description;

    // For external links (Google Drive, GitHub, etc.)
    @Column(length = 500)
    private String externalUrl;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private AttachmentType type;

    private Long fileSize; // In bytes (for uploaded files)

    private Integer orderIndex; // Display order

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Get download URL (either from uploaded media or external URL)
     */
    @Transient
    public String getDownloadUrl() {
        return media != null ? media.getUrl() : externalUrl;
    }

    /**
     * Format file size for display
     */
    @Transient
    public String getFormattedFileSize() {
        if (fileSize == null)
            return null;
        if (fileSize < 1024)
            return fileSize + " B";
        if (fileSize < 1024 * 1024)
            return String.format("%.2f KB", fileSize / 1024.0);
        return String.format("%.2f MB", fileSize / (1024.0 * 1024.0));
    }
}
