package com.exe.skillverse_backend.journey_service.node_mentoring.service.impl;

import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.AssessJourneyOutputRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.ConfirmJourneyCompletionRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.SubmitJourneyOutputAssessmentRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.JourneyCompletionGateResponse;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.JourneyCompletionGateResponse.FinalGateStatus;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.JourneyCompletionReportResponse;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.JourneyOutputAssessmentResponse;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyCompletionReport;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyCompletionReport.GateDecision;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyOutputAssessment;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyOutputAssessment.AssessmentStatus;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.JourneyCompletionReportRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.JourneyOutputAssessmentRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.service.FinalVerificationGateService;
import com.exe.skillverse_backend.journey_service.node_mentoring.service.RoadmapNodeResolver;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingRepository;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinalVerificationGateServiceImpl implements FinalVerificationGateService {

    private static final List<BookingStatus> ASSIGNED_MENTOR_STATUSES = List.of(
            BookingStatus.CONFIRMED,
            BookingStatus.ONGOING,
            BookingStatus.PENDING_COMPLETION);

    // Completion report authorization is wider: a mentor who has COMPLETED a
    // JOURNEY_MENTORING booking must still be able to submit the gate report.
    private static final List<BookingStatus> REPORT_ELIGIBLE_STATUSES = List.of(
            BookingStatus.CONFIRMED,
            BookingStatus.ONGOING,
            BookingStatus.PENDING_COMPLETION,
            BookingStatus.COMPLETED);

    private final RoadmapNodeResolver resolver;
    private final JourneyCompletionReportRepository completionReportRepo;
    private final JourneyOutputAssessmentRepository outputAssessmentRepo;
    private final BookingRepository bookingRepository;
    private final JourneyRepository journeyRepository;

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
        resolver.resolveJourneyWithRoadmap(journeyId);
        requireAssignedJourneyMentor(actingMentorId, journeyId);

        // Guard: once a PASS exists the gate is already unlocked — block re-submission to avoid dirty data.
        if (completionReportRepo.existsByJourneyIdAndGateDecision(journeyId, GateDecision.PASS)) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "A PASS completion report already exists for journey " + journeyId);
        }

        // Upsert: replace the latest report rather than accumulating duplicates.
        JourneyCompletionReport report = completionReportRepo
                .findFirstByJourneyIdOrderByConfirmedAtDesc(journeyId)
                .orElseGet(() -> JourneyCompletionReport.builder()
                        .journeyId(journeyId)
                        .mentorId(actingMentorId)
                        .build());

        report.setGateDecision(request.getGateDecision());
        report.setCompletionNote(request.getCompletionNote());
        report.setBookingId(request.getBookingId());

        return JourneyCompletionReportResponse.from(completionReportRepo.save(report));
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

        JourneyOutputAssessment a = outputAssessmentRepo.findFirstByJourneyIdOrderBySubmittedAtDesc(journeyId)
                .orElseGet(() -> JourneyOutputAssessment.builder()
                        .journeyId(journeyId)
                        .learnerId(learnerId)
                        .build());

        // If previous one was APPROVED, do not allow replacement (journey should progress to completion).
        if (a.getAssessmentStatus() == AssessmentStatus.APPROVED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "The journey output assessment has already been APPROVED");
        }

        a.setSubmissionText(request.getSubmissionText());
        a.setEvidenceUrl(request.getEvidenceUrl());
        a.setAttachmentUrl(request.getAttachmentUrl());
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
        resolver.resolveJourneyWithRoadmap(journeyId);
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

        return JourneyOutputAssessmentResponse.from(outputAssessmentRepo.save(a));
    }

    @Override
    public JourneyOutputAssessmentResponse getLatestOutputAssessment(Long callerId, Long journeyId) {
        Journey journey = resolver.resolveJourney(journeyId);
        requireOwnerOrAssignedMentor(callerId, journey);
        return outputAssessmentRepo.findFirstByJourneyIdOrderBySubmittedAtDesc(journeyId)
                .map(JourneyOutputAssessmentResponse::from)
                .orElse(null);
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
}
