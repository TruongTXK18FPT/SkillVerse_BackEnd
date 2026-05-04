package com.exe.skillverse_backend.business_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.business_service.entity.enums.JobApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

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
    @Column(nullable = false, length = 30)
    private JobApplicationStatus status = JobApplicationStatus.PENDING; // Default to PENDING

    @Column(name = "acceptance_message", columnDefinition = "TEXT")
    private String acceptanceMessage; // Nullable - message from recruiter when accepting

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason; // Nullable - reason from recruiter when rejecting

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt; // Nullable - timestamp when marked as REVIEWED

    @Column(name = "processed_at")
    private LocalDateTime processedAt; // Nullable - timestamp when ACCEPTED or REJECTED

    @Column(name = "interview_result", columnDefinition = "TEXT")
    private String interviewResult; // Nullable - interview notes after completion

    // Offer letter — recruiter's offer details when status = OFFER_SENT
    @Column(name = "offer_details", columnDefinition = "TEXT")
    private String offerDetails; // Nullable - additional conditions from recruiter

    // Recruiter's structured offer fields
    @Column(name = "offer_salary")
    private Long offerSalary; // Offered salary amount (VND)

    @Column(name = "offer_additional_requirements", columnDefinition = "TEXT")
    private String offerAdditionalRequirements; // Additional terms/benefits/conditions

    // Candidate's response after OFFER_SENT
    @Column(name = "candidate_offer_response", columnDefinition = "TEXT")
    private String candidateOfferResponse; // Nullable - counter-offer or acceptance message

    // Candidate's structured counter-offer fields
    @Column(name = "counter_salary_amount")
    private Long counterSalaryAmount; // Counter salary amount requested by candidate

    @Column(name = "counter_additional_requirements", columnDefinition = "TEXT")
    private String counterAdditionalRequirements; // Additional requirements from candidate

    // Offer round counter: tracks how many times the recruiter has sent an offer.
    // 0 = no offer sent yet, 1 = first offer, 2 = second (final) offer.
    // After round 2 is rejected the application is permanently REJECTED.
    @Builder.Default
    @Column(name = "offer_round")
    private Integer offerRound = 0;

    @PrePersist
    protected void onCreate() {
        appliedAt = LocalDateTime.now();
    }
}
