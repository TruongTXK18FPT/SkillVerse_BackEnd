package com.exe.skillverse_backend.mentor_booking_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.mentor_booking_service.dto.request.CreateBookingIntentRequest;
import com.exe.skillverse_backend.mentor_booking_service.dto.response.BookingResponse;
import com.exe.skillverse_backend.mentor_booking_service.entity.Booking;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingRepository;
import com.exe.skillverse_backend.mentor_booking_service.service.BookingService;
import com.exe.skillverse_backend.mentor_service.entity.MentorProfile;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import com.exe.skillverse_backend.payment_service.event.PaymentSuccessEvent;
import com.exe.skillverse_backend.payment_service.service.InvoiceService;
import com.exe.skillverse_backend.payment_service.service.PaymentService;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import com.exe.skillverse_backend.wallet_service.repository.WalletTransactionRepository;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingReview;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingDisputeRepository;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingReviewRepository;
import com.exe.skillverse_backend.shared.service.EmailService;
import com.exe.skillverse_backend.user_service.service.UserProfileService;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final BookingReviewRepository bookingReviewRepository;
    private final WalletTransactionRepository transactionRepository;
    private final BookingDisputeRepository disputeRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final WalletService walletService;
    private final MentorProfileRepository mentorProfileRepository;
    private final UserProfileService userProfileService;
    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final InvoiceService invoiceService;

    @Value("${jitsi.base-url:https://meet.jit.si}")
    private String jitsiBaseUrl;

    @EventListener
    @Transactional
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        PaymentTransaction transaction = event.getTransaction();
        if (transaction.getType() == PaymentTransaction.PaymentType.MENTOR_BOOKING) {
            log.info("Received PaymentSuccessEvent for booking. Transaction ref: {}",
                    transaction.getInternalReference());
            createPendingFromPayment(transaction);
        }
    }

    @Transactional
    public Booking createBookingWithWallet(Long learnerId, CreateBookingIntentRequest request) {
        validateBookingRequest(learnerId, request);

        User mentor = userRepository.findById(request.getMentorId())
                .orElseThrow(() -> new IllegalArgumentException("Mentor không tồn tại"));
        User learner = userRepository.findById(learnerId)
                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại"));

        LocalDateTime start = request.getStartTime()
                .withZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh"))
                .toLocalDateTime();
        LocalDateTime end = start.plusMinutes(request.getDurationMinutes());

        Booking booking = Booking.builder()
                .mentor(mentor)
                .learner(learner)
                .startTime(start)
                .endTime(end)
                .durationMinutes(request.getDurationMinutes())
                .status(BookingStatus.PENDING)
                .priceVnd(request.getPriceVnd())
                .build();

        Booking saved = bookingRepository.save(booking);

        walletService.freezeCashForBooking(learnerId, request.getPriceVnd(), saved.getId());

        // Part 9a: meeting link NOT generated here — only when mentor starts the meeting
        notificationService.createNotification(
                mentor.getId(),
                "Có booking mới",
                "Bạn có một yêu cầu đặt lịch mới",
                NotificationType.BOOKING_CREATED,
                saved.getId().toString(),
                learner.getId());

        try {
            byte[] pdf = invoiceService.generateBookingInvoice(saved);
            String subject = "🎉 Đặt lịch mentor thành công";
            String html = buildBookingSuccessHtml(saved);
            emailService.sendHtmlEmailWithAttachment(learner.getEmail(), subject, html,
                    "Hoa_don_Booking_" + saved.getId() + ".pdf", pdf, "application/pdf");
        } catch (Exception e) {
        }

        scheduleMeetingReminderEmails(saved);

        return saved;
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingDetail(Long userId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking không tồn tại"));
        Long mentorId = booking.getMentor() != null ? booking.getMentor().getId() : null;
        Long learnerId = booking.getLearner() != null ? booking.getLearner().getId() : null;
        if (!Objects.equals(mentorId, userId) && !Objects.equals(learnerId, userId)) {
            throw new IllegalArgumentException("Không có quyền xem booking này");
        }
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public Booking getBookingIfParticipant(Long userId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking không tồn tại"));
        Long mentorId = booking.getMentor() != null ? booking.getMentor().getId() : null;
        Long learnerId = booking.getLearner() != null ? booking.getLearner().getId() : null;
        if (!Objects.equals(mentorId, userId) && !Objects.equals(learnerId, userId)) {
            throw new IllegalArgumentException("Không có quyền tải hóa đơn này");
        }
        return booking;
    }

    private void validateBookingRequest(Long learnerId, CreateBookingIntentRequest request) {
        // Convert ZonedDateTime (VN) to LocalDateTime for storage and business logic
        ZonedDateTime startZoned = request.getStartTime();
        LocalDateTime start = startZoned.withZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDateTime();
        LocalDateTime end = start.plusMinutes(request.getDurationMinutes());

        // Part 10a: Check booking start time is in the future (VN timezone)
        if (start.isBefore(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")))) {
            throw new IllegalArgumentException("Thời gian bắt đầu phải trong tương lai");
        }

        // Removed 2-hour and 23:00 restrictions as requested

        User mentor = userRepository.findById(request.getMentorId())
                .orElseThrow(() -> new IllegalArgumentException("Mentor không tồn tại"));
        User learner = userRepository.findById(learnerId)
                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại"));

        ensureLearnerIsNotMentor(learner, mentor);

        List<BookingStatus> activeStatuses = List.of(
                BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.ONGOING);

        if (bookingRepository.existsByMentorAndStatusInAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(
                mentor, activeStatuses, end, start)) {
            throw new IllegalStateException("Mentor có lịch trùng giờ");
        }
        if (bookingRepository.existsByLearnerAndStatusInAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(
                learner, activeStatuses, end, start)) {
            throw new IllegalStateException("Bạn có lịch trùng giờ");
        }
    }

    private String buildMetadataJson(CreateBookingIntentRequest request) {
        LocalDateTime startLocal = request.getStartTime()
                .withZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh"))
                .toLocalDateTime();
        return String.format("{\"mentorId\":%d,\"startTime\":\"%s\",\"durationMinutes\":%d,\"priceVnd\":%s}",
                request.getMentorId(),
                startLocal.toString(),
                request.getDurationMinutes(),
                request.getPriceVnd().toPlainString());
    }

    @Transactional
    public Booking createPendingFromPayment(PaymentTransaction transaction) {
        try {
            String paymentReference = transaction.getInternalReference();
            if (bookingRepository.existsByPaymentReference(paymentReference)) {
                log.warn("Booking already exists for payment reference {}, skipping duplicate callback",
                        paymentReference);
                return bookingRepository.findByPaymentReference(paymentReference)
                        .orElseThrow(() -> new IllegalStateException("Booking reference exists but cannot be loaded"));
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(transaction.getMetadata());
            Long mentorId = node.get("mentorId").asLong();
            LocalDateTime start = LocalDateTime.parse(node.get("startTime").asText());
            int duration = node.get("durationMinutes").asInt();
            BigDecimal price = new BigDecimal(node.get("priceVnd").asText());

            User mentor = userRepository.findById(mentorId)
                    .orElseThrow(() -> new IllegalArgumentException("Mentor không tồn tại"));
            User learner = transaction.getUser();

            ensureLearnerIsNotMentor(learner, mentor);

            LocalDateTime end = start.plusMinutes(duration);

            Booking booking = Booking.builder()
                    .mentor(mentor)
                    .learner(learner)
                    .startTime(start)
                    .endTime(end)
                    .durationMinutes(duration)
                    .status(BookingStatus.PENDING)
                    .priceVnd(price)
                    .paymentReference(paymentReference)
                    .build();

            Booking saved = bookingRepository.save(booking);

            // Part 9a: meeting link NOT generated here — only when mentor starts the meeting
            notificationService.createNotification(
                    mentor.getId(),
                    "Có booking mới",
                    "Bạn có một yêu cầu đặt lịch mới",
                    NotificationType.BOOKING_CREATED,
                    saved.getId().toString(),
                    learner.getId());

            try {
                byte[] pdf = invoiceService.generateBookingInvoice(saved);
                String subject = "🎉 Đặt lịch mentor thành công";
                String html = buildBookingSuccessHtml(saved);
                emailService.sendHtmlEmailWithAttachment(learner.getEmail(), subject, html,
                        "Hoa_don_Booking_" + saved.getId() + ".pdf", pdf, "application/pdf");
            } catch (Exception e) {
            }

            scheduleMeetingReminderEmails(saved);

            return saved;
        } catch (Exception e) {
            throw new RuntimeException("Không thể tạo booking từ thanh toán", e);
        }
    }

    private void ensureLearnerIsNotMentor(User learner, User mentor) {
        if (learner != null && mentor != null && Objects.equals(learner.getId(), mentor.getId())) {
            throw new IllegalArgumentException("Bạn không thể tự đặt lịch với chính mình");
        }
    }

    @Transactional
    public Booking approve(Long mentorId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking không tồn tại"));
        if (!booking.getMentor().getId().equals(mentorId)) {
            throw new IllegalArgumentException("Không có quyền duyệt booking này");
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("Chỉ duyệt booking ở trạng thái pending");
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        Booking saved = bookingRepository.save(booking);

        notificationService.createNotification(
                booking.getLearner().getId(),
                "Booking được chấp nhận",
                "Mentor đã duyệt lịch học",
                NotificationType.BOOKING_CONFIRMED,
                saved.getId().toString(),
                mentorId);

        try {
            String subjectLearner = "✅ Booking được chấp nhận";
            String htmlLearner = buildBookingApprovedHtml(saved, false);
            emailService.sendHtmlEmail(booking.getLearner().getEmail(), subjectLearner, htmlLearner);

            String subjectMentor = "📩 Bạn đã chấp nhận một booking";
            String htmlMentor = buildBookingApprovedHtml(saved, true);
            emailService.sendHtmlEmail(booking.getMentor().getEmail(), subjectMentor, htmlMentor);
        } catch (Exception e) {
        }

        scheduleMeetingReminderEmails(saved);

        return saved;
    }

    @Transactional
    public Booking reject(Long mentorId, Long bookingId, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking không tồn tại"));
        if (!booking.getMentor().getId().equals(mentorId)) {
            throw new IllegalArgumentException("Không có quyền từ chối booking này");
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("Chỉ từ chối booking ở trạng thái pending");
        }
        booking.setStatus(BookingStatus.REJECTED);
        booking.setMeetingLink(null);
        Booking saved = bookingRepository.save(booking);

        // Part 3: Idempotency check before refund
        boolean alreadyRefunded = transactionRepository.existsByReferenceIdAndReferenceTypeAndStatus(
                "BOOKING_" + saved.getId(),
                "BOOKING_REFUND",
                WalletTransaction.TransactionStatus.COMPLETED);
        if (!alreadyRefunded) {
            walletService.processRefund(
                    saved.getLearner().getId(),
                    saved.getPriceVnd(),
                    "Hoàn tiền do mentor từ chối",
                    "BOOKING_" + saved.getId());
        }

        notificationService.createNotification(
                booking.getLearner().getId(),
                "Booking bị từ chối",
                reason != null ? reason : "Mentor đã từ chối",
                NotificationType.BOOKING_REJECTED,
                saved.getId().toString(),
                mentorId);

        // Send rejection email with refund info
        try {
            String subject = "Lịch hẹn bị từ chối — Tiền đã hoàn về ví";
            String html = buildBookingRejectedHtml(saved, reason);
            emailService.sendHtmlEmail(booking.getLearner().getEmail(), subject, html);
        } catch (Exception e) {
            log.warn("Failed to send rejection email for booking {}: {}", saved.getId(), e.getMessage());
        }

        return saved;
    }

    @Transactional
    public Booking startMeeting(Long mentorId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking không tồn tại"));
        if (!booking.getMentor().getId().equals(mentorId)) {
            throw new IllegalArgumentException("Không có quyền bắt đầu buổi học này");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Chỉ bắt đầu từ trạng thái confirmed");
        }
        booking.setStatus(BookingStatus.ONGOING);
        // Part 9a: Generate meeting link when mentor actually starts the meeting
        booking.setMeetingLink(generateMeetingLink(booking));
        Booking saved = bookingRepository.save(booking);

        // Notify learner that meeting has started
        notificationService.createNotification(
                saved.getLearner().getId(),
                "Buổi học bắt đầu",
                "Mentor đã bắt đầu buổi học. Vào phòng ngay!",
                NotificationType.BOOKING_STARTED,
                saved.getId().toString(),
                mentorId);

        return saved;
    }

    /**
     * Mentor marks session as complete.
     * Always goes to PENDING_COMPLETION + sets 24h deadline.
     * If learner already confirmed -> COMPLETED + release payment.
     * Otherwise -> notify learner to confirm.
     */
    @Transactional
    public Booking complete(Long mentorId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking không tồn tại"));
        if (!booking.getMentor().getId().equals(mentorId)) {
            throw new IllegalArgumentException("Không có quyền hoàn tất buổi học này");
        }
        if (booking.getStatus() != BookingStatus.ONGOING && booking.getStatus() != BookingStatus.CONFIRMED
                && !(booking.getStatus() == BookingStatus.PENDING_COMPLETION && booking.getMentorCompletedAt() == null)) {
            throw new IllegalStateException("Chỉ hoàn tất từ trạng thái ongoing hoặc confirmed");
        }
        // Only allow completion if session has ended
        LocalDateTime sessionEnd = booking.getStartTime().plusMinutes(booking.getDurationMinutes());
        if (LocalDateTime.now().isBefore(sessionEnd)) {
            throw new IllegalStateException("Buổi học chưa kết thúc");
        }

        // Set deadline if not already set (first completion request)
        if (booking.getCompletionDeadline() == null) {
            booking.setCompletionDeadline(LocalDateTime.now().plusHours(24));
        }

        booking.setMentorCompletedAt(LocalDateTime.now());
        Booking saved = bookingRepository.save(booking);

        // If learner already confirmed, complete everything now
        if (Boolean.TRUE.equals(booking.getConfirmedByLearner())) {
            return finalizeSessionCompletion(saved);
        }

        // Otherwise stay at PENDING_COMPLETION, notify learner
        booking.setStatus(BookingStatus.PENDING_COMPLETION);
        saved = bookingRepository.save(booking);
        notificationService.createNotification(
                saved.getLearner().getId(),
                "Mentor đã hoàn tất buổi học",
                "Vui lòng xác nhận hoàn tất hoặc từ chối",
                NotificationType.BOOKING_MENTOR_COMPLETED,
                saved.getId().toString(),
                mentorId);
        try {
            String subject = "Mentor đã hoàn tất — Cần xác nhận của bạn";
            String html = buildMentorCompletedHtml(saved);
            emailService.sendHtmlEmail(booking.getLearner().getEmail(), subject, html);
        } catch (Exception e) {
            log.warn("Failed to send mentor-completed email for booking {}: {}", saved.getId(), e.getMessage());
        }
        return saved;
    }

    /**
     * Learner confirms session completion.
     * If mentor already completed -> COMPLETED + release payment.
     * Otherwise -> goes to PENDING_COMPLETION + 24h deadline, notifies mentor to confirm.
     * Either party can initiate — both must confirm for final completion.
     */
    @Transactional
    public Booking learnerConfirmComplete(Long learnerId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking không tồn tại"));
        if (!booking.getLearner().getId().equals(learnerId)) {
            throw new IllegalArgumentException("Không có quyền xác nhận buổi học này");
        }
        if (booking.getStatus() != BookingStatus.ONGOING && booking.getStatus() != BookingStatus.CONFIRMED
                && booking.getStatus() != BookingStatus.PENDING_COMPLETION) {
            throw new IllegalStateException("Không thể xác nhận từ trạng thái hiện tại");
        }
        // Only allow confirmation if session has ended (or if mentor already completed it)
        if (booking.getMentorCompletedAt() == null) {
            LocalDateTime sessionEnd = booking.getStartTime().plusMinutes(booking.getDurationMinutes());
            if (LocalDateTime.now().isBefore(sessionEnd)) {
                throw new IllegalStateException("Buổi học chưa kết thúc");
            }
        }

        // Set deadline if not already set (first completion request)
        if (booking.getCompletionDeadline() == null) {
            booking.setCompletionDeadline(LocalDateTime.now().plusHours(24));
        }

        booking.setConfirmedByLearner(true);
        booking.setLearnerCompletedAt(LocalDateTime.now());
        booking.setLearnerConfirmedAt(LocalDateTime.now());
        Booking saved = bookingRepository.save(booking);

        // If mentor already completed, complete everything now
        if (booking.getMentorCompletedAt() != null) {
            return finalizeSessionCompletion(saved);
        }

        // Otherwise go to PENDING_COMPLETION, notify mentor
        if (booking.getStatus() != BookingStatus.PENDING_COMPLETION) {
            booking.setStatus(BookingStatus.PENDING_COMPLETION);
            saved = bookingRepository.save(booking);
        }
        notificationService.createNotification(
                saved.getMentor().getId(),
                "Học viên đã hoàn tất buổi học",
                "Vui lòng xác nhận để hoàn tất buổi học",
                NotificationType.BOOKING_MENTOR_COMPLETED,
                saved.getId().toString(),
                learnerId);
        try {
            String subject = "Học viên đã hoàn tất — Cần xác nhận của bạn";
            String html = buildLearnerCompletedHtml(saved);
            emailService.sendHtmlEmail(booking.getMentor().getEmail(), subject, html);
        } catch (Exception e) {
            log.warn("Failed to send learner-completed email for booking {}: {}", saved.getId(), e.getMessage());
        }
        return saved;
    }

    /**
     * Shared finalization: both parties agreed -> COMPLETED + release payment + rewards.
     */
    private Booking finalizeSessionCompletion(Booking booking) {
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setMeetingLink(null);
        Booking saved = bookingRepository.save(booking);

        // Release payment to mentor
        walletService.chargeFrozenForBooking(saved.getLearner().getId(), saved.getPriceVnd(), saved.getId());
        BigDecimal mentorReceive = saved.getPriceVnd().multiply(new BigDecimal("0.80"));
        walletService.payMentorForBooking(saved.getMentor().getId(), mentorReceive, saved.getId());

        notificationService.createNotification(
                saved.getLearner().getId(),
                "Buổi học hoàn tất",
                "Bạn có thể đánh giá mentor",
                NotificationType.BOOKING_COMPLETED,
                saved.getId().toString(),
                saved.getMentor().getId());

        // Award skill points, badges, level-ups to mentor
        final Long mentorId = saved.getMentor().getId();
        mentorProfileRepository.findByUserId(mentorId).ifPresent(profile -> {
            int before = profile.getSkillPoints() != null ? profile.getSkillPoints() : 0;
            int after = before + SESSION_COMPLETION_POINTS;
            profile.setSkillPoints(after);
            int oldLevel = profile.getCurrentLevel() != null ? profile.getCurrentLevel() : 0;
            int newLevel = calculateLevel(after);
            if (newLevel > oldLevel) {
                profile.setCurrentLevel(newLevel);
                String title = getLevelTitle(newLevel);
                String message = title != null ? ("Bạn đã lên level " + newLevel + " - " + title)
                        : ("Bạn đã lên level " + newLevel);
                notificationService.createNotification(mentorId, "Lên level", message, NotificationType.MENTOR_LEVEL_UP,
                        "LEVEL_" + newLevel, saved.getId());
            }
            Set<String> badges = parseBadges(profile.getBadges());
            long completedCount = bookingRepository.countByMentorAndStatus(saved.getMentor(), BookingStatus.COMPLETED);
            if (completedCount == 1 && !badges.contains("FIRST_SESSION")) {
                badges.add("FIRST_SESSION");
                profile.setSkillPoints(profile.getSkillPoints() + FIRST_SESSION_BONUS);
                notificationService.createNotification(mentorId, "Nhận huy hiệu", "Hoàn thành buổi mentor đầu tiên",
                        NotificationType.MENTOR_BADGE_AWARDED, "BADGE_FIRST_SESSION", saved.getId());
            }
            if (completedCount == 10 && !badges.contains("TEN_SESSIONS")) {
                badges.add("TEN_SESSIONS");
                profile.setSkillPoints(profile.getSkillPoints() + TEN_SESSIONS_BONUS);
                notificationService.createNotification(mentorId, "Nhận huy hiệu", "Hoàn thành 10 buổi mentor",
                        NotificationType.MENTOR_BADGE_AWARDED, "BADGE_TEN_SESSIONS", saved.getId());
            }
            if (completedCount == 100 && !badges.contains("HUNDRED_SESSIONS")) {
                badges.add("HUNDRED_SESSIONS");
                profile.setSkillPoints(profile.getSkillPoints() + HUNDRED_SESSIONS_BONUS);
                notificationService.createNotification(mentorId, "Nhận huy hiệu", "Hoàn thành 100 buổi mentor",
                        NotificationType.MENTOR_BADGE_AWARDED, "BADGE_HUNDRED_SESSIONS", saved.getId());
            }
            profile.setBadges(toBadgesJson(badges));
            int recalculatedLevel = calculateLevel(profile.getSkillPoints());
            if (recalculatedLevel > profile.getCurrentLevel()) {
                profile.setCurrentLevel(recalculatedLevel);
                String title2 = getLevelTitle(recalculatedLevel);
                String msg2 = title2 != null ? ("Bạn đã lên level " + recalculatedLevel + " - " + title2)
                        : ("Bạn đã lên level " + recalculatedLevel);
                notificationService.createNotification(mentorId, "Lên level", msg2, NotificationType.MENTOR_LEVEL_UP,
                        "LEVEL_" + recalculatedLevel, saved.getId());
            }
            profile.setUpdatedAt(LocalDateTime.now());
            mentorProfileRepository.save(profile);
        });

        return saved;
    }

    private void scheduleMeetingReminderEmails(Booking booking) {
        try {
            Instant now = Instant.now();
            // Send reminder 30 minutes before start time, using VN timezone
            Instant target = booking.getStartTime()
                    .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                    .minusMinutes(30)
                    .toInstant();
            long delayMs = Duration.between(now, target).toMillis();
            if (delayMs < 0)
                delayMs = 0;

            // Read meeting link from booking entity (set when mentor starts meeting)
            // If not set yet (booking approved but not started), generate it proactively
            final String meetingLink = booking.getMeetingLink() != null
                    ? booking.getMeetingLink()
                    : generateMeetingLink(booking);

            final String linkToSend = meetingLink;

            Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                try {
                    String subject = "⏰ Nhắc lịch mentoring bắt đầu sau 30 phút";
                    String htmlLearner = buildBookingReminderHtml(booking, false, linkToSend);
                    String htmlMentor = buildBookingReminderHtml(booking, true, linkToSend);
                    emailService.sendHtmlEmail(booking.getLearner().getEmail(), subject, htmlLearner);
                    emailService.sendHtmlEmail(booking.getMentor().getEmail(), subject, htmlMentor);

                    // Also send in-app notification to learner
                    notificationService.createNotification(
                            booking.getLearner().getId(),
                            "Nhắc lịch hẹn",
                            "Buổi học bắt đầu sau 30 phút! Link phòng họp đã sẵn sàng.",
                            NotificationType.BOOKING_REMINDER,
                            booking.getId().toString(),
                            booking.getMentor().getId());
                } catch (Exception e) {
                    log.error("Failed to send meeting reminder email for booking {}: {}", booking.getId(), e.getMessage());
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("Failed to schedule meeting reminder for booking {}: {}", booking.getId(), e.getMessage());
        }
    }

    // Fixed timezone: stored LocalDateTime is already VN wall-clock time.
    // LocalDateTime has no timezone, so we format directly with a VN-aware formatter.
    private String formatTimeVN(LocalDateTime localTime) {
        try {
            // Treat the LocalDateTime as VN wall-clock time — format with VN zone
            return localTime.atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                    .format(DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy"));
        } catch (Exception e) {
            return localTime.toString();
        }
    }

    private String formatDateVN(LocalDateTime localTime) {
        try {
            return localTime.atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                    .format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", java.util.Locale.forLanguageTag("vi")));
        } catch (Exception e) {
            return localTime.toString();
        }
    }

    private String formatTimeOnlyVN(LocalDateTime localTime) {
        try {
            return localTime.atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                    .format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            return localTime.toString();
        }
    }

    private String formatVnd(BigDecimal amount) {
        try {
            return java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("vi"))
                    .format(amount) + " đ";
        } catch (Exception e) {
            return amount.toPlainString() + " VND";
        }
    }

        // ═══════════════════════════════════════════════════════════
        // EMAIL TEMPLATE 1: Booking thành công — gửi cho learner
        // ═══════════════════════════════════════════════════════════
        private String buildBookingSuccessHtml(Booking booking) {
        String mentorName = getDisplayName(booking.getMentor());
        String learnerName = getDisplayName(booking.getLearner());
        String time = formatTimeVN(booking.getStartTime());
        String price = formatVnd(booking.getPriceVnd());

        String intro = "<p>Kính gửi <strong>" + learnerName + "</strong>, lịch mentoring của bạn đã được tạo thành công.</p>"
            + "<p>Hóa đơn PDF của phiên học đã được đính kèm trong email này để bạn tiện đối soát.</p>";

        String details = buildBookingDetailRow("Mentor", mentorName)
            + buildBookingDetailRow("Thời gian", time)
            + buildBookingDetailRow("Thời lượng", booking.getDurationMinutes() + " phút")
            + buildBookingDetailRow("Chi phí", price);

        return buildBookingEmailLayout(
            "ĐẶT LỊCH THÀNH CÔNG",
            "Lịch mentoring đã được xác nhận",
            intro,
            details,
            "Xem lịch hẹn",
            "https://skillverse.vn/bookings/" + booking.getId(),
            "Link phòng học sẽ được gửi đến email của bạn trước 30 phút khi buổi mentoring bắt đầu.");
        }

        private String buildBookingApprovedHtml(Booking booking, boolean forMentor) {
        String counterpart = forMentor ? getDisplayName(booking.getLearner()) : getDisplayName(booking.getMentor());
        String roleText = forMentor ? "Học viên" : "Mentor";
        String time = formatTimeVN(booking.getStartTime());
        String meetingLink = booking.getMeetingLink();
        String meetingLinkText = (meetingLink != null && !meetingLink.isBlank())
            ? "<a href=\"" + meetingLink + "\" style=\"color:#0f75bc;font-weight:700;text-decoration:none\">Tham gia phòng học</a>"
            : "Sẽ gửi trước 30 phút";

        String intro = "<p>Lịch mentoring của bạn đã được xác nhận thành công.</p>"
            + "<p>Vui lòng kiểm tra lại thông tin buổi học bên dưới để chuẩn bị trước giờ tham gia.</p>";

        String details = buildBookingDetailRow(roleText, counterpart)
            + buildBookingDetailRow("Thời gian", time)
            + buildBookingDetailRow("Phòng học trực tuyến", meetingLinkText);

        return buildBookingEmailLayout(
            "LỊCH ĐÃ XÁC NHẬN",
            forMentor ? "Bạn đã xác nhận lịch mentoring" : "Mentor đã xác nhận lịch mentoring",
            intro,
            details,
            "Xem chi tiết buổi học",
            "https://skillverse.vn/bookings/" + booking.getId(),
            "Nếu chưa nhận được link phòng học, hệ thống sẽ tự động gửi thêm email nhắc trước giờ bắt đầu 30 phút.");
        }

        private String buildBookingReminderHtml(Booking booking, boolean forMentor, String meetingLink) {
        String counterpart = forMentor ? getDisplayName(booking.getLearner()) : getDisplayName(booking.getMentor());
        String roleText = forMentor ? "Học viên" : "Mentor";
        String time = formatTimeVN(booking.getStartTime());
        String link = meetingLink != null ? meetingLink : "";
        String safeJoinLink = (link != null && !link.isBlank()) ? link : "https://skillverse.vn/bookings/" + booking.getId();
        String meetingLinkText = (link != null && !link.isBlank())
            ? "<a href=\"" + link + "\" style=\"color:#0f75bc;font-weight:700;text-decoration:none\">Tham gia ngay</a>"
            : "Vui lòng mở chi tiết lịch hẹn để vào phòng học";

        String intro = "<p>Buổi mentoring của bạn sắp bắt đầu. Vui lòng vào phòng học đúng giờ để đảm bảo chất lượng buổi trao đổi.</p>";

        String details = buildBookingDetailRow(roleText, counterpart)
            + buildBookingDetailRow("Thời gian", time)
            + buildBookingDetailRow("Link phòng học", meetingLinkText);

        return buildBookingEmailLayout(
            "NHẮC LỊCH MENTORING",
            "Đến giờ mentoring",
            intro,
            details,
            "Vào phòng học",
            safeJoinLink,
            "Trong trường hợp không truy cập được link, vui lòng quay lại trang chi tiết lịch hẹn trên SkillVerse.");
        }

        private String getDisplayName(User user) {
        String fn = user.getFirstName();
        String ln = user.getLastName();
        String built = ((fn != null ? fn : "") + (ln != null ? " " + ln : "")).trim();
        return built.isEmpty() ? ("User #" + user.getId()) : built;
        }

        // [Nghiệp vụ] Dùng dòng thông tin chuẩn để mọi email booking có cấu trúc nhất quán và dễ đọc.
        private String buildBookingDetailRow(String label, String value) {
        String normalizedValue = (value == null || value.isBlank()) ? "-" : value;
        return """
            <tr>
              <td class=\"label\">%s</td>
              <td class=\"value\">%s</td>
            </tr>
            """.formatted(label, normalizedValue);
        }

        // [Nghiệp vụ] Dùng khung table-based để giữ logo căn giữa ổn định trên đa số email client.
        private String buildBookingEmailLayout(
            String badge,
            String title,
            String introHtml,
            String detailRowsHtml,
            String actionLabel,
            String actionUrl,
            String noteHtml) {

        String actionBlock = (actionLabel != null && !actionLabel.isBlank() && actionUrl != null && !actionUrl.isBlank())
            ? """
                <div class=\"cta\">
                  <a class=\"button\" href=\"%s\">%s</a>
                </div>
                """.formatted(actionUrl, actionLabel)
            : "";

        String noteBlock = (noteHtml != null && !noteHtml.isBlank())
            ? "<div class=\"note\">" + noteHtml + "</div>"
            : "";

        return """
            <!doctype html>
            <html lang=\"vi\">
            <head>
              <meta charset=\"UTF-8\" />
              <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
              <title>SkillVerse Booking</title>
              <style>
                body { margin:0; padding:0; background:#f3f6fb; font-family:Arial, Helvetica, sans-serif; color:#132238; }
                .wrapper { width:100%%; background:#f3f6fb; }
                .container { width:640px; max-width:640px; background:#ffffff; border-radius:16px; overflow:hidden; border:1px solid #d9e4f1; }
                                .header { padding:22px 18px; background:#061322; background-image:linear-gradient(120deg,#071321 0%%,#0a1f35 52%%,#0f3b63 100%%); border-bottom:1px solid #1c4d7a; }
                .logo { width:138px; max-width:138px; height:auto; display:block; margin:0 auto; }
                                .badge { display:inline-block; margin-top:12px; padding:6px 12px; border-radius:999px; background:#0c2138; color:#6de9ff; border:1px solid #24c8f5; font-size:11px; font-weight:700; letter-spacing:0.4px; }
                .content { padding:24px; }
                h1 { margin:0 0 12px 0; font-size:24px; line-height:1.3; color:#10263f; }
                p { margin:0 0 10px 0; line-height:1.7; font-size:14px; color:#344a63; }
                .detail-card { width:100%%; border:1px solid #dbe6f3; border-radius:12px; border-collapse:separate; border-spacing:0; margin-top:14px; }
                .detail-card tr + tr td { border-top:1px solid #e8eff8; }
                .detail-card td { padding:12px 14px; font-size:14px; }
                .detail-card .label { color:#617991; width:42%%; }
                .detail-card .value { color:#163352; font-weight:700; text-align:right; }
                .note { margin-top:14px; background:#eaf6ff; border:1px solid #cae8ff; color:#1f5f92; border-radius:10px; padding:12px 14px; font-size:13px; line-height:1.6; }
                .cta { margin-top:20px; text-align:left; }
                .button { display:inline-block; background:#0f75bc; color:#ffffff !important; text-decoration:none; padding:12px 18px; border-radius:10px; font-size:14px; font-weight:700; }
                .footer { padding:14px 20px 20px; font-size:12px; text-align:center; color:#6c8098; border-top:1px solid #e6eef8; background:#fbfdff; }
              </style>
            </head>
            <body>
              <table role=\"presentation\" class=\"wrapper\" cellpadding=\"0\" cellspacing=\"0\">
                <tr>
                  <td align=\"center\" style=\"padding:24px 12px;\">
                <table role=\"presentation\" class=\"container\" cellpadding=\"0\" cellspacing=\"0\">
                  <tr>
                    <td class=\"header\" align=\"center\">
                      <img class=\"logo\" src=\"cid:skillverse-logo\" alt=\"SkillVerse\" />
                      <div class=\"badge\">%s</div>
                    </td>
                  </tr>
                  <tr>
                    <td class=\"content\">
                      <h1>%s</h1>
                      %s
                      <table role=\"presentation\" class=\"detail-card\" cellpadding=\"0\" cellspacing=\"0\">
                    %s
                      </table>
                      %s
                      %s
                    </td>
                  </tr>
                  <tr>
                    <td class=\"footer\">© 2026 SkillVerse. Email này được gửi tự động từ hệ thống.</td>
                  </tr>
                </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(badge, title, introHtml, detailRowsHtml, noteBlock, actionBlock);
        }

        // EMAIL TEMPLATE 4: Booking bị từ chối — gửi cho learner
        private String buildBookingRejectedHtml(Booking booking, String reason) {
        String mentorName = getDisplayName(booking.getMentor());
        String time = formatTimeVN(booking.getStartTime());
        String reasonText = (reason != null && !reason.isBlank())
            ? reason
            : "Mentor hiện chưa thể nhận lịch này. Vui lòng chọn khung giờ khác hoặc mentor khác phù hợp hơn.";
        String refundAmount = formatVnd(booking.getPriceVnd());

        String intro = "<p>Rất tiếc, lịch mentoring của bạn chưa thể được thực hiện theo thời gian đã đặt.</p>"
            + "<p>Số tiền thanh toán của bạn đã được hoàn về ví SkillVerse để bạn có thể đặt lịch mới ngay lập tức.</p>";

        String details = buildBookingDetailRow("Mentor", mentorName)
            + buildBookingDetailRow("Thời gian đã đặt", time)
            + buildBookingDetailRow("Số tiền hoàn", refundAmount);

        return buildBookingEmailLayout(
            "LỊCH HẸN BỊ TỪ CHỐI",
            "Cập nhật lịch mentoring",
            intro,
            details,
            "Tìm mentor khác",
            "https://skillverse.vn/mentors/" + booking.getMentor().getId(),
            "Lý do từ mentor: <strong>" + reasonText + "</strong>");
        }

        // EMAIL TEMPLATE 5: Booking bị hủy — gửi cho cả learner và mentor
        private String buildBookingCancelledHtml(Booking booking, boolean forMentor) {
        String counterpart = forMentor ? getDisplayName(booking.getLearner()) : getDisplayName(booking.getMentor());
        String roleText = forMentor ? "Học viên" : "Mentor";
        String time = formatTimeVN(booking.getStartTime());
        String cancelledBy = forMentor ? "Học viên đã hủy lịch mentoring này." : "Mentor đã hủy lịch mentoring này.";
        String refundAmount = formatVnd(booking.getPriceVnd());

        String intro = "<p>Thông báo từ hệ thống: lịch hẹn mentoring đã được hủy thành công.</p>"
            + "<p>Bạn có thể theo dõi trạng thái và đặt lịch mới trực tiếp trên SkillVerse.</p>";

        String details = buildBookingDetailRow(roleText, counterpart)
            + buildBookingDetailRow("Thời gian đã đặt", time)
            + buildBookingDetailRow("Số tiền hoàn", refundAmount);

        return buildBookingEmailLayout(
            "LỊCH HẸN ĐÃ HỦY",
            "Thông báo hủy lịch mentoring",
            intro,
            details,
            "Xem lịch hẹn",
            "https://skillverse.vn/bookings/" + booking.getId(),
            cancelledBy + " Số tiền đã được hoàn về ví học viên.");
        }

        // EMAIL TEMPLATE 6: Mentor hoàn thành buổi học — gửi cho learner
        private String buildMentorCompletedHtml(Booking booking) {
        String mentorName = getDisplayName(booking.getMentor());
        String time = formatTimeVN(booking.getStartTime());
        String actionDeadline = booking.getStartTime()
            .plusDays(1)
            .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
            .format(DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy"));

        String intro = "<p><strong>" + mentorName + "</strong> đã đánh dấu buổi mentoring là hoàn tất.</p>"
            + "<p>Vui lòng xác nhận kết quả buổi học để hệ thống tiến hành thanh toán cho mentor đúng hạn.</p>";

        String details = buildBookingDetailRow("Mentor", mentorName)
            + buildBookingDetailRow("Thời gian", time)
            + buildBookingDetailRow("Chi phí phiên học", formatVnd(booking.getPriceVnd()));

        String note = "Bạn cần xác nhận trước <strong>" + actionDeadline + "</strong>. Nếu quá hạn 24 giờ, hệ thống sẽ tự động hoàn tất giao dịch.";

        return buildBookingEmailLayout(
            "CẦN XÁC NHẬN",
            "Mentor đã hoàn tất buổi học",
            intro,
            details,
            "Xác nhận hoàn tất",
            "https://skillverse.vn/bookings/" + booking.getId(),
            note);
        }

        // EMAIL TEMPLATE 7: Learner hoàn thành buổi học — gửi cho mentor
        private String buildLearnerCompletedHtml(Booking booking) {
        String learnerName = getDisplayName(booking.getLearner());
        String time = formatTimeVN(booking.getStartTime());
        String deadline = booking.getCompletionDeadline() != null
            ? booking.getCompletionDeadline()
                .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy"))
            : booking.getStartTime()
                .plusDays(1)
                .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy"));

        String intro = "<p><strong>" + learnerName + "</strong> đã xác nhận buổi mentoring hoàn tất.</p>"
            + "<p>Vui lòng kiểm tra nhanh nội dung phiên học để hoàn tất đối soát và nhận thanh toán.</p>";

        String details = buildBookingDetailRow("Học viên", learnerName)
            + buildBookingDetailRow("Thời gian", time)
            + buildBookingDetailRow("Khoản thanh toán", formatVnd(booking.getPriceVnd()));

        String note = "Bạn cần xác nhận trước <strong>" + deadline + "</strong>. Nếu quá hạn, hệ thống sẽ tự động hoàn tất phiên học.";

        return buildBookingEmailLayout(
            "CẦN XÁC NHẬN",
            "Học viên đã hoàn tất buổi học",
            intro,
            details,
            "Xác nhận hoàn tất",
            "https://skillverse.vn/bookings/" + booking.getId(),
            note);
        }
    @Transactional
    public Booking cancelByLearner(Long learnerId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking không tồn tại"));
        if (!booking.getLearner().getId().equals(learnerId)) {
            throw new IllegalArgumentException("Không có quyền hủy booking này");
        }
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Chỉ được hủy khi pending/confirmed");
        }
        if (LocalDateTime.now().isAfter(booking.getStartTime().minusDays(1))) {
            throw new IllegalStateException("Chỉ hủy trước tối thiểu 1 ngày");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setMeetingLink(null);
        Booking saved = bookingRepository.save(booking);

        // Part 3: Throw exception on unfreeze failure instead of swallowing
        walletService.unfreezeForBooking(learnerId, saved.getPriceVnd(), saved.getId());

        notificationService.createNotification(
                saved.getMentor().getId(),
                "Booking bị hủy",
                "Learner đã hủy lịch trước 1 ngày",
                NotificationType.BOOKING_CANCELLED,
                saved.getId().toString(),
                learnerId);

        // Send cancellation email to learner
        try {
            String subjectLearner = "Lịch hẹn đã bị hủy";
            String htmlLearner = buildBookingCancelledHtml(saved, false);
            emailService.sendHtmlEmail(booking.getLearner().getEmail(), subjectLearner, htmlLearner);
        } catch (Exception e) {
            log.warn("Failed to send cancellation email to learner for booking {}: {}", saved.getId(), e.getMessage());
        }

        // Send cancellation email to mentor
        try {
            String subjectMentor = "Lịch hẹn đã bị hủy";
            String htmlMentor = buildBookingCancelledHtml(saved, true);
            emailService.sendHtmlEmail(booking.getMentor().getEmail(), subjectMentor, htmlMentor);
        } catch (Exception e) {
            log.warn("Failed to send cancellation email to mentor for booking {}: {}", saved.getId(), e.getMessage());
        }

        return saved;
    }

    @Transactional
    public void rateAfterSession(Long learnerId, Long bookingId, Integer stars, String comment, String skillEndorsed) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking không tồn tại"));
        if (!booking.getLearner().getId().equals(learnerId)) {
            throw new IllegalArgumentException("Không có quyền đánh giá buổi học này");
        }
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new IllegalStateException("Chỉ được đánh giá sau khi hoàn tất buổi học");
        }
        // Part 2: Save to BookingReview (mentor_booking_service) instead of MentorReview (portfolio_service)
        BookingReview review = BookingReview.builder()
                .booking(booking)
                .student(booking.getLearner())
                .mentor(booking.getMentor())
                .rating(stars)
                .comment(comment != null ? comment : "")
                .reply(null)
                .isAnonymous(false)
                .build();
        bookingReviewRepository.save(review);

        notificationService.createNotification(
                booking.getMentor().getId(),
                "Bạn nhận được đánh giá",
                (comment != null ? comment : "") + " (" + stars + "/5)",
                NotificationType.MENTOR_REVIEW_RECEIVED,
                booking.getId().toString(),
                learnerId);

        mentorProfileRepository.findByUserId(booking.getMentor().getId()).ifPresent(profile -> {
            // Part 2: Read stats from BookingReview instead of MentorReview
            var reviews = bookingReviewRepository.findByMentorIdOrderByCreatedAtDesc(booking.getMentor().getId());
            int count = reviews.size();
            double avg = count == 0 ? 0.0
                    : reviews.stream().filter(r -> r.getRating() != null).mapToInt(BookingReview::getRating).average()
                            .orElse(0.0);
            profile.setRatingCount(count);
            profile.setRatingAverage(avg);
            if (stars != null && stars == 5) {
                long fiveStarCount = bookingReviewRepository.countByMentorIdAndRating(booking.getMentor().getId(), 5);
                Set<String> badges = parseBadges(profile.getBadges());
                if (fiveStarCount == 1 && !badges.contains("FIRST_5_STAR")) {
                    badges.add("FIRST_5_STAR");
                    profile.setSkillPoints(
                            (profile.getSkillPoints() != null ? profile.getSkillPoints() : 0) + FIRST_FIVE_STAR_BONUS);
                    notificationService.createNotification(booking.getMentor().getId(), "Nhận huy hiệu",
                            "Được đánh giá 5 sao đầu tiên", NotificationType.MENTOR_BADGE_AWARDED,
                            "BADGE_FIRST_5_STAR");
                }
                if (fiveStarCount == 10 && !badges.contains("TEN_5_STAR")) {
                    badges.add("TEN_5_STAR");
                    profile.setSkillPoints(
                            (profile.getSkillPoints() != null ? profile.getSkillPoints() : 0) + TEN_FIVE_STAR_BONUS);
                    notificationService.createNotification(booking.getMentor().getId(), "Nhận huy hiệu",
                            "Đạt 10 đánh giá 5 sao", NotificationType.MENTOR_BADGE_AWARDED, "BADGE_TEN_5_STAR");
                }
                if (fiveStarCount == 100 && !badges.contains("HUNDRED_5_STAR")) {
                    badges.add("HUNDRED_5_STAR");
                    profile.setSkillPoints((profile.getSkillPoints() != null ? profile.getSkillPoints() : 0)
                            + HUNDRED_FIVE_STAR_BONUS);
                    notificationService.createNotification(booking.getMentor().getId(), "Nhận huy hiệu",
                            "Đạt 100 đánh giá 5 sao", NotificationType.MENTOR_BADGE_AWARDED, "BADGE_HUNDRED_5_STAR");
                }
                profile.setBadges(toBadgesJson(badges));
                int currentPoints = profile.getSkillPoints() != null ? profile.getSkillPoints() : 0;
                int newLevel = calculateLevel(currentPoints);
                if (newLevel > (profile.getCurrentLevel() != null ? profile.getCurrentLevel() : 0)) {
                    profile.setCurrentLevel(newLevel);
                    String t = getLevelTitle(newLevel);
                    String msg = t != null ? ("Bạn đã lên level " + newLevel + " - " + t)
                            : ("Bạn đã lên level " + newLevel);
                    notificationService.createNotification(booking.getMentor().getId(), "Lên level", msg,
                            NotificationType.MENTOR_LEVEL_UP, "LEVEL_" + newLevel);
                }
            }
            profile.setUpdatedAt(LocalDateTime.now());
            mentorProfileRepository.save(profile);
        });
    }

    public Page<BookingResponse> getUserBookings(Long userId, boolean mentorView, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại"));
        List<BookingStatus> statuses = List.of(BookingStatus.values());
        Page<Booking> page = mentorView
                ? bookingRepository.findByMentorAndStatusInOrderByStartTimeDesc(user, statuses, pageable)
                : bookingRepository.findByLearnerAndStatusInOrderByStartTimeDesc(user, statuses, pageable);
        return page.map(this::toResponse);
    }

    public List<BookingResponse> getMentorBookingsForDateRange(Long mentorId, LocalDateTime from, LocalDateTime to) {
        List<BookingStatus> activeStatuses = List.of(
                BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.ONGOING,
                BookingStatus.PENDING_COMPLETION);
        User mentor = userRepository.findById(mentorId)
                .orElseThrow(() -> new IllegalArgumentException("Mentor không tồn tại"));
        List<Booking> bookings = bookingRepository.findByMentorAndStatusInAndStartTimeBetween(
                mentor, activeStatuses, from, to);
        return bookings.stream().map(this::toResponse).toList();
    }

    private BookingResponse toResponse(Booking booking) {
        String mentorName = null;
        String mentorAvatar = null;
        String learnerName = null;
        String learnerAvatar = null;

        try {
            var mentor = booking.getMentor();
            var learner = booking.getLearner();

            // ── Mentor: UserProfile.avatarMedia → MentorProfile.avatarUrl → User.avatarUrl ──
            if (userProfileService.hasProfile(mentor.getId())) {
                var mProfile = userProfileService.getProfile(mentor.getId());
                mentorName = mProfile.getFullName();
                if (mProfile.getAvatarMediaUrl() != null) {
                    mentorAvatar = mProfile.getAvatarMediaUrl();
                }
            }
            if (mentorName == null) {
                var mentorProfile = mentorProfileRepository.findById(mentor.getId()).orElse(null);
                if (mentorProfile != null && mentorProfile.getFullName() != null && !mentorProfile.getFullName().isEmpty()) {
                    mentorName = mentorProfile.getFullName();
                }
                if (mentorAvatar == null && mentorProfile != null
                        && mentorProfile.getAvatarUrl() != null && !mentorProfile.getAvatarUrl().isEmpty()) {
                    mentorAvatar = mentorProfile.getAvatarUrl();
                }
            }
            if (mentorName == null || mentorName.isBlank()) {
                mentorName = mentor.getFullName();
            }
            if (mentorAvatar == null) {
                mentorAvatar = mentor.getAvatarUrl();
            }

            // ── Learner: UserProfile.avatarMedia → User.avatarUrl ──
            if (userProfileService.hasProfile(learner.getId())) {
                var lProfile = userProfileService.getProfile(learner.getId());
                learnerName = lProfile.getFullName();
                learnerAvatar = lProfile.getAvatarMediaUrl();
            }
            if (learnerName == null || learnerName.isBlank()) {
                learnerName = learner.getFullName();
            }
            if (learnerAvatar == null) {
                learnerAvatar = learner.getAvatarUrl();
            }
        } catch (Exception e) {
            log.warn("Failed to enrich booking user info: {}", e.getMessage());
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
                .disputeId(disputeRepository.findByBooking_Id(booking.getId()).map(d -> d.getId()).orElse(null))
                .chatAllowed(isChatAllowed(booking))
                .build();
    }

    private boolean isChatAllowed(Booking booking) {
        if (booking == null || booking.getEndTime() == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        if (!booking.getEndTime().isAfter(now)) {
            return false;
        }

        return booking.getStatus() == BookingStatus.PENDING
                || booking.getStatus() == BookingStatus.CONFIRMED
                || booking.getStatus() == BookingStatus.ONGOING;
    }

    private String generateMeetingLink(Booking booking) {
        String room = String.format("SkillVerse-%d-%d-%d", booking.getId(), booking.getMentor().getId(),
                booking.getLearner().getId());
        return jitsiBaseUrl + "/" + room;
    }

    private static final int SESSION_COMPLETION_POINTS = 20;
    private static final int FIRST_SESSION_BONUS = 50;
    private static final int TEN_SESSIONS_BONUS = 100;
    private static final int HUNDRED_SESSIONS_BONUS = 500;
    private static final int FIRST_FIVE_STAR_BONUS = 30;
    private static final int TEN_FIVE_STAR_BONUS = 150;
    private static final int HUNDRED_FIVE_STAR_BONUS = 1000;

    private int calculateLevel(int points) {
        if (points < 0)
            return 0;
        return points / 100;
    }

    private String getLevelTitle(int level) {
        if (level == 1)
            return "Mentor mới nổi";
        if (level == 5)
            return "Mentor ngôi sao";
        if (level == 10)
            return "Mentor kỳ cựu";
        if (level == 15)
            return "Mentor cao thủ";
        if (level == 20)
            return "Mentor siêu cấp";
        return null;
    }

    private Set<String> parseBadges(String badgesJson) {
        try {
            if (badgesJson == null || badgesJson.isBlank())
                return new HashSet<>();
            List<String> list = objectMapper.readValue(badgesJson,
                    new TypeReference<List<String>>() {
                    });
            return new HashSet<>(list);
        } catch (Exception e) {
            return new HashSet<>();
        }
    }

    private String toBadgesJson(Set<String> badges) {
        try {
            return objectMapper.writeValueAsString(new ArrayList<>(badges));
        } catch (Exception e) {
            return "[]";
        }
    }
}
