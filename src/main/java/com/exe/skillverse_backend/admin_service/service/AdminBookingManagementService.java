package com.exe.skillverse_backend.admin_service.service;

import com.exe.skillverse_backend.admin_service.dto.response.AdminBookingDashboardResponse;
import com.exe.skillverse_backend.mentor_booking_service.dto.response.BookingResponse;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminBookingManagementService {
    AdminBookingDashboardResponse getDashboard(LocalDate fromDate, LocalDate toDate);

    Page<BookingResponse> getBookings(
            BookingStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable);

    BookingResponse getBookingDetail(Long bookingId);
}
