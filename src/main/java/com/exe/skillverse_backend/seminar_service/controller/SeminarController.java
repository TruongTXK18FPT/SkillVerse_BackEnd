package com.exe.skillverse_backend.seminar_service.controller;

import com.exe.skillverse_backend.seminar_service.dto.request.SeminarCreateRequest;
import com.exe.skillverse_backend.seminar_service.dto.request.SeminarUpdateRequest;
import com.exe.skillverse_backend.seminar_service.dto.response.SeminarAnalyticsDTO;
import com.exe.skillverse_backend.seminar_service.dto.response.SeminarResponse;
import com.exe.skillverse_backend.seminar_service.dto.response.SeminarRevenueReportDTO;
import com.exe.skillverse_backend.seminar_service.dto.response.SeminarTicketResponse;
import com.exe.skillverse_backend.seminar_service.entity.SeminarStatus;
import com.exe.skillverse_backend.seminar_service.service.SeminarService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seminars")
@Slf4j
@RequiredArgsConstructor
public class SeminarController {

    private final SeminarService seminarService;

    // --- Public / User ---

    @GetMapping("/analytics")
    public ResponseEntity<SeminarAnalyticsDTO> getAnalytics() {
        try {
            SeminarAnalyticsDTO analytics = seminarService.getAnalytics();
            return ResponseEntity.ok(analytics);
        } catch (Exception e) {
            log.error("Failed to retrieve seminar analytics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping
    public ResponseEntity<Page<SeminarResponse>> getAllSeminars(
            @RequestParam(required = false) List<SeminarStatus> statuses,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size, // User asked for 6 per page
            Authentication authentication) {
        String userId = (authentication != null) ? authentication.getName() : null;
        Pageable pageable = PageRequest.of(page, size, Sort.by("startTime").descending());
        // Default to ACCEPTED + OPEN if no statuses provided (for public listing)
        List<SeminarStatus> statusList = (statuses != null && !statuses.isEmpty())
                ? statuses
                : List.of(SeminarStatus.ACCEPTED, SeminarStatus.OPEN);
        return ResponseEntity.ok(seminarService.getAllSeminars(statusList, pageable, userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SeminarResponse> getSeminarById(
            @PathVariable Long id,
            Authentication authentication) {
        String userId = (authentication != null) ? authentication.getName() : null;
        return ResponseEntity.ok(seminarService.getSeminarById(id, userId));
    }

    @PostMapping("/{id}/buy")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SeminarTicketResponse> buyTicket(
            @PathVariable Long id,
            Authentication authentication) {
        String userId = authentication.getName();
        return ResponseEntity.ok(seminarService.buyTicket(id, userId));
    }

    @GetMapping("/my-tickets")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<SeminarTicketResponse>> getMyTickets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        String userId = authentication.getName();
        Pageable pageable = PageRequest.of(page, size, Sort.by("purchasedAt").descending());
        return ResponseEntity.ok(seminarService.getMyTickets(userId, pageable));
    }

    // --- Recruiter ---

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    public ResponseEntity<SeminarResponse> createSeminar(
            @RequestPart("data") @Valid SeminarCreateRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image,
            Authentication authentication) {
        String userId = authentication.getName();
        return ResponseEntity.ok(seminarService.createSeminar(request, image, userId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    public ResponseEntity<SeminarResponse> updateSeminar(
            @PathVariable Long id,
            @Valid @RequestBody SeminarUpdateRequest request,
            Authentication authentication) {
        String userId = authentication.getName();
        return ResponseEntity.ok(seminarService.updateSeminar(id, request, userId));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    public ResponseEntity<Void> submitSeminar(
            @PathVariable Long id,
            Authentication authentication) {
        String userId = authentication.getName();
        seminarService.submitSeminar(id, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/my-seminars")
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    public ResponseEntity<Page<SeminarResponse>> getMySeminars(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        String userId = authentication.getName();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(seminarService.getMySeminars(userId, pageable));
    }

    // --- Admin ---

    @GetMapping("/{id}/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SeminarResponse> getSeminarByIdForAdmin(@PathVariable Long id) {
        return ResponseEntity.ok(seminarService.getSeminarByIdForAdmin(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> approveSeminar(@PathVariable Long id) {
        seminarService.approveSeminar(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> rejectSeminar(@PathVariable Long id) {
        seminarService.rejectSeminar(id);
        return ResponseEntity.ok().build();
    }

    // --- Revenue Report (Recruiter only) ---

    /**
     * Get revenue report for a seminar (JSON response)
     * Shows total revenue, platform fees, net income, ticket sales, and payout
     * history
     */
    @GetMapping("/{id}/revenue-report")
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    public ResponseEntity<SeminarRevenueReportDTO> getSeminarRevenueReport(
            @PathVariable Long id,
            Authentication authentication) {
        String userId = authentication.getName();
        return ResponseEntity.ok(seminarService.getSeminarRevenueReport(id, userId));
    }

    /**
     * Download revenue report as PDF invoice
     */
    @GetMapping("/{id}/revenue-invoice")
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    public ResponseEntity<byte[]> downloadSeminarRevenueInvoice(
            @PathVariable Long id,
            Authentication authentication) {
        String userId = authentication.getName();
        byte[] pdfBytes = seminarService.generateSeminarRevenueInvoicePdf(id, userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "seminar_revenue_" + id + ".pdf");
        headers.setContentLength(pdfBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
}
