package com.exe.skillverse_backend.student_verification_service.dto.response;

import com.exe.skillverse_backend.student_verification_service.enums.StudentVerificationStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StudentVerificationDetailResponse {
    private Long id;
    private Long userId;
    private String userEmail;
    private String userFullName;
    private String schoolEmail;
    private String schoolDomain;
    private Boolean emailDomainValid;
    private StudentVerificationStatus status;
    private LocalDateTime otpExpiresAt;
    private LocalDateTime otpVerifiedAt;
    private String imageUrl;
    private String uploadedFileName;
    private String uploadedContentType;
    private Long uploadedFileSize;
    private String reviewNote;
    private String rejectionReason;
    private Long reviewedById;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
