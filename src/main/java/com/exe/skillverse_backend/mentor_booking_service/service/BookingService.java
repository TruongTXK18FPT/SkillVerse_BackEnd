package com.exe.skillverse_backend.mentor_booking_service.service;

import com.exe.skillverse_backend.mentor_booking_service.dto.request.CreateBookingIntentRequest;
import com.exe.skillverse_backend.mentor_booking_service.dto.response.BookingResponse;
import com.exe.skillverse_backend.mentor_booking_service.entity.Booking;
import com.exe.skillverse_backend.payment_service.dto.response.CreatePaymentResponse;
import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import com.exe.skillverse_backend.payment_service.event.PaymentSuccessEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BookingService {
    void handlePaymentSuccess(PaymentSuccessEvent event);
    CreatePaymentResponse createBookingIntent(Long learnerId, CreateBookingIntentRequest request);
    Booking createBookingWithWallet(Long learnerId, CreateBookingIntentRequest request);
    Booking getBookingIfParticipant(Long userId, Long bookingId);
    BookingResponse getBookingDetail(Long userId, Long bookingId);
    Booking createPendingFromPayment(PaymentTransaction transaction);
    Booking approve(Long mentorId, Long bookingId);
    Booking reject(Long mentorId, Long bookingId, String reason);
    Booking startMeeting(Long mentorId, Long bookingId);
    Booking complete(Long mentorId, Long bookingId);
    Booking learnerConfirmComplete(Long learnerId, Long bookingId);
    Booking cancelByLearner(Long learnerId, Long bookingId);
    void rateAfterSession(Long learnerId, Long bookingId, Integer stars, String comment, String skillEndorsed);
    Page<BookingResponse> getUserBookings(Long userId, boolean mentorView, Pageable pageable);
    List<BookingResponse> getMentorBookingsForDateRange(Long mentorId, LocalDateTime from, LocalDateTime to);
}