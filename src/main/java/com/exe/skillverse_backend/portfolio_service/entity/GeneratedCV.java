package com.exe.skillverse_backend.portfolio_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "generated_cvs")
public class GeneratedCV {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "cv_content", columnDefinition = "TEXT", nullable = false)
    private String cvContent; // HTML or Markdown formatted CV

    @Column(name = "cv_json", columnDefinition = "TEXT")
    private String cvJson; // JSON representation for easy editing

    @Column(name = "template_name", length = 100)
    private String templateName; // CV template used: PROFESSIONAL, CREATIVE, MINIMAL, etc.

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true; // Currently selected CV

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1; // CV version number

    @Column(name = "generated_by_ai", nullable = false)
    @Builder.Default
    private Boolean generatedByAi = false;

    @Column(name = "ai_prompt", columnDefinition = "TEXT")
    private String aiPrompt; // Prompt used for AI generation

    @Column(name = "pdf_url", length = 1000)
    private String pdfUrl; // Generated PDF URL (if converted)

    @Column(name = "pdf_public_id", length = 500)
    private String pdfPublicId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
