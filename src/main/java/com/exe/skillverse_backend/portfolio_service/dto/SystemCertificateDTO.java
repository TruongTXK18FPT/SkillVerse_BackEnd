package com.exe.skillverse_backend.portfolio_service.dto;

import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for system-issued certificates and gamification badges
 * shown in the portfolio certificates tab as "system certificates".
 *
 * source: "COURSE" | "BADGE" | "EXTERNAL"
 * imported: whether this has been imported into external_certificates
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemCertificateDTO {

    /** Unique ID within its own system table */
    private Long id;

    /** "COURSE" | "BADGE" */
    private String source;

    private String title;
    private String issuer;
    private LocalDate issueDate;
    private String credentialId;
    private String credentialUrl;
    private String category; // mapped from CertificateCategory
    private List<String> skills;
    private String imageUrl;
    private String badgeKey; // only for BADGE source
    private String badgeRarity; // only for BADGE source: common/rare/epic/legendary

    /** Whether this has been imported into external_certificates table */
    private boolean imported;
}
