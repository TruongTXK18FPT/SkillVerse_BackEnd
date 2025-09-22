package com.exe.skillverse_backend.course_service.dto.certificatedto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CertificateIssueRequestDTO {
    // DTO for issuing a certificate:
    @NotNull
    private Long courseId;
}
