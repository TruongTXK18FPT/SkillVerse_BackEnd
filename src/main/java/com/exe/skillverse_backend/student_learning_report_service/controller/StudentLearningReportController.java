package com.exe.skillverse_backend.student_learning_report_service.controller;

import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.exe.skillverse_backend.student_learning_report_service.dto.request.GenerateStudentReportRequest;
import com.exe.skillverse_backend.student_learning_report_service.dto.response.LearningReportTimelineResponse;
import com.exe.skillverse_backend.student_learning_report_service.dto.response.StudentLearningReportResponse;
import com.exe.skillverse_backend.student_learning_report_service.service.StudentLearningReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/student/learning-report")
@RequiredArgsConstructor
@Tag(name = "Student Learning Report", description = "Deterministic learning analytics and snapshot history for students")
public class StudentLearningReportController {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final StudentLearningReportService learningReportService;

    private Long extractUserId(Jwt jwt) {
        Object userIdClaim = jwt.getClaim("userId");
        if (userIdClaim instanceof Number number) {
            return number.longValue();
        }
        if (userIdClaim instanceof String value) {
            return Long.parseLong(value);
        }
        throw new IllegalStateException("Cannot extract userId from JWT token");
    }

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Live learning report summary")
    public ResponseEntity<StudentLearningReportResponse> getSummary(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "30d") String range) {
        Long userId = extractUserId(jwt);
        return ResponseEntity.ok(learningReportService.getSummary(userId, range));
    }

    @GetMapping("/timeline")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Timeline chart data for learning report")
    public ResponseEntity<LearningReportTimelineResponse> getTimeline(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "30d") String range,
            @RequestParam(required = false) Long snapshotId) {
        Long userId = extractUserId(jwt);
        return ResponseEntity.ok(learningReportService.getTimeline(userId, range, snapshotId));
    }

    @PostMapping("/snapshots")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a learning report snapshot")
    public ResponseEntity<StudentLearningReportResponse> createSnapshot(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "30d") String range) {
        Long userId = extractUserId(jwt);
        return ResponseEntity.ok(learningReportService.createSnapshot(userId, range));
    }

    @PostMapping("/generate")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Legacy alias for create snapshot")
    public ResponseEntity<StudentLearningReportResponse> generateReport(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody(required = false) GenerateStudentReportRequest request) {
        Long userId = extractUserId(jwt);
        return ResponseEntity.ok(learningReportService.generateLearningReport(userId, request));
    }

    @PostMapping("/generate/quick")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Legacy quick snapshot alias")
    public ResponseEntity<StudentLearningReportResponse> generateQuickReport(
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = extractUserId(jwt);
        return ResponseEntity.ok(learningReportService.generateQuickReport(userId));
    }

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get saved report snapshots")
    public ResponseEntity<List<StudentLearningReportResponse>> getReportHistory(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Page number starting from 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        Long userId = extractUserId(jwt);
        return ResponseEntity.ok(learningReportService.getReportHistory(userId, page, size));
    }

    @GetMapping("/latest")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get latest saved snapshot")
    public ResponseEntity<StudentLearningReportResponse> getLatestReport(
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = extractUserId(jwt);
        StudentLearningReportResponse response = learningReportService.getLatestReport(userId);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{reportId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get saved snapshot detail by id")
    public ResponseEntity<StudentLearningReportResponse> getReportById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String reportId) {
        Long userId = extractUserId(jwt);
        Long parsedReportId = parseReportId(reportId, userId);
        return ResponseEntity.ok(learningReportService.getReportById(userId, parsedReportId));
    }

    @GetMapping("/metrics")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Legacy metrics endpoint backed by live analytics")
    public ResponseEntity<StudentLearningReportResponse.StudentMetrics> getCurrentMetrics(
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = extractUserId(jwt);
        return ResponseEntity.ok(learningReportService.getCurrentMetrics(userId));
    }

    @GetMapping("/can-generate")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Snapshots can always be generated")
    public ResponseEntity<Map<String, Object>> canGenerateNewReport(
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = extractUserId(jwt);
        boolean canGenerate = learningReportService.canGenerateNewReport(userId);
        return ResponseEntity.ok(Map.of(
                "canGenerate", canGenerate,
                "cooldownHours", 0,
                "remainingCooldownMinutes", learningReportService.getCooldownRemainingMinutes(userId),
                "nextAvailableAt", LocalDateTime.now(VN_ZONE).toString(),
                "message", "Bạn có thể lưu snapshot bất kỳ lúc nào"
        ));
    }

    @GetMapping("/count")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Count saved snapshots")
    public ResponseEntity<Map<String, Long>> countReports(@AuthenticationPrincipal Jwt jwt) {
        Long userId = extractUserId(jwt);
        return ResponseEntity.ok(Map.of("totalReports", learningReportService.countReports(userId)));
    }

    @GetMapping("/report-types")
    @Operation(summary = "Legacy report-types endpoint")
    public ResponseEntity<List<Map<String, Object>>> getReportTypes() {
        return ResponseEntity.ok(List.of(Map.of(
                "type", "COMPREHENSIVE",
                "name", "Báo cáo học tập",
                "description", "Dashboard thống kê realtime và snapshot lịch sử",
                "cooldownHours", 0
        )));
    }

    private Long parseReportId(String reportId, Long userId) {
        if (reportId == null || reportId.trim().isEmpty()
                || "undefined".equalsIgnoreCase(reportId)
                || "null".equalsIgnoreCase(reportId)) {
            log.warn("Invalid reportId received: '{}' for user: {}", reportId, userId);
            throw new ApiException(ErrorCode.BAD_REQUEST, "Report ID không hợp lệ.");
        }

        try {
            long parsed = Long.parseLong(reportId.trim());
            if (parsed <= 0) {
                throw new NumberFormatException("Report ID must be positive");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            log.warn("Failed to parse reportId: '{}' for user: {}", reportId, userId);
            throw new ApiException(ErrorCode.BAD_REQUEST, "Report ID phải là số hợp lệ. Giá trị nhận được: " + reportId);
        }
    }
}
