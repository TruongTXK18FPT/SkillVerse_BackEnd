package com.exe.skillverse_backend.portfolio_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
