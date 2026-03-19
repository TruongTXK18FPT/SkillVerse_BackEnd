package com.exe.skillverse_backend.seminar_service.service;

import com.exe.skillverse_backend.seminar_service.dto.request.SeminarCreateRequest;
import com.exe.skillverse_backend.seminar_service.dto.request.SeminarUpdateRequest;
import com.exe.skillverse_backend.seminar_service.dto.response.SeminarAnalyticsDTO;
import com.exe.skillverse_backend.seminar_service.dto.response.SeminarResponse;
import com.exe.skillverse_backend.seminar_service.dto.response.SeminarRevenueReportDTO;
import com.exe.skillverse_backend.seminar_service.dto.response.SeminarTicketResponse;
import com.exe.skillverse_backend.seminar_service.entity.SeminarStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface SeminarService {
    SeminarResponse createSeminar(SeminarCreateRequest request, MultipartFile image, String userId);

    SeminarResponse updateSeminar(Long id, SeminarUpdateRequest request, String userId);

    SeminarResponse getSeminarById(Long id, String userId); // userId optional to check ownership

    SeminarResponse getSeminarByIdForAdmin(Long id); // Admin: full access including meetingLink

    Page<SeminarResponse> getAllSeminars(List<SeminarStatus> statuses, Pageable pageable, String userId);

    Page<SeminarResponse> getMySeminars(String userId, Pageable pageable);

    void submitSeminar(Long id, String userId);

    void approveSeminar(Long id);

    void rejectSeminar(Long id);

    SeminarTicketResponse buyTicket(Long seminarId, String userId);

    Page<SeminarTicketResponse> getMyTickets(String userId, Pageable pageable);

    void updateExpiredSeminars();

    void updateStartedSeminars();

    /**
     * Get revenue report for a specific seminar (for recruiter only)
     * Shows total revenue, platform fees, net income, ticket sales, and payouts
     */
    SeminarRevenueReportDTO getSeminarRevenueReport(Long seminarId, String userId);

    /**
     * Generate PDF invoice for seminar revenue report
     * Returns PDF as byte array for download
     */
    byte[] generateSeminarRevenueInvoicePdf(Long seminarId, String userId);

    /**
     * Get public seminar analytics
     * Returns aggregate statistics and top speakers leaderboard
     * No authentication required - public endpoint
     */
    SeminarAnalyticsDTO getAnalytics();
}
