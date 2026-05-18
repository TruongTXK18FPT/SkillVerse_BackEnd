package com.exe.skillverse_backend.mentor_verification_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * [Nghiệp vụ] Yêu cầu xác thực skill của mentor.
 * Mỗi lần mentor muốn được công nhận 1 skill, họ tạo 1 request kèm
 * chứng chỉ/bằng cấp/kinh nghiệm → admin review → approve/reject.
 * Chứng chỉ kèm theo sẽ được lưu vào portfolio (external_certificates).
 */
@Entity
@Table(name = "mentor_skill_verification_requests", indexes = {
        @Index(name = "idx_msvr_mentor_status", columnList = "mentor_id, status"),
        @Index(name = "idx_msvr_status_requested", columnList = "status, requested_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MentorSkillVerificationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mentor who submitted this skill verification request */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentor_id", nullable = false)
    private User mentor;

    // Optional link to the batch this skill belongs to
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "batch_request_id")
    private MentorBatchVerificationRequest batchRequest;

    /** Tên skill cần xác thực (normalized uppercase, e.g. "REACT", "JAVA_SPRING_BOOT") */
    @Column(name = "skill_name", nullable = false, length = 100)
    private String skillName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private VerificationStatus status = VerificationStatus.PENDING;

    /** Link GitHub repo/profile liên quan skill */
    @Column(name = "github_url", length = 500)
    private String githubUrl;

    /** Link portfolio cá nhân */
    @Column(name = "portfolio_url", length = 500)
    private String portfolioUrl;

    /** Mô tả kinh nghiệm, lý do xin xác thực */
    @Column(name = "additional_notes", columnDefinition = "TEXT")
    private String additionalNotes;

    /** Ghi chú của admin khi duyệt/reject */
    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    /** Admin đã review */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /** Danh sách evidence kèm theo request */
    @OneToMany(mappedBy = "verificationRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MentorVerificationEvidence> evidences = new ArrayList<>();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
