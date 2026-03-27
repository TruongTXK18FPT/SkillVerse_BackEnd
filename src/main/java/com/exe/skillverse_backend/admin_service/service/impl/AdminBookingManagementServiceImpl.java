package com.exe.skillverse_backend.admin_service.service.impl;

import com.exe.skillverse_backend.admin_service.dto.response.AdminBookingDashboardResponse;
import com.exe.skillverse_backend.admin_service.service.AdminBookingManagementService;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.mentor_booking_service.dto.response.BookingResponse;
import com.exe.skillverse_backend.mentor_booking_service.entity.Booking;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingDispute;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingDisputeRepository;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingRepository;
import com.exe.skillverse_backend.mentor_service.entity.MentorProfile;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.user_service.service.UserProfileService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminBookingManagementServiceImpl implements AdminBookingManagementService {

    private static final BigDecimal MENTOR_SHARE_RATE = new BigDecimal("0.80");

    private final BookingRepository bookingRepository;
    private final BookingDisputeRepository disputeRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final UserProfileService userProfileService;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminBookingDashboardResponse getDashboard(LocalDate fromDate, LocalDate toDate) {
        LocalDateTime from = toStartOfDay(fromDate);
        LocalDateTime to = toEndOfDay(toDate);

        List<Booking> source = bookingRepository.findAll();
        List<Booking> bookings = source.stream()
                .filter(booking -> inDateRange(booking, from, to))
                .toList();

        Map<Long, BookingDispute> disputesByBookingId = disputeRepository.findAll().stream()
                .filter(dispute -> dispute.getBookingId() != null)
                .collect(Collectors.toMap(
                        BookingDispute::getBookingId,
                        dispute -> dispute,
                        (left, right) -> left));

        EnumMap<BookingStatus, Long> statusCounts = new EnumMap<>(BookingStatus.class);
        for (BookingStatus status : BookingStatus.values()) {
            statusCounts.put(status, 0L);
        }

        BigDecimal grossBookingValue = BigDecimal.ZERO;
        BigDecimal learnerNetSpend = BigDecimal.ZERO;
        BigDecimal learnerRefunded = BigDecimal.ZERO;
        BigDecimal mentorPayout = BigDecimal.ZERO;
        BigDecimal adminCommission = BigDecimal.ZERO;
        BigDecimal escrowHolding = BigDecimal.ZERO;

        for (Booking booking : bookings) {
            statusCounts.computeIfPresent(booking.getStatus(), (key, value) -> value + 1);

            BigDecimal price = safeMoney(booking.getPriceVnd());
            grossBookingValue = grossBookingValue.add(price);

            RevenueSlice slice = calculateRevenueSlice(booking, disputesByBookingId.get(booking.getId()));
            learnerNetSpend = learnerNetSpend.add(slice.learnerSpend());
            learnerRefunded = learnerRefunded.add(slice.refunded());
            mentorPayout = mentorPayout.add(slice.mentorPayout());
            adminCommission = adminCommission.add(slice.adminCommission());
            escrowHolding = escrowHolding.add(slice.escrowHolding());
        }

        List<AdminBookingDashboardResponse.RevenuePoint> monthlyRevenue = buildMonthlyRevenue(bookings, disputesByBookingId);
        List<AdminBookingDashboardResponse.StatusPoint> statusBreakdown = statusCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Enum::name)))
                .map(entry -> AdminBookingDashboardResponse.StatusPoint.builder()
                        .status(entry.getKey().name())
                        .count(entry.getValue())
                        .build())
                .toList();

        long pendingBookings = statusCounts.getOrDefault(BookingStatus.PENDING, 0L);
        long activeBookings = statusCounts.getOrDefault(BookingStatus.CONFIRMED, 0L)
                + statusCounts.getOrDefault(BookingStatus.ONGOING, 0L)
                + statusCounts.getOrDefault(BookingStatus.PENDING_COMPLETION, 0L);
        long completedBookings = statusCounts.getOrDefault(BookingStatus.COMPLETED, 0L);
        long disputedBookings = statusCounts.getOrDefault(BookingStatus.DISPUTED, 0L);
        long refundedBookings = statusCounts.getOrDefault(BookingStatus.REFUNDED, 0L)
                + statusCounts.getOrDefault(BookingStatus.CANCELLED, 0L)
                + statusCounts.getOrDefault(BookingStatus.REJECTED, 0L);

        return AdminBookingDashboardResponse.builder()
                .totalBookings((long) bookings.size())
                .pendingBookings(pendingBookings)
                .activeBookings(activeBookings)
                .completedBookings(completedBookings)
                .disputedBookings(disputedBookings)
                .refundedBookings(refundedBookings)
                .grossBookingValueVnd(grossBookingValue)
                .learnerNetSpendVnd(learnerNetSpend)
                .learnerRefundedVnd(learnerRefunded)
                .mentorPayoutVnd(mentorPayout)
                .adminCommissionVnd(adminCommission)
                .escrowHoldingVnd(escrowHolding)
                .monthlyRevenue(monthlyRevenue)
                .statusBreakdown(statusBreakdown)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookingResponse> getBookings(
            BookingStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable) {
        Pageable sortedPageable = withDefaultSort(pageable);
        LocalDateTime from = toStartOfDay(fromDate);
        LocalDateTime to = toEndOfDay(toDate);

        Page<Booking> page;
        if (status != null && from != null && to != null) {
            page = bookingRepository.findByStatusAndStartTimeBetweenOrderByStartTimeDesc(status, from, to, sortedPageable);
        } else if (status != null) {
            page = bookingRepository.findByStatusOrderByStartTimeDesc(status, sortedPageable);
        } else if (from != null && to != null) {
            page = bookingRepository.findByStartTimeBetweenOrderByStartTimeDesc(from, to, sortedPageable);
        } else {
            page = bookingRepository.findAll(sortedPageable);
        }

        return page.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBookingDetail(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking khĂ´ng tá»“n táº¡i"));
        return toResponse(booking);
    }

    private List<AdminBookingDashboardResponse.RevenuePoint> buildMonthlyRevenue(
            List<Booking> bookings,
            Map<Long, BookingDispute> disputesByBookingId) {
        LocalDate baseDate = bookings.stream()
                .map(booking -> booking.getStartTime().toLocalDate())
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());
        YearMonth startMonth = YearMonth.from(baseDate);
        YearMonth currentMonth = YearMonth.now();
        int totalMonths = Math.max(6, (currentMonth.getYear() - startMonth.getYear()) * 12
                + currentMonth.getMonthValue() - startMonth.getMonthValue() + 1);

        List<AdminBookingDashboardResponse.RevenuePoint> points = new ArrayList<>();
        YearMonth firstPointMonth = currentMonth.minusMonths(totalMonths - 1L);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/yy");

        for (int index = 0; index < totalMonths; index++) {
            YearMonth month = firstPointMonth.plusMonths(index);
            BigDecimal gross = BigDecimal.ZERO;
            BigDecimal learnerSpend = BigDecimal.ZERO;
            BigDecimal refunded = BigDecimal.ZERO;
            BigDecimal mentorPayout = BigDecimal.ZERO;
            BigDecimal adminCommission = BigDecimal.ZERO;

            for (Booking booking : bookings) {
                if (!YearMonth.from(booking.getStartTime()).equals(month)) {
                    continue;
                }

                gross = gross.add(safeMoney(booking.getPriceVnd()));
                RevenueSlice slice = calculateRevenueSlice(booking, disputesByBookingId.get(booking.getId()));
                learnerSpend = learnerSpend.add(slice.learnerSpend());
                refunded = refunded.add(slice.refunded());
                mentorPayout = mentorPayout.add(slice.mentorPayout());
                adminCommission = adminCommission.add(slice.adminCommission());
            }

            points.add(AdminBookingDashboardResponse.RevenuePoint.builder()
                    .label(month.format(formatter))
                    .grossValueVnd(gross)
                    .learnerSpendVnd(learnerSpend)
                    .refundedVnd(refunded)
                    .mentorPayoutVnd(mentorPayout)
                    .adminCommissionVnd(adminCommission)
                    .build());
        }

        return points;
    }

    private RevenueSlice calculateRevenueSlice(Booking booking, BookingDispute dispute) {
        BigDecimal price = safeMoney(booking.getPriceVnd());
        BookingStatus status = booking.getStatus();

        if (status == BookingStatus.PENDING
                || status == BookingStatus.CONFIRMED
                || status == BookingStatus.ONGOING
                || status == BookingStatus.PENDING_COMPLETION
                || status == BookingStatus.DISPUTED) {
            return new RevenueSlice(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, price);
        }

        if (status == BookingStatus.REFUNDED
                || status == BookingStatus.CANCELLED
                || status == BookingStatus.REJECTED) {
            return new RevenueSlice(BigDecimal.ZERO, price, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        if (status == BookingStatus.COMPLETED && dispute != null && dispute.getResolution() != null) {
            return new RevenueSlice(
                    safeMoney(dispute.getReleasedAmount()),
                    safeMoney(dispute.getRefundAmount()),
                    safeMoney(dispute.getMentorPayoutAmount()),
                    safeMoney(dispute.getAdminCommissionAmount()),
                    BigDecimal.ZERO);
        }

        if (status == BookingStatus.COMPLETED) {
            BigDecimal mentorPayout = price.multiply(MENTOR_SHARE_RATE).setScale(2, RoundingMode.HALF_UP);
            BigDecimal adminCommission = price.subtract(mentorPayout);
            return new RevenueSlice(price, BigDecimal.ZERO, mentorPayout, adminCommission, BigDecimal.ZERO);
        }

        return new RevenueSlice(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private BookingResponse toResponse(Booking booking) {
        String mentorName = booking.getMentor().getFullName();
        String mentorAvatar = booking.getMentor().getAvatarUrl();
        String learnerName = booking.getLearner().getFullName();
        String learnerAvatar = booking.getLearner().getAvatarUrl();

        try {
            MentorProfile mentorProfile = mentorProfileRepository.findByUserId(booking.getMentor().getId()).orElse(null);
            if (mentorProfile != null) {
                if (mentorProfile.getFullName() != null && !mentorProfile.getFullName().isBlank()) {
                    mentorName = mentorProfile.getFullName();
                }
                if (mentorProfile.getAvatarUrl() != null && !mentorProfile.getAvatarUrl().isBlank()) {
                    mentorAvatar = mentorProfile.getAvatarUrl();
                }
            }

            if (userProfileService.hasProfile(booking.getLearner().getId())) {
                learnerName = userProfileService.getProfile(booking.getLearner().getId()).getFullName();
                learnerAvatar = userProfileService.getProfile(booking.getLearner().getId()).getAvatarMediaUrl();
            }
        } catch (Exception exception) {
            log.warn("Failed to enrich booking {} for admin view: {}", booking.getId(), exception.getMessage());
        }

        return BookingResponse.builder()
                .id(booking.getId())
                .mentorId(booking.getMentor().getId())
                .learnerId(booking.getLearner().getId())
                .createdAt(booking.getCreatedAt())
                .startTime(booking.getStartTime())
                .endTime(booking.getEndTime())
                .durationMinutes(booking.getDurationMinutes())
                .status(booking.getStatus())
                .priceVnd(booking.getPriceVnd())
                .meetingLink(booking.getMeetingLink())
                .paymentReference(booking.getPaymentReference())
                .confirmedByLearner(booking.getConfirmedByLearner())
                .mentorCompletedAt(booking.getMentorCompletedAt())
                .learnerConfirmedAt(booking.getLearnerConfirmedAt())
                .learnerCompletedAt(booking.getLearnerCompletedAt())
                .completionDeadline(booking.getCompletionDeadline())
                .mentorName(mentorName)
                .mentorAvatar(mentorAvatar)
                .learnerName(learnerName)
                .learnerAvatar(learnerAvatar)
                .disputeId(disputeRepository.findByBooking_Id(booking.getId()).map(BookingDispute::getId).orElse(null))
                .build();
    }

    private Pageable withDefaultSort(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "startTime"));
    }

    private boolean inDateRange(Booking booking, LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return true;
        }
        return !booking.getStartTime().isBefore(from) && !booking.getStartTime().isAfter(to);
    }

    private LocalDateTime toStartOfDay(LocalDate value) {
        return value != null ? value.atStartOfDay() : null;
    }

    private LocalDateTime toEndOfDay(LocalDate value) {
        return value != null ? value.atTime(23, 59, 59) : null;
    }

    private BigDecimal safeMoney(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private record RevenueSlice(
            BigDecimal learnerSpend,
            BigDecimal refunded,
            BigDecimal mentorPayout,
            BigDecimal adminCommission,
            BigDecimal escrowHolding) {
    }
}
