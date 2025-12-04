package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.dto.purchasedto.CoursePurchaseDTO;
import com.exe.skillverse_backend.course_service.dto.purchasedto.CoursePurchaseRequestDTO;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CoursePurchase;
import com.exe.skillverse_backend.course_service.entity.enums.PurchaseStatus;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.entity.enums.EntitlementSource;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.CoursePurchaseRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.service.CoursePurchaseService;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.user_service.service.UserProfileService;
import com.exe.skillverse_backend.payment_service.dto.request.CreatePaymentRequest;
import com.exe.skillverse_backend.payment_service.dto.response.CreatePaymentResponse;
import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import com.exe.skillverse_backend.payment_service.event.PaymentSuccessEvent;
import com.exe.skillverse_backend.payment_service.service.PaymentService;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.service.EmailService;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import com.exe.skillverse_backend.wallet_service.repository.WalletTransactionRepository;
import com.exe.skillverse_backend.payment_service.service.InvoiceService;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoursePurchaseServiceImpl implements CoursePurchaseService {

    private final CourseRepository courseRepository;
    private final CoursePurchaseRepository coursePurchaseRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final UserProfileService userProfileService;
    private final EmailService emailService;
    private final InvoiceService invoiceService;
    private final WalletTransactionRepository walletTransactionRepository;

    @Override
    @Transactional
    public CreatePaymentResponse createPurchaseIntent(Long userId, CoursePurchaseRequestDTO request) {
        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new NotFoundException("Course not found"));

        if (coursePurchaseRepository.existsByUserIdAndCourseIdAndStatus(userId, request.getCourseId(), PurchaseStatus.PAID)) {
            throw new IllegalStateException("You have already purchased this course");
        }

        String metadata = String.format("{\"courseId\":%d,\"userId\":%d}", course.getId(), userId);

        String successUrl = request.getReturnUrl() != null ? request.getReturnUrl() : "http://localhost:5173/payment/success";
        String cancelUrl = request.getCancelUrl() != null ? request.getCancelUrl() : "http://localhost:5173/payment/cancel";

        CreatePaymentRequest paymentRequest = CreatePaymentRequest.builder()
                .amount(course.getPrice())
                .currency("VND")
                .type(PaymentTransaction.PaymentType.COURSE_PURCHASE)
                .paymentMethod(PaymentTransaction.PaymentMethod.PAYOS)
                .description("Purchase course: " + course.getTitle())
                .metadata(metadata)
                .successUrl(successUrl)
                .cancelUrl(cancelUrl)
                .build();

        return paymentService.createPayment(userId, paymentRequest);
    }

    @Override
    @Transactional
    public CoursePurchaseDTO purchaseWithWallet(Long userId, CoursePurchaseRequestDTO request) {
        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new NotFoundException("Course not found"));

        if (coursePurchaseRepository.existsByUserIdAndCourseIdAndStatus(userId, request.getCourseId(), PurchaseStatus.PAID)) {
            throw new IllegalStateException("You have already purchased this course");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Deduct from user wallet
        walletService.deductCash(userId, course.getPrice(), "Purchase course: " + course.getTitle(), "COURSE_PURCHASE", "COURSE_" + course.getId());

        // Pay mentor (80%)
        BigDecimal mentorShare = course.getPrice().multiply(new BigDecimal("0.80"));
        walletService.payMentorForCourse(course.getAuthor().getId(), mentorShare, course.getId());

        // Create purchase record
        CoursePurchase purchase = CoursePurchase.builder()
                .user(user)
                .course(course)
                .price(course.getPrice())
                .currency("VND")
                .status(PurchaseStatus.PAID)
                .purchasedAt(Instant.now())
                .build();

        purchase = coursePurchaseRepository.save(purchase);

        // Auto-enroll user
        if (!courseEnrollmentRepository.existsByCourseIdAndUserId(course.getId(), userId)) {
            CourseEnrollment enrollment = CourseEnrollment.builder()
                    .user(user)
                    .course(course)
                    .status(EnrollmentStatus.ENROLLED)
                    .progressPercent(0)
                    .entitlementSource(EntitlementSource.PURCHASE)
                    .entitlementRef("PURCHASE_" + purchase.getId())
                    .enrollDate(Instant.now())
                    .build();
            enrollment.setId(new CourseEnrollment.CourseEnrollmentId(userId, course.getId()));
            courseEnrollmentRepository.save(enrollment);
        }

        try {
            notificationService.createNotification(
                    userId,
                    "Mua khóa học thành công",
                    "Bạn đã mua khóa học '" + course.getTitle() + "'",
                    NotificationType.SYSTEM,
                    "COURSE_" + course.getId()
            );
        } catch (Exception e) {
            log.warn("Failed to create notification for wallet course purchase: {}", e.getMessage());
        }

        try {
            java.util.Optional<WalletTransaction> walletTxOpt = walletTransactionRepository
                    .findByReferenceIdAndReferenceType("COURSE_" + course.getId(), "COURSE_PURCHASE");
            if (walletTxOpt.isPresent()) {
                WalletTransaction walletTx = walletTxOpt.get();
                byte[] pdf = invoiceService.generateWalletTransactionInvoice(walletTx);
                String subject = "🎉 Mua khóa học thành công - " + course.getTitle();
                String html = buildWalletCoursePurchaseEmail(getDisplayName(user), course.getTitle(), course.getPrice(),
                        String.valueOf(walletTx.getTransactionId()));
                emailService.sendHtmlEmailWithAttachment(user.getEmail(), subject, html,
                        "Hoa_don_WAL-" + walletTx.getTransactionId() + ".pdf", pdf, "application/pdf");
            } else {
                log.warn("Wallet transaction not found for course purchase invoice: user={}, courseId={}", userId, course.getId());
            }
        } catch (Exception e) {
            log.warn("Failed to send wallet course purchase email/invoice: {}", e.getMessage());
        }

        return mapToDTO(purchase);
    }

    @Override
    @EventListener
    @Transactional
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        PaymentTransaction transaction = event.getTransaction();
        if (transaction.getType() == PaymentTransaction.PaymentType.COURSE_PURCHASE) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.readTree(transaction.getMetadata());
                Long courseId = node.get("courseId").asLong();
                Long userId = node.get("userId").asLong();

                Course course = courseRepository.findById(courseId)
                        .orElseThrow(() -> new NotFoundException("Course not found"));
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new NotFoundException("User not found"));

                if (coursePurchaseRepository.existsByUserIdAndCourseIdAndStatus(userId, courseId, PurchaseStatus.PAID)) {
                    log.info("Course already purchased, skipping");
                    return;
                }

                // Pay mentor (80%)
                BigDecimal mentorShare = course.getPrice().multiply(new BigDecimal("0.80"));
                walletService.payMentorForCourse(course.getAuthor().getId(), mentorShare, course.getId());

                CoursePurchase purchase = CoursePurchase.builder()
                        .user(user)
                        .course(course)
                        .price(course.getPrice())
                        .currency("VND")
                        .status(PurchaseStatus.PAID)
                        .purchasedAt(Instant.now())
                        .build();

                CoursePurchase savedPurchase = coursePurchaseRepository.save(purchase);

                // Auto-enroll user
                if (!courseEnrollmentRepository.existsByCourseIdAndUserId(courseId, userId)) {
                    CourseEnrollment enrollment = CourseEnrollment.builder()
                            .user(user)
                            .course(course)
                            .status(EnrollmentStatus.ENROLLED)
                            .progressPercent(0)
                            .entitlementSource(EntitlementSource.PURCHASE)
                            .entitlementRef("PURCHASE_" + savedPurchase.getId())
                            .enrollDate(Instant.now())
                            .build();
                    enrollment.setId(new CourseEnrollment.CourseEnrollmentId(userId, courseId));
                    courseEnrollmentRepository.save(enrollment);
                }

                log.info("Course purchase completed via payment gateway for user {} course {}", userId, courseId);

            } catch (Exception e) {
                log.error("Error handling course purchase payment success", e);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<CoursePurchaseDTO> getMentorPurchases(Long mentorId, org.springframework.data.domain.Pageable pageable) {
        return coursePurchaseRepository.findByCourse_Author_Id(mentorId, pageable)
                .map(this::mapToDTO);
    }

    private String buildWalletCoursePurchaseEmail(String name, String courseTitle, java.math.BigDecimal amount, String ref) {
        String amountStr = amount != null ? amount.toPlainString() + " VND" : "-";
        return """
                <html>
                <head>
                    <meta charset=\"UTF-8\" />
                    <style>
                        body{font-family:Inter,system-ui,-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;background:#f8fafc;margin:0;padding:0}
                        .container{max-width:640px;margin:24px auto;background:#ffffff;border-radius:16px;box-shadow:0 10px 25px rgba(2,6,23,0.08);overflow:hidden}
                        .header{background:linear-gradient(135deg,#4f46e5,#0ea5e9);padding:24px;display:flex;justify-content:center;align-items:center}
                        .logo{width:44px;height:44px;border-radius:10px;overflow:hidden}
                        .content{padding:24px;color:#111827}
                        .pill{display:inline-block;background:#ecfeff;color:#0ea5e9;padding:6px 12px;border-radius:999px;font-size:12px;font-weight:600;margin-bottom:12px}
                        .card{border:1px solid #e5e7eb;border-radius:12px;padding:16px;margin-top:12px}
                        .row{display:flex;justify-content:space-between;margin:6px 0}
                        .label{color:#6b7280}
                        .value{font-weight:600}
                        .cta{margin-top:20px}
                        .button{background:#4f46e5;color:#fff;text-decoration:none;padding:12px 16px;border-radius:10px;font-weight:700}
                        .footer{padding:16px;text-align:center;color:#6b7280;font-size:12px}
                    </style>
                </head>
                <body>
                    <div class=\"container\">
                        <div class=\"header\"><img class=\"logo\" src=\"cid:skillverse-logo\" /></div>
                        <div class=\"content\">
                            <div class=\"pill\">Mua khóa học thành công</div>
                            <h2>Chúc mừng, %s!</h2>
                            <p>Bạn đã mua khóa học <strong>%s</strong> qua My-Wallet. Hóa đơn PDF được đính kèm.</p>
                            <div class=\"card\">
                                <div class=\"row\"><div class=\"label\">Khóa học</div><div class=\"value\">%s</div></div>
                                <div class=\"row\"><div class=\"label\">Số tiền</div><div class=\"value\">%s</div></div>
                                <div class=\"row\"><div class=\"label\">Mã giao dịch ví</div><div class=\"value\">WAL-%s</div></div>
                            </div>
                            <div class=\"cta\"><a class=\"button\" href=\"https://skillverse.vn/dashboard\">Bắt đầu học</a></div>
                        </div>
                        <div class=\"footer\">© 2025</div>
                    </div>
                </body>
                </html>
                """.formatted(name, courseTitle, courseTitle, amountStr, ref);
    }

    private String getDisplayName(com.exe.skillverse_backend.auth_service.entity.User user) {
        if (user == null) return "Learner";
        String fn = user.getFirstName();
        String ln = user.getLastName();
        String built = ((fn != null ? fn : "") + (ln != null ? " " + ln : "")).trim();
        return built.isEmpty() ? ("User #" + user.getId()) : built;
    }

    private CoursePurchaseDTO mapToDTO(CoursePurchase purchase) {
        String buyerName = buildFullName(purchase.getUser());
        String avatarUrl = getAvatarUrl(purchase.getUser());
        CoursePurchaseDTO dto = new CoursePurchaseDTO(
                purchase.getId(),
                purchase.getCourse().getId(),
                purchase.getUser().getId(),
                purchase.getStatus().name(),
                purchase.getPrice(),
                purchase.getCurrency(),
                purchase.getPurchasedAt(),
                purchase.getCouponCode(),
                buyerName,
                avatarUrl,
                purchase.getCourse().getTitle()
        );
        return dto;
    }

    private String buildFullName(User user) {
        try {
            if (userProfileService.hasProfile(user.getId())) {
                var profile = userProfileService.getProfile(user.getId());
                if (profile.getFullName() != null && !profile.getFullName().isBlank()) {
                    return profile.getFullName();
                }
            }
        } catch (Exception ignored) {}
        String fn = user.getFirstName();
        String ln = user.getLastName();
        String built = ((fn != null ? fn : "") + (ln != null ? " " + ln : "")).trim();
        return built.isEmpty() ? ("User #" + user.getId()) : built;
    }

    private String getAvatarUrl(User user) {
        try {
            if (userProfileService.hasProfile(user.getId())) {
                var profile = userProfileService.getProfile(user.getId());
                String avatar = profile.getAvatarMediaUrl();
                if (avatar != null && !avatar.isBlank()) return avatar;
            }
        } catch (Exception ignored) {}
        return user.getAvatarUrl();
    }
}
