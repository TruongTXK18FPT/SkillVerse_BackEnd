package com.exe.skillverse_backend.journey_service.controller;

import com.exe.skillverse_backend.journey_service.dto.response.JourneyVerificationDetailResponse;
import com.exe.skillverse_backend.journey_service.service.PublicJourneyVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/journeys")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Public Journey Verification", description = "Public endpoints for roadmap journey verification details")
public class PublicJourneyVerificationController {

    private final PublicJourneyVerificationService verificationService;

    @GetMapping("/{journeyId}/verification-details")
    @Operation(summary = "Public: Lấy chi tiết quá trình học và xác thực kỹ năng qua Roadmap Journey")
    public ResponseEntity<JourneyVerificationDetailResponse> getVerificationDetails(
            @PathVariable Long journeyId) {
        return ResponseEntity.ok(verificationService.getVerificationDetails(journeyId));
    }
}
