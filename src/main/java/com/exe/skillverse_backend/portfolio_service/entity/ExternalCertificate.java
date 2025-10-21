package com.exe.skillverse_backend.portfolio_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "external_certificates")
public class ExternalCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "issuing_organization", nullable = false, length = 255)
    private String issuingOrganization;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "credential_id", length = 255)
    private String credentialId;

    @Column(name = "credential_url", length = 1000)
    private String credentialUrl; // Verification URL

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "certificate_image_url", length = 1000)
    private String certificateImageUrl; // Cloudinary URL for certificate image

    @Column(name = "certificate_image_public_id", length = 500)
    private String certificateImagePublicId;

    @ElementCollection
    @CollectionTable(name = "certificate_skills", joinColumns = @JoinColumn(name = "certificate_id"))
    @Column(name = "skill")
    @Builder.Default
    private List<String> skills = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private CertificateCategory category;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false; // Admin can verify external certificates

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

    public enum CertificateCategory {
        TECHNICAL,
        DESIGN,
        BUSINESS,
        SOFT_SKILLS,
        LANGUAGE,
        OTHER
    }
}
