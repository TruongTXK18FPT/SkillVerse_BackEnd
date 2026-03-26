package com.exe.skillverse_backend.admin_service.controller;

import com.exe.skillverse_backend.admin_service.dto.response.AdminBookingDashboardResponse;
import com.exe.skillverse_backend.admin_service.service.AdminBookingManagementService;
import com.exe.skillverse_backend.mentor_booking_service.dto.response.BookingResponse;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/bookings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Booking Management", description = "Booking operations and revenue dashboard for admins")
public class AdminBookingManagementController {

    private final AdminBookingManagementService adminBookingManagementService;

    @GetMapping("/dashboard")
    @Operation(summary = "Booking dashboard summary for admin")
    public ResponseEntity<AdminBookingDashboardResponse> getDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(adminBookingManagementService.getDashboard(fromDate, toDate));
    }

    @GetMapping
    @Operation(summary = "Paginated booking list for admin")
    public ResponseEntity<Page<BookingResponse>> getBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Pageable pageable) {
        return ResponseEntity.ok(adminBookingManagementService.getBookings(status, fromDate, toDate, pageable));
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Admin booking detail")
    public ResponseEntity<BookingResponse> getBookingDetail(@PathVariable Long bookingId) {
        return ResponseEntity.ok(adminBookingManagementService.getBookingDetail(bookingId));
    }
}
