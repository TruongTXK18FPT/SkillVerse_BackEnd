package com.exe.skillverse_backend.course_service.dto.certificatedto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor

public class CertificateDTO {
    //Long id, Long courseId, Long userId, String serial, Instant issuedAt, Instant revokedAt
    private Long id;
    private Long courseId;
    private Long userId;
    private String serial;
    private Instant issuedAt;
    private Instant revokedAt;
}
