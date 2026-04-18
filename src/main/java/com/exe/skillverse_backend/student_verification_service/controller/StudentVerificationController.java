package com.exe.skillverse_backend.student_verification_service.controller;

import com.exe.skillverse_backend.student_verification_service.dto.request.VerifyStudentVerificationOtpRequest;
import com.exe.skillverse_backend.student_verification_service.dto.response.StudentVerificationDetailResponse;
import com.exe.skillverse_backend.student_verification_service.dto.response.StudentVerificationEligibilityResponse;
import com.exe.skillverse_backend.student_verification_service.dto.response.StudentVerificationStartResponse;
import com.exe.skillverse_backend.student_verification_service.service.StudentVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/student-verifications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Student Verification", description = "Student endpoints for student premium verification")
public class StudentVerificationController {

    private final StudentVerificationService studentVerificationService;

    // [Nghiep vu] Student nop email truong + anh the de he thong tao request va gui OTP xac minh.
    @PostMapping(value = "/requests", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Start student verification request")
    public ResponseEntity<StudentVerificationStartResponse> startVerification(
            @RequestParam("schoolEmail") String schoolEmail,
            @RequestParam("studentCardImage") MultipartFile studentCardImage,
            Authentication authentication) {

        Long userId = extractUserId(authentication);
        StudentVerificationStartResponse response =
                studentVerificationService.startVerification(userId, schoolEmail, studentCardImage);

        return ResponseEntity.ok(response);
    }

    // [Nghiep vu] Student duoc gui lai OTP neu chua xac minh trong lan truoc.
    @PostMapping("/requests/{requestId}/resend-otp")
    @Operation(summary = "Resend OTP for a pending student verification request")
    public ResponseEntity<StudentVerificationStartResponse> resendOtp(
            @PathVariable Long requestId,
            Authentication authentication) {

        Long userId = extractUserId(authentication);
        StudentVerificationStartResponse response = studentVerificationService.resendOtp(userId, requestId);
        return ResponseEntity.ok(response);
    }

    // [Nghiep vu] Student nhap OTP hop le de dua request sang trang thai cho admin review.
    @PostMapping("/requests/{requestId}/verify-otp")
    @Operation(summary = "Verify school email OTP and submit for admin review")
    public ResponseEntity<StudentVerificationDetailResponse> verifyOtpAndSubmit(
            @PathVariable Long requestId,
            @Valid @RequestBody VerifyStudentVerificationOtpRequest request,
            Authentication authentication) {

        Long userId = extractUserId(authentication);
        StudentVerificationDetailResponse response =
                studentVerificationService.verifyOtpAndSubmit(userId, requestId, request.getOtp());

        return ResponseEntity.ok(response);
    }

    // [Nghiep vu] Student xem nhanh request moi nhat de biet dang cho OTP, review hay da duoc duyet.
    @GetMapping("/requests/latest")
    @Operation(summary = "Get current user's latest student verification request")
    public ResponseEntity<StudentVerificationDetailResponse> getLatestMyRequest(Authentication authentication) {
        Long userId = extractUserId(authentication);
        return ResponseEntity.ok(studentVerificationService.getLatestMyRequest(userId));
    }

    // [Nghiep vu] Student xem chi tiet theo requestId de theo doi tien do duyet thu cong.
    @GetMapping("/requests/{requestId}")
    @Operation(summary = "Get current user's student verification request detail")
    public ResponseEntity<StudentVerificationDetailResponse> getMyRequestDetail(
            @PathVariable Long requestId,
            Authentication authentication) {

        Long userId = extractUserId(authentication);
        return ResponseEntity.ok(studentVerificationService.getMyRequestDetail(userId, requestId));
    }

    // [Nghiep vu] API nay duoc frontend premium goi de biet co mo khoa goi STUDENT_PACK hay khong.
    @GetMapping("/eligibility")
    @Operation(summary = "Check student premium eligibility")
    public ResponseEntity<StudentVerificationEligibilityResponse> getMyEligibility(Authentication authentication) {
        Long userId = extractUserId(authentication);
        return ResponseEntity.ok(studentVerificationService.getMyEligibility(userId));
    }

    // [Nghiep vu] Student xem anh the luu local trong request cua chinh minh.
    @GetMapping("/requests/{requestId}/image")
    @Operation(summary = "Get locally stored student card image for current user")
    public ResponseEntity<org.springframework.core.io.Resource> getMyLocalImage(
            @Parameter(description = "Student verification request ID") @PathVariable Long requestId,
            Authentication authentication) {

        Long userId = extractUserId(authentication);
        StudentVerificationService.LocalImagePayload payload =
                studentVerificationService.getLocalImageForUser(userId, requestId);

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (payload.contentType() != null && !payload.contentType().isBlank()) {
            mediaType = MediaType.parseMediaType(payload.contentType());
        }

        ContentDisposition contentDisposition = ContentDisposition.inline()
                .filename(payload.fileName())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(mediaType)
                .body(payload.resource());
    }

    private Long extractUserId(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();

        String userIdClaim = jwt.getClaimAsString("userId");
        if (userIdClaim != null && !userIdClaim.isBlank()) {
            return Long.parseLong(userIdClaim);
        }

        return Long.valueOf(jwt.getSubject());
    }
}
