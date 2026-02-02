package com.exe.skillverse_backend.student_learning_report_service.controller;

import com.exe.skillverse_backend.student_learning_report_service.dto.request.GenerateStudentReportRequest;
import com.exe.skillverse_backend.student_learning_report_service.dto.response.StudentLearningReportResponse;
import com.exe.skillverse_backend.student_learning_report_service.service.StudentLearningReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller cho Student Learning Report.
 * Cung cấp API để học viên xem và tạo báo cáo học tập cá nhân.
 */
@Slf4j
@RestController
@RequestMapping("/api/student/learning-report")
@RequiredArgsConstructor
@Tag(name = "Student Learning Report", description = "APIs để quản lý báo cáo học tập cá nhân của học viên")
public class StudentLearningReportController {

    private final StudentLearningReportService learningReportService;

    /**
     * Helper method to extract userId from JWT token
     */
    private Long extractUserId(Jwt jwt) {
        Object userIdClaim = jwt.getClaim("userId");
        if (userIdClaim instanceof Number) {
            return ((Number) userIdClaim).longValue();
        }
        if (userIdClaim instanceof String) {
            return Long.parseLong((String) userIdClaim);
        }
        throw new IllegalStateException("Cannot extract userId from JWT token");
    }

    @PostMapping("/generate")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tạo báo cáo học tập mới", 
               description = "Tạo báo cáo học tập cá nhân dựa trên dữ liệu học tập hiện tại. Rate limit: 1 báo cáo toàn diện mỗi 6 giờ.")
    public ResponseEntity<StudentLearningReportResponse> generateReport(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody GenerateStudentReportRequest request) {
        Long userId = extractUserId(jwt);
        log.info("Generating learning report for student: {}, type: {}", 
                userId, request.getReportType());
        
        StudentLearningReportResponse response = learningReportService.generateLearningReport(
                userId, request);
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/generate/quick")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tạo báo cáo nhanh", 
               description = "Tạo báo cáo toàn diện với cấu hình mặc định")
    public ResponseEntity<StudentLearningReportResponse> generateQuickReport(
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = extractUserId(jwt);
        log.info("Generating quick learning report for student: {}", userId);
        
        return ResponseEntity.ok(learningReportService.generateQuickReport(userId));
    }

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy lịch sử báo cáo", 
               description = "Lấy danh sách tất cả báo cáo đã tạo, sắp xếp theo thời gian mới nhất")
    public ResponseEntity<List<StudentLearningReportResponse>> getReportHistory(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Số trang (bắt đầu từ 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số lượng mỗi trang") @RequestParam(defaultValue = "10") int size) {
        Long userId = extractUserId(jwt);
        log.info("Fetching report history for student: {}, page: {}, size: {}", 
                userId, page, size);
        
        return ResponseEntity.ok(learningReportService.getReportHistory(userId, page, size));
    }

    @GetMapping("/latest")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy báo cáo mới nhất", 
               description = "Lấy báo cáo học tập gần nhất của học viên")
    public ResponseEntity<StudentLearningReportResponse> getLatestReport(
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = extractUserId(jwt);
        log.info("Fetching latest report for student: {}", userId);
        
        StudentLearningReportResponse response = learningReportService.getLatestReport(userId);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{reportId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy báo cáo theo ID", 
               description = "Lấy chi tiết một báo cáo cụ thể")
    public ResponseEntity<StudentLearningReportResponse> getReportById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String reportId) {
        Long userId = extractUserId(jwt);
        
        // Validate reportId - fix for "undefined" string issue
        if (reportId == null || reportId.trim().isEmpty() || 
            "undefined".equalsIgnoreCase(reportId) || "null".equalsIgnoreCase(reportId)) {
            log.warn("Invalid reportId received: '{}' for user: {}", reportId, userId);
            throw new com.exe.skillverse_backend.shared.exception.ApiException(
                com.exe.skillverse_backend.shared.exception.ErrorCode.BAD_REQUEST,
                "Report ID không hợp lệ. Vui lòng chọn báo cáo từ danh sách."
            );
        }
        
        Long parsedReportId;
        try {
            parsedReportId = Long.parseLong(reportId.trim());
            if (parsedReportId <= 0) {
                throw new NumberFormatException("Report ID must be positive");
            }
        } catch (NumberFormatException e) {
            log.warn("Failed to parse reportId: '{}' for user: {}", reportId, userId);
            throw new com.exe.skillverse_backend.shared.exception.ApiException(
                com.exe.skillverse_backend.shared.exception.ErrorCode.BAD_REQUEST,
                "Report ID phải là số hợp lệ. Giá trị nhận được: " + reportId
            );
        }
        
        log.info("Fetching report {} for student: {}", parsedReportId, userId);
        
        return ResponseEntity.ok(learningReportService.getReportById(userId, parsedReportId));
    }

    @GetMapping("/metrics")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy metrics hiện tại", 
               description = "Lấy các chỉ số học tập hiện tại mà không tạo báo cáo")
    public ResponseEntity<StudentLearningReportResponse.StudentMetrics> getCurrentMetrics(
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = extractUserId(jwt);
        log.info("Fetching current metrics for student: {}", userId);
        
        return ResponseEntity.ok(learningReportService.getCurrentMetrics(userId));
    }

    @GetMapping("/can-generate")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Kiểm tra có thể tạo báo cáo", 
               description = "Kiểm tra xem học viên có thể tạo báo cáo mới không (rate limit check)")
    public ResponseEntity<Map<String, Object>> canGenerateNewReport(
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = extractUserId(jwt);
        boolean canGenerate = learningReportService.canGenerateNewReport(userId);
        
        return ResponseEntity.ok(Map.of(
                "canGenerate", canGenerate,
                "cooldownHours", 6,
                "message", canGenerate 
                        ? "Bạn có thể tạo báo cáo mới" 
                        : "Vui lòng đợi để tạo báo cáo toàn diện mới"
        ));
    }

    @GetMapping("/count")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đếm số báo cáo", 
               description = "Đếm tổng số báo cáo đã tạo")
    public ResponseEntity<Map<String, Long>> countReports(
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = extractUserId(jwt);
        return ResponseEntity.ok(Map.of(
                "totalReports", learningReportService.countReports(userId)
        ));
    }

    @GetMapping("/report-types")
    @Operation(summary = "Lấy danh sách loại báo cáo", 
               description = "Lấy danh sách các loại báo cáo có thể tạo")
    public ResponseEntity<List<Map<String, String>>> getReportTypes() {
        return ResponseEntity.ok(List.of(
                Map.of("type", "COMPREHENSIVE", "name", "Báo cáo toàn diện", 
                       "description", "Phân tích đầy đủ về kỹ năng, tiến độ và đề xuất cá nhân"),
                Map.of("type", "WEEKLY_SUMMARY", "name", "Tóm tắt tuần", 
                       "description", "Tổng kết hoạt động học tập trong tuần"),
                Map.of("type", "MONTHLY_SUMMARY", "name", "Tóm tắt tháng", 
                       "description", "Tổng kết tiến bộ và thành tựu trong tháng"),
                Map.of("type", "SKILL_ASSESSMENT", "name", "Đánh giá kỹ năng", 
                       "description", "Phân tích chuyên sâu về các kỹ năng đang phát triển"),
                Map.of("type", "GOAL_TRACKING", "name", "Theo dõi mục tiêu", 
                       "description", "Đánh giá tiến độ theo từng mục tiêu học tập")
        ));
    }
}
