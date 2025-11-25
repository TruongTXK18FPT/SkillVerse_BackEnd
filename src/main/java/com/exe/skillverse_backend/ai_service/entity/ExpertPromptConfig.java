package com.exe.skillverse_backend.ai_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "expert_prompt_configs", indexes = {
    @Index(name = "idx_role_industry_domain", columnList = "jobRole, industry, domain")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpertPromptConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Broad field (e.g., "Information Technology")
     */
    @Column(nullable = false)
    private String domain;

    /**
     * Industry/Sector (e.g., "Software Development")
     */
    @Column(nullable = false)
    private String industry;

    /**
     * Specific Job Role (e.g., "Backend Developer")
     * This will be used for matching.
     */
    @Column(nullable = false)
    private String jobRole;

    /**
     * Keywords for fuzzy matching (e.g., "backend, back-end, java developer")
     */
    @Column(columnDefinition = "TEXT")
    private String keywords;

    /**
     * The detailed system prompt for this role
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String systemPrompt;

    /**
     * Media URL (icon/image) for this role from Cloudinary
     * Can be used for displaying visual representation in frontend
     */
    @Column(length = 500)
    private String mediaUrl;

    @Builder.Default
    private boolean isActive = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
