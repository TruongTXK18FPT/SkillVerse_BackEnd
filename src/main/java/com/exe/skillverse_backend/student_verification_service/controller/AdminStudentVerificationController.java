package com.exe.skillverse_backend.student_verification_service.controller;

import com.exe.skillverse_backend.student_verification_service.dto.request.ApproveStudentVerificationRequest;
import com.exe.skillverse_backend.student_verification_service.dto.request.ExpireStudentVerificationEmailRequest;
import com.exe.skillverse_backend.student_verification_service.dto.request.RejectStudentVerificationRequest;
import com.exe.skillverse_backend.student_verification_service.dto.response.StudentVerificationDetailResponse;
import com.exe.skillverse_backend.student_verification_service.dto.response.StudentVerificationListItemResponse;
import com.exe.skillverse_backend.student_verification_service.enums.StudentVerificationStatus;
import com.exe.skillverse_backend.student_verification_service.service.StudentVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/student-verifications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Student Verification", description = "Admin endpoints for reviewing student verification requests")
@PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN')")
public class AdminStudentVerificationController {

    private final StudentVerificationService studentVerificationService;

    // [Nghiep vu] Admin list request theo trang thai de chia queue review ro rang.
    @GetMapping("/requests")
    @Operation(summary = "List student verification requests for admin")
    public ResponseEntity<Page<StudentVerificationListItemResponse>> getRequests(
            @RequestParam(required = false) StudentVerificationStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<StudentVerificationListItemResponse> page =
                studentVerificationService.getRequestsForAdmin(status, pageable);
        return ResponseEntity.ok(page);
    }

    // [Nghiep vu] Admin xem full thong tin request va anh the truoc khi quyet dinh.
    @GetMapping("/requests/{requestId}")
    @Operation(summary = "Get request detail for admin review")
    public ResponseEntity<StudentVerificationDetailResponse> getRequestDetail(
            @Parameter(description = "Student verification request ID") @PathVariable Long requestId) {

        return ResponseEntity.ok(studentVerificationService.getRequestDetailForAdmin(requestId));
    }

    // [Nghiep vu] Approve mo quyen mua student premium cho user.
    @PostMapping("/requests/{requestId}/approve")
    @Operation(summary = "Approve a pending student verification request")
    public ResponseEntity<StudentVerificationDetailResponse> approveRequest(
            @PathVariable Long requestId,
            @Valid @RequestBody(required = false) ApproveStudentVerificationRequest request,
            Authentication authentication) {

        Long adminId = extractUserId(authentication);
        String reviewNote = request == null ? null : request.getReviewNote();

        StudentVerificationDetailResponse response =
                studentVerificationService.approveRequest(adminId, requestId, reviewNote);

        return ResponseEntity.ok(response);
    }

    // [Nghiep vu] Reject buoc admin ghi ly do de student biet can sua gi cho lan nop lai.
    @PostMapping("/requests/{requestId}/reject")
    @Operation(summary = "Reject a pending student verification request")
    public ResponseEntity<StudentVerificationDetailResponse> rejectRequest(
            @PathVariable Long requestId,
            @Valid @RequestBody RejectStudentVerificationRequest request,
            Authentication authentication) {

        Long adminId = extractUserId(authentication);
        StudentVerificationDetailResponse response =
                studentVerificationService.rejectRequest(adminId, requestId, request.getReason());

        return ResponseEntity.ok(response);
    }

    // [Nghiep vu] Admin co the danh dau het han email truong da duoc duyet de mo khoa cho tai khoan khac su dung.
    @PostMapping("/requests/{requestId}/expire")
    @Operation(summary = "Expire an approved student verification request and release school email binding")
    public ResponseEntity<StudentVerificationDetailResponse> expireRequest(
            @PathVariable Long requestId,
            @Valid @RequestBody ExpireStudentVerificationEmailRequest request,
            Authentication authentication) {

        Long adminId = extractUserId(authentication);
        StudentVerificationDetailResponse response =
                studentVerificationService.expireApprovedEmail(adminId, requestId, request.getReason());

        return ResponseEntity.ok(response);
    }

    // [Nghiep vu] Admin xem anh local cua request de review truc quan.
    @GetMapping("/requests/{requestId}/image")
    @Operation(summary = "Get locally stored student card image for admin")
    public ResponseEntity<org.springframework.core.io.Resource> getLocalImage(
            @PathVariable Long requestId) {

        StudentVerificationService.LocalImagePayload payload =
                studentVerificationService.getLocalImageForAdmin(requestId);

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
