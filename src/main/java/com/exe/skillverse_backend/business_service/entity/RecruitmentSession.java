package com.exe.skillverse_backend.business_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.business_service.entity.enums.RecruitmentJobContextType;
import com.exe.skillverse_backend.business_service.entity.enums.RecruitmentSessionSource;
import com.exe.skillverse_backend.business_service.entity.enums.RecruitmentSessionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity lưu trữ phiên chat tuyển dụng giữa recruiter và candidate
 * Khác với job application (candidate chủ động nộp đơn), đây là recruiter chủ động tiếp cận candidate
 */
@Entity
@Table(name = "recruitment_sessions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_recruitment_session_recruiter_candidate_job",
                        columnNames = {"recruiter_id", "candidate_id", "job_posting_id"})
        },
        indexes = {
                @Index(name = "idx_recruitment_session_recruiter", columnList = "recruiter_id"),
                @Index(name = "idx_recruitment_session_candidate", columnList = "candidate_id"),
                @Index(name = "idx_recruitment_session_status", columnList = "status"),
                @Index(name = "idx_recruitment_session_created", columnList = "created_at")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecruitmentSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruiter_id", nullable = false)
    private User recruiter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private User candidate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id")
    private JobPosting jobPosting; // Nullable - có thể chat không có job cụ thể

    @Enumerated(EnumType.STRING)
    @Column(name = "job_context_type", length = 30)
    private RecruitmentJobContextType jobContextType;

    @Column(name = "job_context_id")
    private Long jobContextId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RecruitmentSessionStatus status = RecruitmentSessionStatus.CONTACTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    @Builder.Default
    private RecruitmentSessionSource sourceType = RecruitmentSessionSource.MANUAL;

    @Column(name = "match_score")
    private Integer matchScore; // AI match score nếu từ candidate search

    @Column(name = "skill_match_percent")
    private Integer skillMatchPercent; // % kỹ năng phù hợp

    @Column(name = "candidate_title")
    private String candidateTitle; // Lưu candidate's professional title tại thời điểm tạo session

    @Column(name = "candidate_avatar")
    private String candidateAvatar; // Avatar URL tại thời điểm tạo

    @Column(name = "recruiter_company")
    private String recruiterCompany; // Company name của recruiter

    @Column(name = "job_title")
    private String jobTitle; // Job title nếu có gắn job

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "unread_count_recruiter")
    @Builder.Default
    private Integer unreadCountRecruiter = 0;

    @Column(name = "unread_count_candidate")
    @Builder.Default
    private Integer unreadCountCandidate = 0;

    @Column(name = "is_archived_by_recruiter")
    @Builder.Default
    private Boolean isArchivedByRecruiter = false;

    @Column(name = "is_archived_by_candidate")
    @Builder.Default
    private Boolean isArchivedByCandidate = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
