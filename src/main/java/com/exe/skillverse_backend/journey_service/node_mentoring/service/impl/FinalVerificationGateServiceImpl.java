package com.exe.skillverse_backend.journey_service.node_mentoring.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.ai_service.entity.UserRoadmapProgress;
import com.exe.skillverse_backend.ai_service.repository.UserRoadmapProgressRepository;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.journey_service.entity.Journey.JourneyStatus;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.AssessJourneyOutputRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.ConfirmJourneyCompletionRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.SubmitEvidenceReportRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.SubmitJourneyOutputAssessmentRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.JourneyCompletionGateResponse;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.JourneyCompletionGateResponse.FinalGateStatus;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.JourneyCompletionReportResponse;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.JourneyOutputAssessmentResponse;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.VerificationEvidenceReportResponse;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyCompletionReport;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyCompletionReport.GateDecision;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyOutputAssessment;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyOutputAssessment.AssessmentStatus;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeSubmission;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.VerificationEvidenceReport;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.JourneyCompletionReportRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.JourneyOutputAssessmentRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.RoadmapNodeSubmissionRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.VerificationEvidenceReportRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.service.FinalVerificationGateService;
import com.exe.skillverse_backend.journey_service.node_mentoring.service.RoadmapNodeResolver;
import com.exe.skillverse_backend.mentor_booking_service.entity.Booking;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingRepository;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.portfolio_service.entity.UserVerifiedSkill;
import com.exe.skillverse_backend.portfolio_service.repository.UserVerifiedSkillRepository;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.exe.skillverse_backend.shared.util.SkillNameUtils;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinalVerificationGateServiceImpl implements FinalVerificationGateService {

    private static final int MAX_VERIFICATION_ATTEMPTS = 3;
    private static final int REVERIFY_COOLDOWN_DAYS = 7;
    private static final BigDecimal MENTOR_BOOKING_SHARE_RATE = new BigDecimal("0.80");

    private static final List<BookingStatus> ASSIGNED_MENTOR_STATUSES = List.of(
            BookingStatus.CONFIRMED,
            BookingStatus.ONGOING,
            BookingStatus.MENTORING_ACTIVE,
            BookingStatus.PENDING_COMPLETION);

    // Completion report authorization is wider: a mentor who has COMPLETED a
    // JOURNEY_MENTORING booking must still be able to submit the gate report.
    private static final List<BookingStatus> REPORT_ELIGIBLE_STATUSES = List.of(
            BookingStatus.CONFIRMED,
            BookingStatus.ONGOING,
            BookingStatus.MENTORING_ACTIVE,
            BookingStatus.PENDING_COMPLETION,
            BookingStatus.COMPLETED);

    private final RoadmapNodeResolver resolver;
    private final JourneyCompletionReportRepository completionReportRepo;
    private final JourneyOutputAssessmentRepository outputAssessmentRepo;
    private final BookingRepository bookingRepository;
    private final JourneyRepository journeyRepository;

    // V3 Phase 2 dependencies
    private final VerificationEvidenceReportRepository evidenceReportRepo;
    private final UserVerifiedSkillRepository userVerifiedSkillRepo;
    private final RoadmapNodeSubmissionRepository submissionRepo;
    private final UserRoadmapProgressRepository progressRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Value("${jitsi.base-url:https://meet.jit.si}")
    private String jitsiBaseUrl;

    // ─── Gate evaluation ──────────────────────────────────────────────────────

    @Override
    public JourneyCompletionGateResponse evaluateGate(Long callerId, Long journeyId) {
        Journey journey = resolver.resolveJourney(journeyId);
        requireOwnerOrAssignedMentor(callerId, journey);
        return buildGateResponse(journey);
    }

    @Override
    public void requireGatePassed(Journey journey) {
        JourneyCompletionGateResponse gate = buildGateResponse(journey);
        if (gate.getFinalGateStatus() == FinalGateStatus.BLOCKED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Journey completion blocked by final verification gate: "
                            + String.join("; ", gate.getBlockingReasons()));
        }
    }

    private JourneyCompletionGateResponse buildGateResponse(Journey journey) {
        boolean requireFinal = Boolean.TRUE.equals(journey.getFinalVerificationRequired());
        boolean requireOutput = Boolean.TRUE.equals(journey.getJourneyOutputVerificationRequired());

        if (!requireFinal) {
            return JourneyCompletionGateResponse.builder()
                    .journeyId(journey.getId())
                    .finalGateStatus(FinalGateStatus.NOT_REQUIRED)
                    .finalVerificationRequired(false)
                    .journeyOutputVerificationRequired(requireOutput)
                    .hasPassCompletionReport(false)
                    .outputAssessmentApproved(false)
                    .blockingReasons(List.of())
                    .build();
        }

        boolean hasPass = completionReportRepo.existsByJourneyIdAndGateDecision(
                journey.getId(), GateDecision.PASS);
        boolean outputApproved = !requireOutput
                || outputAssessmentRepo.findFirstByJourneyIdOrderBySubmittedAtDesc(journey.getId())
                        .map(a -> a.getAssessmentStatus() == AssessmentStatus.APPROVED)
                        .orElse(false);

        List<String> reasons = new ArrayList<>();
        if (!hasPass) {
            reasons.add("A mentor completion report with gateDecision=PASS is required");
        }
        if (requireOutput && !outputApproved) {
            reasons.add("The journey output assessment must be APPROVED");
        }

        FinalGateStatus status = reasons.isEmpty() ? FinalGateStatus.PASSED : FinalGateStatus.BLOCKED;
        return JourneyCompletionGateResponse.builder()
                .journeyId(journey.getId())
                .finalGateStatus(status)
                .finalVerificationRequired(true)
                .journeyOutputVerificationRequired(requireOutput)
                .hasPassCompletionReport(hasPass)
                .outputAssessmentApproved(outputApproved)
                .blockingReasons(reasons)
                .build();
    }

    // ─── Completion report ────────────────────────────────────────────────────

    @Override
    @Transactional
    public JourneyCompletionReportResponse submitCompletionReport(Long actingMentorId, Long journeyId,
                                                                  ConfirmJourneyCompletionRequest request) {
        Journey journey = resolver.resolveJourneyWithRoadmap(journeyId);
        requireAssignedJourneyMentor(actingMentorId, journeyId);

        if (completionReportRepo.existsByJourneyIdAndGateDecision(journeyId, GateDecision.PASS)) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "A PASS completion report already exists for journey " + journeyId);
        }

        JourneyCompletionReport report = completionReportRepo
                .findFirstByJourneyIdOrderByConfirmedAtDesc(journeyId)
                .orElseGet(() -> JourneyCompletionReport.builder()
                        .journeyId(journeyId)
                        .mentorId(actingMentorId)
                        .build());

        report.setGateDecision(request.getGateDecision());
        report.setCompletionNote(request.getCompletionNote());
        report.setBookingId(request.getBookingId());

        JourneyCompletionReport saved = completionReportRepo.save(report);
        if (request.getGateDecision() == GateDecision.PASS) {
            completeJourneyIfGatePassed(journey, actingMentorId, saved.getBookingId(), saved.getCompletionNote());
        } else if (request.getGateDecision() == GateDecision.FAIL) {
            journey.setStatus(JourneyStatus.ACTIVE);
            journeyRepository.save(journey);
        }

        return JourneyCompletionReportResponse.from(saved);
    }

    // ─── Output assessment ────────────────────────────────────────────────────

    @Override
    @Transactional
    public JourneyOutputAssessmentResponse submitOutputAssessment(Long learnerId, Long journeyId,
                                                                  SubmitJourneyOutputAssessmentRequest request) {
        Journey journey = resolver.resolveJourneyWithRoadmap(journeyId);
        resolver.ensureLearnerOwns(journey, learnerId);

        boolean hasMentorBooking = bookingRepository.existsActiveJourneyBookingForAnyMentor(
                journeyId, ASSIGNED_MENTOR_STATUSES);
        if (!hasMentorBooking) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Output assessment requires an active mentor booking for this journey");
        }

        JourneyOutputAssessment latest = outputAssessmentRepo.findFirstByJourneyIdOrderBySubmittedAtDesc(journeyId)
                .orElse(null);

        if (latest != null && latest.getAssessmentStatus() == AssessmentStatus.PENDING) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Final assessment đã được nộp và đang chờ mentor đánh giá. "
                            + "Bạn chỉ có thể nộp lại khi mentor yêu cầu làm lại.");
        }
        if (latest != null && latest.getAssessmentStatus() == AssessmentStatus.APPROVED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Final assessment đã được duyệt, không thể cập nhật thêm.");
        }

        JourneyOutputAssessment a = JourneyOutputAssessment.builder()
                .journeyId(journeyId)
                .learnerId(learnerId)
                .build();

        a.setSubmissionText(request.getSubmissionText());
        a.setEvidenceUrl(request.getEvidenceUrl());
        a.setEvidencePublicId(request.getEvidencePublicId());
        a.setEvidenceResourceType(request.getEvidenceResourceType());
        a.setAttachmentUrl(request.getAttachmentUrl());
        a.setAttachmentPublicId(request.getAttachmentPublicId());
        a.setAttachmentResourceType(request.getAttachmentResourceType());
        a.setAssessmentStatus(AssessmentStatus.PENDING);
        a.setAssessedAt(null);
        a.setScore(null);
        a.setFeedback(null);

        return JourneyOutputAssessmentResponse.from(outputAssessmentRepo.save(a));
    }

    @Override
    @Transactional
    public JourneyOutputAssessmentResponse assessOutput(Long actingMentorId, Long journeyId,
                                                        AssessJourneyOutputRequest request) {
        Journey journey = resolver.resolveJourneyWithRoadmap(journeyId);
        requireAssignedJourneyMentor(actingMentorId, journeyId);

        JourneyOutputAssessment a = outputAssessmentRepo.findFirstByJourneyIdOrderBySubmittedAtDesc(journeyId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "No output assessment to assess for journey " + journeyId));

        if (a.getAssessmentStatus() == AssessmentStatus.APPROVED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "This output assessment is already APPROVED");
        }
        if (request.getAssessmentStatus() == AssessmentStatus.PENDING) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "assessmentStatus must be APPROVED or REJECTED");
        }

        a.setMentorId(actingMentorId);
        a.setAssessmentStatus(request.getAssessmentStatus());
        a.setFeedback(request.getFeedback());
        a.setScore(request.getScore());
        a.setAssessedAt(Instant.now());

        JourneyOutputAssessment saved = outputAssessmentRepo.save(a);
        if (request.getAssessmentStatus() == AssessmentStatus.APPROVED) {
            completeJourneyIfGatePassed(journey, actingMentorId, null, request.getFeedback());
        } else if (request.getAssessmentStatus() == AssessmentStatus.REJECTED) {
            journey.setStatus(JourneyStatus.ACTIVE);
            journeyRepository.save(journey);
        }

        return JourneyOutputAssessmentResponse.from(saved);
    }

    @Override
    public JourneyOutputAssessmentResponse getLatestOutputAssessment(Long callerId, Long journeyId) {
        Journey journey = resolver.resolveJourney(journeyId);
        requireOwnerOrAssignedMentor(callerId, journey);
        return outputAssessmentRepo.findFirstByJourneyIdOrderBySubmittedAtDesc(journeyId)
                .map(JourneyOutputAssessmentResponse::from)
                .orElse(null);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // V3 PHASE 2: ROADMAP_MENTORING Final Verification Meeting Flow
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public String createFinalMeetingLink(Long callerId, Long journeyId) {
        Journey journey = resolver.resolveJourney(journeyId);
        requireOwnerOrAssignedMentor(callerId, journey);

        Booking booking = bookingRepository.findActiveRoadmapMentoringBooking(journeyId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Không tìm thấy booking ROADMAP_MENTORING đang hoạt động cho journey " + journeyId));

        // Enforce 7-day cooldown after FAIL
        if (booking.getNextVerifyAllowedAt() != null
                && LocalDateTime.now().isBefore(booking.getNextVerifyAllowedAt())) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Chưa đến thời gian cho phép verify lại. Vui lòng chờ đến "
                            + booking.getNextVerifyAllowedAt());
        }

        // Check max attempts
        int attempts = booking.getVerificationAttempts() != null ? booking.getVerificationAttempts() : 0;
        if (attempts >= MAX_VERIFICATION_ATTEMPTS) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Đã vượt quá số lần verify tối đa (" + MAX_VERIFICATION_ATTEMPTS + "). Booking đã bị hủy.");
        }

        // Generate Jitsi link
        String roomName = "sv-verify-" + journeyId + "-" + booking.getId()
                + "-" + UUID.randomUUID().toString().substring(0, 8);
        String meetingLink = jitsiBaseUrl + "/" + roomName;

        booking.setMeetingLink(meetingLink);
        bookingRepository.save(booking);

        log.info("Created final verification meeting link for journey={}, booking={}: {}",
                journeyId, booking.getId(), meetingLink);
        return meetingLink;
    }

    @Override
    @Transactional
    public VerificationEvidenceReportResponse submitEvidenceReportAndVerdict(
            Long mentorId, Long journeyId, SubmitEvidenceReportRequest request) {

        Journey journey = resolver.resolveJourney(journeyId);
        requireAssignedJourneyMentor(mentorId, journeyId);

        Booking booking = bookingRepository.findActiveRoadmapMentoringBooking(journeyId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Không tìm thấy booking ROADMAP_MENTORING đang hoạt động cho journey " + journeyId));

        // Validate FAIL-specific fields
        if (request.getGateDecision() == GateDecision.FAIL) {
            if (request.getWeakNodeIds() == null || request.getWeakNodeIds().isEmpty()) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "weakNodeIds là bắt buộc khi verdict = FAIL");
            }
            if (request.getFailReason() == null || request.getFailReason().isBlank()) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "failReason là bắt buộc khi verdict = FAIL");
            }
        }

        int currentAttempt = (booking.getVerificationAttempts() != null ? booking.getVerificationAttempts() : 0) + 1;

        VerificationEvidenceReport report = VerificationEvidenceReport.builder()
                .journeyId(journeyId)
                .bookingId(booking.getId())
                .mentorId(mentorId)
                .meetingJitsiLink(booking.getMeetingLink())
                .meetingDurationMinutes(request.getMeetingDurationMinutes())
                .summaryReport(request.getSummaryReport())
                .assignmentsGiven(toJson(request.getAssignmentsGiven()))
                .weakNodeIds(toJson(request.getWeakNodeIds()))
                .failReason(request.getFailReason())
                .gateDecision(request.getGateDecision())
                .attemptNumber(currentAttempt)
                .build();

        VerificationEvidenceReport saved = evidenceReportRepo.save(report);

        if (request.getGateDecision() == GateDecision.PASS) {
            handleVerificationPass(journey, booking, mentorId, saved);
        } else if (request.getGateDecision() == GateDecision.FAIL) {
            handleVerificationFail(journey, booking, currentAttempt, request.getWeakNodeIds(), request.getFailReason());
        }

        return VerificationEvidenceReportResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VerificationEvidenceReportResponse> getVerificationHistory(Long callerId, Long journeyId) {
        Journey journey = resolver.resolveJourney(journeyId);
        requireOwnerOrAssignedMentor(callerId, journey);

        return evidenceReportRepo.findByJourneyIdOrderByAttemptNumberAsc(journeyId)
                .stream()
                .map(VerificationEvidenceReportResponse::from)
                .toList();
    }

    // ─── PASS handler ─────────────────────────────────────────────────────────

    private void handleVerificationPass(Journey journey, Booking booking, Long mentorId,
                                         VerificationEvidenceReport report) {
        String skillName = upsertVerifiedSkill(
                journey,
                mentorId,
                booking.getId(),
                report.getSummaryReport());

        JourneyCompletionReport gateReport = JourneyCompletionReport.builder()
                .journeyId(journey.getId())
                .mentorId(mentorId)
                .bookingId(booking.getId())
                .gateDecision(GateDecision.PASS)
                .completionNote(report.getSummaryReport())
                .build();
        completionReportRepo.save(gateReport);

        markAllNodesCompleted(journey);
        journey.setStatus(JourneyStatus.COMPLETED_VERIFIED);
        journey.setProgressPercentage(100);
        journey.setCompletedAt(Instant.now());
        journeyRepository.save(journey);

        booking.setStatus(BookingStatus.COMPLETED);
        booking.setVerificationAttempts(
                (booking.getVerificationAttempts() != null ? booking.getVerificationAttempts() : 0) + 1);
        bookingRepository.save(booking);

        // 5. Capture learner escrow, then pay 80% to mentor. The remaining 20%
        // is retained by the platform.
        try {
            BigDecimal bookingAmount = booking.getPriceVnd();
            BigDecimal mentorPayout = bookingAmount.multiply(MENTOR_BOOKING_SHARE_RATE);
            walletService.chargeFrozenForBooking(booking.getLearner().getId(), bookingAmount, booking.getId());
            walletService.payMentorForBooking(booking.getMentor().getId(), mentorPayout, booking.getId());
        } catch (Exception e) {
            log.error("Failed to process escrow release for booking {}: {}", booking.getId(), e.getMessage());
        }

        User mentor = userRepository.findById(mentorId).orElse(null);
        String mentorName = mentor != null ? mentor.getFullName() : "Mentor";

        notificationService.createNotification(
                journey.getUser().getId(),
                "🎉 Skill đã được xác thực!",
                skillName + " đã được xác thực bởi " + mentorName + ". Xem trong Portfolio!",
                NotificationType.BOOKING_COMPLETED,
                journey.getId().toString(),
                mentorId);

        notificationService.createNotification(
                mentorId,
                "✅ Xác thực hoàn tất",
                "Bạn đã xác thực skill " + skillName + " cho học viên.",
                NotificationType.BOOKING_COMPLETED,
                booking.getId().toString(),
                journey.getUser().getId());

        log.info("PASS: Journey {} verified by mentor {}. Skill '{}' added to user {} portfolio.",
                journey.getId(), mentorId, skillName, journey.getUser().getId());
    }

    // ─── FAIL handler ─────────────────────────────────────────────────────────

    private void completeJourneyIfGatePassed(Journey journey, Long mentorId, Long bookingId, String note) {
        if (journey == null || journey.getId() == null) {
            return;
        }

        JourneyCompletionGateResponse gate = buildGateResponse(journey);
        if (gate.getFinalGateStatus() != FinalGateStatus.PASSED) {
            return;
        }

        Long resolvedBookingId = bookingId != null
                ? bookingId
                : bookingRepository.findActiveRoadmapMentoringBooking(journey.getId())
                        .map(Booking::getId)
                        .orElse(null);

        upsertVerifiedSkill(journey, mentorId, resolvedBookingId, note);
        markAllNodesCompleted(journey);
        journey.setStatus(JourneyStatus.COMPLETED_VERIFIED);
        journey.setProgressPercentage(100);
        if (journey.getCompletedAt() == null) {
            journey.setCompletedAt(Instant.now());
        }
        journeyRepository.save(journey);

        // Release escrow + pay mentor 80%. Without this, mentors who confirm
        // completion via the Completion Report PASS path (instead of the final
        // meeting verdict) never receive their booking earnings.
        if (resolvedBookingId != null) {
            bookingRepository.findById(resolvedBookingId).ifPresent(booking -> {
                if (booking.getStatus() != BookingStatus.COMPLETED) {
                    booking.setStatus(BookingStatus.COMPLETED);
                    bookingRepository.save(booking);
                }
                try {
                    BigDecimal bookingAmount = booking.getPriceVnd();
                    if (bookingAmount != null && bookingAmount.compareTo(BigDecimal.ZERO) > 0
                            && booking.getLearner() != null && booking.getMentor() != null) {
                        BigDecimal mentorPayout = bookingAmount.multiply(MENTOR_BOOKING_SHARE_RATE);
                        walletService.chargeFrozenForBooking(
                                booking.getLearner().getId(), bookingAmount, booking.getId());
                        walletService.payMentorForBooking(
                                booking.getMentor().getId(), mentorPayout, booking.getId());
                        log.info("Released escrow for booking {}: mentor {} received {} VND (80% of {}).",
                                booking.getId(), booking.getMentor().getId(), mentorPayout, bookingAmount);
                    }
                } catch (Exception e) {
                    log.error("Failed to process escrow release for booking {} during completion-report PASS: {}",
                            booking.getId(), e.getMessage(), e);
                }
            });
        }
    }

    private String upsertVerifiedSkill(Journey journey, Long mentorId, Long bookingId, String verificationNote) {
        String normalizedSkillName = SkillNameUtils.normalize(journey.getSkillName());
        if (normalizedSkillName == null || normalizedSkillName.isBlank()) {
            normalizedSkillName = "UNKNOWN_SKILL";
        }
        final String skillName = normalizedSkillName;

        UserVerifiedSkill skill = userVerifiedSkillRepo
                .findByUserIdAndSkillName(journey.getUser().getId(), skillName)
                .orElseGet(() -> UserVerifiedSkill.builder()
                        .userId(journey.getUser().getId())
                        .skillName(skillName)
                        .build());

        skill.setVerifiedByMentorId(mentorId);
        skill.setJourneyId(journey.getId());
        skill.setBookingId(bookingId);
        skill.setSkillLevel(journey.getCurrentLevel() != null ? journey.getCurrentLevel().name() : null);
        skill.setVerificationNote(verificationNote);
        if (skill.getVerifiedAt() == null) {
            skill.setVerifiedAt(Instant.now());
        }
        userVerifiedSkillRepo.save(skill);
        return skillName;
    }

    private void handleVerificationFail(Journey journey, Booking booking,
                                         int currentAttempt, List<String> weakNodeIds, String failReason) {
        booking.setVerificationAttempts(currentAttempt);

        if (currentAttempt >= MAX_VERIFICATION_ATTEMPTS) {
            booking.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(booking);

            try {
                walletService.unfreezeForBooking(
                        booking.getLearner().getId(), booking.getPriceVnd(), booking.getId());
            } catch (Exception e) {
                log.error("Failed to refund after 3 fails for booking {}: {}", booking.getId(), e.getMessage());
            }

            journey.setFinalVerificationRequired(false);
            journey.setJourneyOutputVerificationRequired(false);
            journey.setStatus(JourneyStatus.ACTIVE);
            journeyRepository.save(journey);

            notificationService.createNotification(
                    journey.getUser().getId(),
                    "⚠️ Booking đã bị hủy tự động",
                    "Bạn đã fail " + MAX_VERIFICATION_ATTEMPTS + " lần. Booking hủy và hoàn tiền. Chọn mentor mới.",
                    NotificationType.BOOKING_CANCELLED,
                    booking.getId().toString(),
                    booking.getMentor().getId());

            notificationService.createNotification(
                    booking.getMentor().getId(),
                    "Booking kết thúc",
                    "Học viên đã fail " + MAX_VERIFICATION_ATTEMPTS + " lần. Booking tự động hủy.",
                    NotificationType.BOOKING_CANCELLED,
                    booking.getId().toString(),
                    journey.getUser().getId());

            log.warn("AUTO-CANCEL: Booking {} cancelled after {} failed attempts for journey {}.",
                    booking.getId(), MAX_VERIFICATION_ATTEMPTS, journey.getId());
            return;
        }

        booking.setNextVerifyAllowedAt(LocalDateTime.now().plusDays(REVERIFY_COOLDOWN_DAYS));
        bookingRepository.save(booking);

        resetWeakNodes(journey.getId(), weakNodeIds, failReason);

        journey.setStatus(JourneyStatus.ACTIVE);
        journeyRepository.save(journey);

        notificationService.createNotification(
                journey.getUser().getId(),
                "❌ Chưa đạt verify (lần " + currentAttempt + "/" + MAX_VERIFICATION_ATTEMPTS + ")",
                "Một số node cần học lại. Verify lại sau " + REVERIFY_COOLDOWN_DAYS + " ngày.",
                NotificationType.BOOKING_CONFIRMED,
                journey.getId().toString(),
                booking.getMentor().getId());

        log.info("FAIL: Journey {} attempt {}/{}. {} weak nodes reset. Re-verify after {}.",
                journey.getId(), currentAttempt, MAX_VERIFICATION_ATTEMPTS,
                weakNodeIds.size(), booking.getNextVerifyAllowedAt());
    }

    private void resetWeakNodes(Long journeyId, List<String> weakNodeIds, String failReason) {
        if (weakNodeIds == null || weakNodeIds.isEmpty()) return;
        for (String nodeId : weakNodeIds) {
            submissionRepo.findByJourneyIdAndNodeId(journeyId, nodeId).ifPresent(submission -> {
                submission.setSubmissionStatus(RoadmapNodeSubmission.SubmissionStatus.REWORK_REQUESTED);
                submission.setVerificationStatus(RoadmapNodeSubmission.VerificationStatus.REJECTED);
                submission.setMentorFeedback(failReason);
                submissionRepo.save(submission);
                resetRoadmapProgress(submission);
                log.debug("Reset node {} in journey {} to REWORK_REQUESTED/REJECTED for re-learning.", nodeId, journeyId);
            });
        }
    }

    /**
     * When the gate passes, force every roadmap node tracked for this journey to
     * COMPLETED. Without this, students who completed all tasks but lacked
     * mentor verification per node would see the journey stuck below 100% (the
     * task-driven progress derivation caps at 99% per node).
     */
    private void markAllNodesCompleted(Journey journey) {
        if (journey == null || journey.getRoadmapSessionId() == null) {
            return;
        }
        Long sessionId = journey.getRoadmapSessionId();
        try {
            List<UserRoadmapProgress> entries = progressRepository.findBySessionId(sessionId);
            if (entries == null || entries.isEmpty()) {
                return;
            }
            Instant now = Instant.now();
            List<UserRoadmapProgress> dirty = new ArrayList<>();
            for (UserRoadmapProgress p : entries) {
                if (p.getStatus() == UserRoadmapProgress.ProgressStatus.COMPLETED
                        && p.getProgress() != null && p.getProgress() == 100) {
                    continue;
                }
                p.setStatus(UserRoadmapProgress.ProgressStatus.COMPLETED);
                p.setProgress(100);
                if (p.getCompletedAt() == null) {
                    p.setCompletedAt(now);
                }
                dirty.add(p);
            }
            if (!dirty.isEmpty()) {
                progressRepository.saveAll(dirty);
                log.info("Marked {} node(s) of journey {} (session {}) as COMPLETED after gate PASS.",
                        dirty.size(), journey.getId(), sessionId);
            }
        } catch (Exception e) {
            log.error("Failed to bulk-complete nodes for journey {} (session {}): {}",
                    journey.getId(), sessionId, e.getMessage(), e);
        }
    }

    private void resetRoadmapProgress(RoadmapNodeSubmission submission) {
        if (submission.getRoadmapSessionId() == null || submission.getNodeId() == null) {
            return;
        }
        progressRepository.findBySessionIdAndQuestId(submission.getRoadmapSessionId(), submission.getNodeId())
                .ifPresent(progress -> {
                    progress.setStatus(UserRoadmapProgress.ProgressStatus.NOT_STARTED);
                    progress.setProgress(0);
                    progress.setCompletedAt(null);
                    progressRepository.save(progress);
                });
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void requireAssignedJourneyMentor(Long mentorId, Long journeyId) {
        boolean allowed = bookingRepository.existsActiveJourneyBookingForMentor(
                mentorId, journeyId, REPORT_ELIGIBLE_STATUSES);
        if (!allowed) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Mentor is not assigned to journey " + journeyId);
        }
    }

    @Override
    @Transactional
    public void adminResetGate(Long journeyId) {
        journeyRepository.findById(journeyId).ifPresent(journey -> {
            journey.setFinalVerificationRequired(false);
            journeyRepository.save(journey);
        });
    }

    private void requireOwnerOrAssignedMentor(Long callerId, Journey journey) {
        boolean isOwner = journey.getUser() != null && callerId.equals(journey.getUser().getId());
        if (isOwner) return;
        boolean isAssignedMentor = bookingRepository.existsActiveJourneyBookingForMentor(
                callerId, journey.getId(), REPORT_ELIGIBLE_STATUSES);
        if (!isAssignedMentor) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Access denied: not the journey owner or assigned mentor");
        }
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return null;
        }
    }
}
