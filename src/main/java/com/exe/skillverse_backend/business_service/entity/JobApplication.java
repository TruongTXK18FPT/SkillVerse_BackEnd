package com.exe.skillverse_backend.business_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.business_service.entity.enums.JobApplicationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_applications", uniqueConstraints = {
        @UniqueConstraint(name = "uk_job_application_user_job", columnNames = { "user_id", "job_posting_id" })
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "cover_letter", columnDefinition = "TEXT")
    private String coverLetter; // Nullable - optional cover letter

    @Column(name = "applied_at", nullable = false, updatable = false)
    private LocalDateTime appliedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobApplicationStatus status = JobApplicationStatus.PENDING; // Default to PENDING

    @Column(name = "acceptance_message", columnDefinition = "TEXT")
    private String acceptanceMessage; // Nullable - message from recruiter when accepting

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason; // Nullable - reason from recruiter when rejecting

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt; // Nullable - timestamp when marked as REVIEWED

    @Column(name = "processed_at")
    private LocalDateTime processedAt; // Nullable - timestamp when ACCEPTED or REJECTED

    @PrePersist
    protected void onCreate() {
        appliedAt = LocalDateTime.now();
    }
}
