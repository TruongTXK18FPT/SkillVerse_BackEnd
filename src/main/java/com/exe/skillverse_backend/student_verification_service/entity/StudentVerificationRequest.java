package com.exe.skillverse_backend.student_verification_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.student_verification_service.enums.StudentVerificationStatus;
import com.exe.skillverse_backend.student_verification_service.enums.StudentVerificationStorageProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "student_verification_requests", indexes = {
        @Index(name = "idx_svr_user_id", columnList = "user_id"),
        @Index(name = "idx_svr_status", columnList = "status"),
        @Index(name = "idx_svr_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentVerificationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "school_email", nullable = false, length = 255)
    private String schoolEmail;

    @Column(name = "school_domain", nullable = false, length = 255)
    private String schoolDomain;

    @Builder.Default
    @Column(name = "email_domain_valid", nullable = false)
    private Boolean emailDomainValid = Boolean.FALSE;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 40)
    private StudentVerificationStatus status = StudentVerificationStatus.EMAIL_OTP_PENDING;

    @Column(name = "otp_hash", length = 128)
    private String otpHash;

    @Column(name = "otp_expires_at")
    private LocalDateTime otpExpiresAt;

    @Builder.Default
    @Column(name = "otp_attempts", nullable = false)
    private Integer otpAttempts = 0;

    @Column(name = "otp_verified_at")
    private LocalDateTime otpVerifiedAt;

    @Column(name = "last_otp_sent_at")
    private LocalDateTime lastOtpSentAt;

    @Column(name = "temp_image_path", columnDefinition = "TEXT")
    private String tempImagePath;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "image_storage_path", columnDefinition = "TEXT")
    private String imageStoragePath;

    @Column(name = "image_public_id", length = 255)
    private String imagePublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_provider", length = 20)
    private StudentVerificationStorageProvider imageProvider;

    @Column(name = "uploaded_file_name", length = 255)
    private String uploadedFileName;

    @Column(name = "uploaded_content_type", length = 100)
    private String uploadedContentType;

    @Column(name = "uploaded_file_size")
    private Long uploadedFileSize;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
