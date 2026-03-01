package com.exe.skillverse_backend.course_service.controller;

import com.exe.skillverse_backend.course_service.dto.certificatedto.CertificateDTO;
import com.exe.skillverse_backend.course_service.dto.certificatedto.CertificateVerificationDTO;
import com.exe.skillverse_backend.course_service.service.CertificateService;
import com.exe.skillverse_backend.shared.util.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Certificates", description = "APIs for issued course certificates")
public class CertificateController {

    private final CertificateService certificateService;

    @GetMapping("/verify/{serial}")
    @Operation(summary = "Verify a public Skillverse course certificate by serial")
    public ResponseEntity<CertificateVerificationDTO> verifyCertificate(
            @Parameter(description = "Certificate serial") @PathVariable String serial
    ) {
        log.info("Verifying certificate serial {}", serial);
        return ResponseEntity.ok(certificateService.getCertificateVerification(serial));
    }

    @GetMapping("/{certificateId}")
    @Operation(summary = "Get a certificate owned by the current user")
    public ResponseEntity<CertificateDTO> getMyCertificate(
            @Parameter(description = "Certificate ID") @PathVariable @NotNull Long certificateId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = JwtUtils.extractUserId(jwt);
        log.info("Getting certificate {} for user {}", certificateId, userId);
        return ResponseEntity.ok(certificateService.getUserCertificate(certificateId, userId));
    }
}
