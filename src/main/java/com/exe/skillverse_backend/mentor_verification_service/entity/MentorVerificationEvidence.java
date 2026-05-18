package com.exe.skillverse_backend.mentor_verification_service.entity;

import com.exe.skillverse_backend.portfolio_service.entity.ExternalCertificate;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * [Nghiệp vụ] Bằng chứng đính kèm cho yêu cầu xác thực skill.
 * Có thể là chứng chỉ (link từ portfolio), GitHub repo, portfolio link,
 * hoặc kinh nghiệm công việc. Chứng chỉ linked sẽ được mark verified
 * trên portfolio khi admin approve request.
 */
@Entity
@Table(name = "mentor_verification_evidences", indexes = {
        @Index(name = "idx_mve_request", columnList = "verification_request_id"),
        @Index(name = "idx_mve_batch", columnList = "batch_request_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MentorVerificationEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Evidence can belong to either a single skill verification or a whole batch.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "verification_request_id", nullable = true)
    private MentorSkillVerificationRequest verificationRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "batch_request_id", nullable = true)
    private MentorBatchVerificationRequest batchRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 30)
    private EvidenceType evidenceType;

    /**
     * Link trực tiếp đến bằng chứng (URL).
     * Với CERTIFICATE type, đây có thể là credential_url hoặc certificate_image_url.
     */
    @Column(name = "evidence_url", length = 1000)
    private String evidenceUrl;

    /** Mô tả thêm về bằng chứng */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * FK đến ExternalCertificate trong portfolio (nếu evidence_type = CERTIFICATE).
     * Khi admin approve, certificate này sẽ được mark is_verified = true.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_id")
    private ExternalCertificate certificate;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
