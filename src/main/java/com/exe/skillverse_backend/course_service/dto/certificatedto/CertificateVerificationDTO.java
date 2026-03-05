package com.exe.skillverse_backend.course_service.dto.certificatedto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateVerificationDTO {
    private String serial;
    private String courseTitle;
    private String recipientName;
    private String instructorName;
    private String instructorSignatureUrl;
    private String issuerName;
    private String type;
    private Instant issuedAt;
    private Instant revokedAt;
    private String verificationStatus;
    private String completionStatement;
    private String disclaimer;
    private String platformProof;
    private Boolean proofVerified;
}
