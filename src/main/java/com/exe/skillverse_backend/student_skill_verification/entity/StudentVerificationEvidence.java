package com.exe.skillverse_backend.student_skill_verification.entity;

import com.exe.skillverse_backend.mentor_verification_service.entity.EvidenceType;
import com.exe.skillverse_backend.portfolio_service.entity.ExternalCertificate;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * [Nghiệp vụ] Bằng chứng đính kèm cho yêu cầu xác thực skill student.
 * Có thể là chứng chỉ (hình ảnh Cloudinary), GitHub repo, portfolio link,
 * kinh nghiệm công việc, project đã làm, hoặc học vấn/bằng cấp.
 */
@Entity
@Table(name = "student_verification_evidences", indexes = {
        @Index(name = "idx_sve_request", columnList = "verification_request_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentVerificationEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "verification_request_id", nullable = false)
    private StudentSkillVerificationRequest verificationRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 30)
    private EvidenceType evidenceType;

    /**
     * Link trực tiếp đến bằng chứng (URL Cloudinary hoặc external link).
     */
    @Column(name = "evidence_url", length = 1000)
    private String evidenceUrl;

    /** Mô tả thêm về bằng chứng */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * FK đến ExternalCertificate trong portfolio (nếu evidence_type = CERTIFICATE).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_id")
    private ExternalCertificate certificate;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
