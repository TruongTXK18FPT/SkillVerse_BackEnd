package com.exe.skillverse_backend.mentor_booking_service.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.mentor_booking_service.dto.request.CreateBookingIntentRequest;
import com.exe.skillverse_backend.mentor_booking_service.dto.response.BookingResponse;
import com.exe.skillverse_backend.mentor_booking_service.entity.Booking;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingRepository;
import com.exe.skillverse_backend.mentor_booking_service.service.BookingService;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.payment_service.dto.request.CreatePaymentRequest;
import com.exe.skillverse_backend.payment_service.dto.response.CreatePaymentResponse;
import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import com.exe.skillverse_backend.payment_service.service.PaymentService;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.payment_service.event.PaymentSuccessEvent;
import com.exe.skillverse_backend.mentor_service.entity.MentorProfile;
import com.exe.skillverse_backend.shared.service.EmailService;
import com.exe.skillverse_backend.payment_service.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final WalletService walletService;
    private final com.exe.skillverse_backend.portfolio_service.repository.MentorReviewRepository mentorReviewRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final com.exe.skillverse_backend.user_service.service.UserProfileService userProfileService;
    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final InvoiceService invoiceService;

    @org.springframework.beans.factory.annotation.Value("${jitsi.base-url:https://meet.jit.si}")
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
    public CreatePaymentResponse createBookingIntent(Long learnerId, CreateBookingIntentRequest request) {
        validateBookingRequest(learnerId, request);

        String metadata = buildMetadataJson(request);

        CreatePaymentRequest paymentRequest = CreatePaymentRequest.builder()
                .amount(request.getPriceVnd())
                .currency("VND")
                .type(PaymentTransaction.PaymentType.MENTOR_BOOKING)
                .paymentMethod("PAYOS".equalsIgnoreCase(request.getPaymentMethod())
                        ? PaymentTransaction.PaymentMethod.PAYOS
                        : PaymentTransaction.PaymentMethod.BANK_TRANSFER)
                .description("Mentor booking")
                .metadata(metadata)
                .successUrl(request.getSuccessUrl())
                .cancelUrl(request.getCancelUrl())
                .build();

        return paymentService.createPayment(learnerId, paymentRequest);
    }

    @Transactional
    public Booking createBookingWithWallet(Long learnerId, CreateBookingIntentRequest request) {
        validateBookingRequest(learnerId, request);

        User mentor = userRepository.findById(request.getMentorId())
                .orElseThrow(() -> new IllegalArgumentException("Mentor không tồn tại"));
        User learner = userRepository.findById(learnerId)
                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại"));

        LocalDateTime start = request.getStartTime();
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

        saved.setMeetingLink(generateMeetingLink(saved));
        saved = bookingRepository.save(saved);

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
    public Booking getBookingIfParticipant(Long userId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking không tồn tại"));
        Long mentorId = booking.getMentor() != null ? booking.getMentor().getId() : null;
        Long learnerId = booking.getLearner() != null ? booking.getLearner().getId() : null;
        if (!java.util.Objects.equals(mentorId, userId) && !java.util.Objects.equals(learnerId, userId)) {
            throw new IllegalArgumentException("Không có quyền tải hóa đơn này");
        }
        return booking;
    }

    private void validateBookingRequest(Long learnerId, CreateBookingIntentRequest request) {
        LocalDateTime start = request.getStartTime();
        LocalDateTime end = start.plusMinutes(request.getDurationMinutes());

        // Removed 2-hour and 23:00 restrictions as requested

        User mentor = userRepository.findById(request.getMentorId())
                .orElseThrow(() -> new IllegalArgumentException("Mentor không tồn tại"));
        User learner = userRepository.findById(learnerId)
                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại"));

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
        return String.format("{\"mentorId\":%d,\"startTime\":\"%s\",\"durationMinutes\":%d,\"priceVnd\":%s}",
                request.getMentorId(),
                request.getStartTime().toString(),
                request.getDurationMinutes(),
                request.getPriceVnd().toPlainString());
    }

    @Transactional
    public Booking createPendingFromPayment(PaymentTransaction transaction) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(transaction.getMetadata());
            Long mentorId = node.get("mentorId").asLong();
            LocalDateTime start = LocalDateTime.parse(node.get("startTime").asText());
            int duration = node.get("durationMinutes").asInt();
            BigDecimal price = new BigDecimal(node.get("priceVnd").asText());

            User mentor = userRepository.findById(mentorId)
                    .orElseThrow(() -> new IllegalArgumentException("Mentor không tồn tại"));
            User learner = transaction.getUser();

            LocalDateTime end = start.plusMinutes(duration);

            Booking booking = Booking.builder()
                    .mentor(mentor)
                    .learner(learner)
                    .startTime(start)
                    .endTime(end)
                    .durationMinutes(duration)
                    .status(BookingStatus.PENDING)
                    .priceVnd(price)
                    .paymentReference(transaction.getInternalReference())
                    .build();

            Booking saved = bookingRepository.save(booking);

            saved.setMeetingLink(generateMeetingLink(saved));
            saved = bookingRepository.save(saved);

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
        booking.setMeetingLink(generateMeetingLink(booking));
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
        Booking saved = bookingRepository.save(booking);

        walletService.processRefund(
                saved.getLearner().getId(),
                saved.getPriceVnd(),
                "Hoàn tiền do mentor từ chối",
                "BOOKING_" + saved.getId());

        notificationService.createNotification(
                booking.getLearner().getId(),
                "Booking bị từ chối",
                reason != null ? reason : "Mentor đã từ chối",
                NotificationType.BOOKING_REJECTED,
                saved.getId().toString(),
                mentorId);

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
        return bookingRepository.save(booking);
    }

    @Transactional
    public Booking complete(Long mentorId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking không tồn tại"));
        if (!booking.getMentor().getId().equals(mentorId)) {
            throw new IllegalArgumentException("Không có quyền hoàn tất buổi học này");
        }
        if (booking.getStatus() != BookingStatus.ONGOING && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Chỉ hoàn tất từ trạng thái ongoing hoặc confirmed");
        }
        booking.setStatus(BookingStatus.COMPLETED);
        Booking saved = bookingRepository.save(booking);

        walletService.chargeFrozenForBooking(saved.getLearner().getId(), saved.getPriceVnd(), saved.getId());

        BigDecimal mentorReceive = saved.getPriceVnd().multiply(new BigDecimal("0.80"));
        walletService.payMentorForBooking(mentorId, mentorReceive, saved.getId());

        notificationService.createNotification(
                saved.getLearner().getId(),
                "Buổi học hoàn tất",
                "Bạn có thể đánh giá mentor",
                NotificationType.BOOKING_COMPLETED,
                saved.getId().toString(),
                mentorId);

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
            java.util.Set<String> badges = parseBadges(profile.getBadges());
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
            profile.setUpdatedAt(java.time.LocalDateTime.now());
            mentorProfileRepository.save(profile);
        });

        return saved;
    }

    private void scheduleMeetingReminderEmails(Booking booking) {
        try {
            java.time.Instant now = java.time.Instant.now();
            java.time.Instant target = booking.getStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant();
            long delayMs = java.time.Duration.between(now, target).toMillis();
            if (delayMs < 0)
                delayMs = 0;

            java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                try {
                    String subject = "⏰ Nhắc lịch mentoring bắt đầu";
                    String htmlLearner = buildBookingReminderHtml(booking, false);
                    String htmlMentor = buildBookingReminderHtml(booking, true);
                    emailService.sendHtmlEmail(booking.getLearner().getEmail(), subject, htmlLearner);
                    emailService.sendHtmlEmail(booking.getMentor().getEmail(), subject, htmlMentor);
                } catch (Exception e) {
                }
            }, delayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
        }
    }

    private String formatTimeVN(LocalDateTime utcTime) {
        try {
            // Assume stored time is UTC, convert to Vietnam time (UTC+7)
            java.time.ZonedDateTime vnTime = utcTime.atZone(java.time.ZoneId.of("UTC"))
                    .withZoneSameInstant(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
            return java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").format(vnTime);
        } catch (Exception e) {
            return utcTime.toString();
        }
    }

    private String buildBookingSuccessHtml(Booking booking) {
        String mentorName = getDisplayName(booking.getMentor());
        String learnerName = getDisplayName(booking.getLearner());
        String time = formatTimeVN(booking.getStartTime());
        String link = booking.getMeetingLink() != null ? booking.getMeetingLink() : "-";
        String price = booking.getPriceVnd() != null ? booking.getPriceVnd().toPlainString() + " VND" : "-";
        return """
                <html><head><meta charset=\"UTF-8\" /><style>
                body{font-family:Inter,system-ui,-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;background:#f8fafc;margin:0;padding:0}
                .container{max-width:640px;margin:24px auto;background:#ffffff;border-radius:16px;box-shadow:0 10px 25px rgba(2,6,23,0.08);overflow:hidden}
                .header{background:linear-gradient(135deg,#4f46e5,#0ea5e9);padding:24px;display:flex;justify-content:center;align-items:center}
                .logo{width:44px;height:44px;border-radius:10px;overflow:hidden}
                .content{padding:24px;color:#111827}
                .pill{display:inline-block;background:#ecfeff;color:#0ea5e9;padding:6px 12px;border-radius:999px;font-size:12px;font-weight:600;margin-bottom:12px}
                .card{border:1px solid #e5e7eb;border-radius:12px;padding:16px;margin-top:12px}
                .row{display:flex;justify-content:space-between;margin:6px 0}
                .label{color:#6b7280}.value{font-weight:600}
                .cta{margin-top:20px}
                .button{background:#4f46e5;color:#fff;text-decoration:none;padding:12px 16px;border-radius:10px;font-weight:700}
                .footer{padding:16px;text-align:center;color:#6b7280;font-size:12px}
                </style></head>
                <body><div class=\"container\"><div class=\"header\"><img class=\"logo\" src=\"cid:skillverse-logo\" /></div>
                <div class=\"content\"><div class=\"pill\">Đặt lịch thành công</div>
                <h2>Chúc mừng, %s!</h2>
                <p>Bạn đã đặt lịch mentoring với <strong>%s</strong>. Hóa đơn PDF được đính kèm.</p>
                <div class=\"card\"><div class=\"row\"><div class=\"label\">Thời gian</div><div class=\"value\">%s</div></div>
                <div class=\"row\"><div class=\"label\">Thời lượng</div><div class=\"value\">%d phút</div></div>
                <div class=\"row\"><div class=\"label\">Giá</div><div class=\"value\">%s</div></div>
                <div class=\"row\"><div class=\"label\">Link Jitsi</div><div class=\"value\"><a href=\"%s\">Tham gia</a></div></div></div>
                <div class=\"cta\"><a class=\"button\" href=\"%s\">Xem lịch</a></div></div>
                <div class=\"footer\">© 2025</div></div></body></html>
                """
                .formatted(learnerName, mentorName, time, booking.getDurationMinutes(), price, link,
                        "https://skillverse.vn/bookings/" + booking.getId());
    }

    private String buildBookingApprovedHtml(Booking booking, boolean forMentor) {
        String counterpart = forMentor ? getDisplayName(booking.getLearner()) : getDisplayName(booking.getMentor());
        String roleText = forMentor ? "Học viên" : "Mentor";
        String time = formatTimeVN(booking.getStartTime());
        String link = booking.getMeetingLink() != null ? booking.getMeetingLink() : "-";
        return """
                <html><head><meta charset=\"UTF-8\" /><style>
                body{font-family:Inter,system-ui,-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;background:#f8fafc;margin:0;padding:0}
                .container{max-width:640px;margin:24px auto;background:#ffffff;border-radius:16px;box-shadow:0 10px 25px rgba(2,6,23,0.08);overflow:hidden}
                .header{background:linear-gradient(135deg,#22c55e,#0ea5e9);padding:24px;display:flex;justify-content:center;align-items:center}
                .logo{width:44px;height:44px;border-radius:10px;overflow:hidden}
                .content{padding:24px;color:#111827}
                .card{border:1px solid #e5e7eb;border-radius:12px;padding:16px;margin-top:12px}
                .row{display:flex;justify-content:space-between;margin:6px 0}
                .label{color:#6b7280}.value{font-weight:600}
                .cta{margin-top:20px}
                .button{background:#22c55e;color:#fff;text-decoration:none;padding:12px 16px;border-radius:10px;font-weight:700}
                .footer{padding:16px;text-align:center;color:#6b7280;font-size:12px}
                </style></head>
                <body><div class=\"container\"><div class=\"header\"><img class=\"logo\" src=\"cid:skillverse-logo\" /></div>
                <div class=\"content\"><h2>%s đã xác nhận!</h2>
                <div class=\"card\"><div class=\"row\"><div class=\"label\">%s</div><div class=\"value\">%s</div></div>
                <div class=\"row\"><div class=\"label\">Thời gian</div><div class=\"value\">%s</div></div>
                <div class=\"row\"><div class=\"label\">Link Jitsi</div><div class=\"value\"><a href=\"%s\">Tham gia</a></div></div>
                </div>
                <div class=\"cta\"><a class=\"button\" href=\"%s\">Xem chi tiết</a></div>
                </div><div class=\"footer\">© 2025</div></div></body></html>
                """
                .formatted(forMentor ? "Bạn" : "Mentor", roleText, counterpart, time, link,
                        "https://skillverse.vn/bookings/" + booking.getId());
    }

    private String buildBookingReminderHtml(Booking booking, boolean forMentor) {
        String counterpart = forMentor ? getDisplayName(booking.getLearner()) : getDisplayName(booking.getMentor());
        String roleText = forMentor ? "Học viên" : "Mentor";
        String time = formatTimeVN(booking.getStartTime());
        String link = booking.getMeetingLink() != null ? booking.getMeetingLink() : "-";
        return """
                <html><head><meta charset=\"UTF-8\" /><style>
                body{font-family:Inter,system-ui,-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;background:#f8fafc;margin:0;padding:0}
                .container{max-width:640px;margin:24px auto;background:#ffffff;border-radius:16px;box-shadow:0 10px 25px rgba(2,6,23,0.08);overflow:hidden}
                .header{background:linear-gradient(135deg,#f59e0b,#0ea5e9);padding:24px;display:flex;justify-content:center;align-items:center}
                .logo{width:44px;height:44px;border-radius:10px;overflow:hidden}
                .content{padding:24px;color:#111827}
                .card{border:1px solid #e5e7eb;border-radius:12px;padding:16px;margin-top:12px}
                .row{display:flex;justify-content:space-between;margin:6px 0}
                .label{color:#6b7280}.value{font-weight:600}
                .cta{margin-top:20px}
                .button{background:#f59e0b;color:#fff;text-decoration:none;padding:12px 16px;border-radius:10px;font-weight:700}
                .footer{padding:16px;text-align:center;color:#6b7280;font-size:12px}
                </style></head>
                <body><div class=\"container\"><div class=\"header\"><img class=\"logo\" src=\"cid:skillverse-logo\" /></div>
                <div class=\"content\"><h2>Đến giờ mentoring</h2>
                <div class=\"card\"><div class=\"row\"><div class=\"label\">%s</div><div class=\"value\">%s</div></div>
                <div class=\"row\"><div class=\"label\">Thời gian</div><div class=\"value\">%s</div></div>
                <div class=\"row\"><div class=\"label\">Link Jitsi</div><div class=\"value\"><a href=\"%s\">Tham gia</a></div></div>
                </div>
                <div class=\"cta\"><a class=\"button\" href=\"%s\">Vào phòng</a></div>
                </div><div class=\"footer\">© 2025</div></div></body></html>
                """
                .formatted(roleText, counterpart, time, link, link);
    }

    private String getDisplayName(User user) {
        String fn = user.getFirstName();
        String ln = user.getLastName();
        String built = ((fn != null ? fn : "") + (ln != null ? " " + ln : "")).trim();
        return built.isEmpty() ? ("User #" + user.getId()) : built;
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
        Booking saved = bookingRepository.save(booking);

        try {
            walletService.unfreezeForBooking(learnerId, saved.getPriceVnd(), saved.getId());
        } catch (Exception e) {
            log.warn("Unfreeze booking funds failed: {}", e.getMessage());
        }

        notificationService.createNotification(
                saved.getMentor().getId(),
                "Booking bị hủy",
                "Learner đã hủy lịch trước 1 ngày",
                NotificationType.BOOKING_CANCELLED,
                saved.getId().toString(),
                learnerId);

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
        var review = com.exe.skillverse_backend.portfolio_service.entity.MentorReview.builder()
                .user(booking.getLearner())
                .mentor(booking.getMentor())
                .feedback(comment != null ? comment : "")
                .skillEndorsed(skillEndorsed)
                .rating(stars)
                .isVerified(false)
                .isPublic(true)
                .build();
        mentorReviewRepository.save(review);

        notificationService.createNotification(
                booking.getMentor().getId(),
                "Bạn nhận được đánh giá",
                (comment != null ? comment : "") + " (" + stars + "/5)",
                NotificationType.MENTOR_REVIEW_RECEIVED,
                booking.getId().toString(),
                learnerId);

        mentorProfileRepository.findByUserId(booking.getMentor().getId()).ifPresent(profile -> {
            var reviews = mentorReviewRepository.findByMentorIdOrderByCreatedAtDesc(booking.getMentor().getId());
            int count = reviews.size();
            double avg = count == 0 ? 0.0
                    : reviews.stream().filter(r -> r.getRating() != null).mapToInt(r -> r.getRating()).average()
                            .orElse(0.0);
            profile.setRatingCount(count);
            profile.setRatingAverage(avg);
            if (stars != null && stars == 5) {
                long fiveStarCount = mentorReviewRepository.countByMentorIdAndRating(booking.getMentor().getId(), 5);
                java.util.Set<String> badges = parseBadges(profile.getBadges());
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
            profile.setUpdatedAt(java.time.LocalDateTime.now());
            mentorProfileRepository.save(profile);
        });
    }

    public Page<BookingResponse> getUserBookings(Long userId, boolean mentorView, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại"));
        List<BookingStatus> statuses = List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.ONGOING,
                BookingStatus.COMPLETED);
        Page<Booking> page = mentorView
                ? bookingRepository.findByMentorAndStatusInOrderByStartTimeDesc(user, statuses, pageable)
                : bookingRepository.findByLearnerAndStatusInOrderByStartTimeDesc(user, statuses, pageable);
        return page.map(this::toResponse);
    }

    private BookingResponse toResponse(Booking booking) {
        String mentorName = null;
        String mentorAvatar = null;
        String learnerName = null;
        String learnerAvatar = null;

        try {
            var mentor = booking.getMentor();
            var learner = booking.getLearner();

            // 1. Try MentorProfile for mentor info
            var mentorProfile = mentorProfileRepository.findById(mentor.getId()).orElse(null);
            if (mentorProfile != null) {
                if (mentorProfile.getFullName() != null && !mentorProfile.getFullName().isEmpty()) {
                    mentorName = mentorProfile.getFullName();
                }
                if (mentorProfile.getAvatarUrl() != null && !mentorProfile.getAvatarUrl().isEmpty()) {
                    mentorAvatar = mentorProfile.getAvatarUrl();
                }
            }

            // 2. If not found, try UserProfile (general profile)
            if (mentorName == null && userProfileService.hasProfile(mentor.getId())) {
                var mProfile = userProfileService.getProfile(mentor.getId());
                mentorName = mProfile.getFullName();
                if (mentorAvatar == null)
                    mentorAvatar = mProfile.getAvatarMediaUrl();
            }

            // Learner info from UserProfile
            if (userProfileService.hasProfile(learner.getId())) {
                var lProfile = userProfileService.getProfile(learner.getId());
                learnerName = lProfile.getFullName();
                learnerAvatar = lProfile.getAvatarMediaUrl();
            }

            // Fallbacks to User entity
            if (mentorName == null || mentorName.isBlank()) {
                mentorName = mentor.getFullName();
            }
            if (mentorAvatar == null || mentorAvatar.isBlank()) {
                mentorAvatar = mentor.getAvatarUrl();
            }
            if (learnerName == null || learnerName.isBlank()) {
                learnerName = learner.getFullName();
            }
            if (learnerAvatar == null || learnerAvatar.isBlank()) {
                learnerAvatar = learner.getAvatarUrl();
            }
        } catch (Exception e) {
            log.warn("Failed to enrich booking user info: {}", e.getMessage());
        }

        return BookingResponse.builder()
                .id(booking.getId())
                .mentorId(booking.getMentor().getId())
                .learnerId(booking.getLearner().getId())
                .startTime(booking.getStartTime())
                .endTime(booking.getEndTime())
                .durationMinutes(booking.getDurationMinutes())
                .status(booking.getStatus())
                .priceVnd(booking.getPriceVnd())
                .meetingLink(booking.getMeetingLink())
                .paymentReference(booking.getPaymentReference())
                .mentorName(mentorName)
                .mentorAvatar(mentorAvatar)
                .learnerName(learnerName)
                .learnerAvatar(learnerAvatar)
                .build();
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

    private java.util.Set<String> parseBadges(String badgesJson) {
        try {
            if (badgesJson == null || badgesJson.isBlank())
                return new java.util.HashSet<>();
            java.util.List<String> list = objectMapper.readValue(badgesJson,
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {
                    });
            return new java.util.HashSet<>(list);
        } catch (Exception e) {
            return new java.util.HashSet<>();
        }
    }

    private String toBadgesJson(java.util.Set<String> badges) {
        try {
            return objectMapper.writeValueAsString(new java.util.ArrayList<>(badges));
        } catch (Exception e) {
            return "[]";
        }
    }
}
