package com.exe.skillverse_backend.course_service.dto.certificatedto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor

public class CertificateDTO {
    private Long id;
    private Long courseId;
    private Long userId;
    private String courseTitle;
    private String recipientName;
    private String instructorName;
    private String instructorSignatureUrl;
    private String issuerName;
    private String type;
    private String serial;
    private Instant issuedAt;
    private Instant revokedAt;
    private String criteria;
    private String platformProof;
    private Boolean proofVerified;
}
