package com.exe.skillverse_backend.admin_service.controller;

import com.exe.skillverse_backend.admin_service.dto.response.AdminJourneyDashboardResponse;
import com.exe.skillverse_backend.admin_service.dto.response.AdminJourneyListItemResponse;
import com.exe.skillverse_backend.admin_service.dto.response.AdminQuestionAnalyticsItemResponse;
import com.exe.skillverse_backend.admin_service.service.AdminJourneyAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/journeys")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Journey Analytics", description = "Admin analytics and management endpoints for journeys, assessments, and question banks")
@PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
public class AdminJourneyAnalyticsController {

    private final AdminJourneyAnalyticsService adminJourneyAnalyticsService;

    @GetMapping("/dashboard")
    @Operation(summary = "Get journey and question bank analytics dashboard")
    public ResponseEntity<AdminJourneyDashboardResponse> getDashboard() {
        return ResponseEntity.ok(adminJourneyAnalyticsService.getDashboard());
    }

    @GetMapping("/list")
    @Operation(summary = "List journeys with admin filters")
    public ResponseEntity<Page<AdminJourneyListItemResponse>> listJourneys(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String questionSource,
            @RequestParam(required = false) Long questionBankId,
            @RequestParam(required = false) Boolean hasRoadmap,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 12) Pageable pageable) {
        return ResponseEntity.ok(adminJourneyAnalyticsService.listJourneys(
                status,
                type,
                domain,
                questionSource,
                questionBankId,
                hasRoadmap,
                createdFrom,
                createdTo,
                keyword,
                pageable));
    }

    @GetMapping("/questions")
    @Operation(summary = "List question analytics with admin filters")
    public ResponseEntity<Page<AdminQuestionAnalyticsItemResponse>> listQuestionAnalytics(
            @RequestParam(required = false) Long questionBankId,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String skillArea,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 12) Pageable pageable) {
        return ResponseEntity.ok(adminJourneyAnalyticsService.listQuestionAnalytics(
                questionBankId,
                difficulty,
                skillArea,
                source,
                isActive,
                keyword,
                pageable));
    }
}
