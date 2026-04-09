package com.exe.skillverse_backend.business_service.entity;

import com.exe.skillverse_backend.business_service.enums.SignatureStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "contract_signatures")
public class ContractSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private JobContract contract;

    @Column(name = "signed_by", nullable = false)
    private Long signedBy;

    @Column(name = "signed_by_name", length = 200)
    private String signedByName;

    @Column(name = "signed_by_role", nullable = false, length = 20)
    private String signedByRole; // "EMPLOYER" or "CANDIDATE"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SignatureStatus status = SignatureStatus.NOT_SIGNED;

    @Column(name = "signature_image_url", length = 500)
    private String signatureImageUrl;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;
}
