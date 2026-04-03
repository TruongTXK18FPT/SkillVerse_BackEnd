package com.exe.skillverse_backend.admin_service.service;

import com.exe.skillverse_backend.admin_service.dto.response.AdminJourneyDashboardResponse;
import com.exe.skillverse_backend.admin_service.dto.response.AdminJourneyListItemResponse;
import com.exe.skillverse_backend.admin_service.dto.response.AdminQuestionAnalyticsItemResponse;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminJourneyAnalyticsService {

    AdminJourneyDashboardResponse getDashboard();

    Page<AdminJourneyListItemResponse> listJourneys(
            String status,
            String type,
            String domain,
            String questionSource,
            Long questionBankId,
            Boolean hasRoadmap,
            Instant createdFrom,
            Instant createdTo,
            String keyword,
            Pageable pageable);

    Page<AdminQuestionAnalyticsItemResponse> listQuestionAnalytics(
            Long questionBankId,
            String difficulty,
            String skillArea,
            String source,
            Boolean isActive,
            String keyword,
            Pageable pageable);
}
