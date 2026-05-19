package com.exe.skillverse_backend.journey_service.node_mentoring.service.impl;

import com.exe.skillverse_backend.ai_service.repository.UserRoadmapProgressRepository;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.journey_service.entity.Journey.JourneyStatus;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.AssessJourneyOutputRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.SubmitEvidenceReportRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.JourneyCompletionGateResponse;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.JourneyCompletionGateResponse.FinalGateStatus;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyCompletionReport;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyCompletionReport.GateDecision;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyOutputAssessment;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyOutputAssessment.AssessmentStatus;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.VerificationEvidenceReport;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.JourneyCompletionReportRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.JourneyOutputAssessmentRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.RoadmapNodeSubmissionRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.VerificationEvidenceReportRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.service.RoadmapNodeResolver;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.mentor_booking_service.entity.Booking;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingRepository;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.portfolio_service.repository.UserVerifiedSkillRepository;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinalVerificationGateServiceImplTest {

    @Mock private RoadmapNodeResolver resolver;
    @Mock private JourneyCompletionReportRepository completionReportRepo;
    @Mock private JourneyOutputAssessmentRepository outputAssessmentRepo;
    @Mock private BookingRepository bookingRepository;
    @Mock private JourneyRepository journeyRepository;
    @Mock private VerificationEvidenceReportRepository evidenceReportRepo;
    @Mock private UserVerifiedSkillRepository userVerifiedSkillRepo;
    @Mock private RoadmapNodeSubmissionRepository submissionRepo;
    @Mock private UserRoadmapProgressRepository progressRepository;
    @Mock private WalletService walletService;
    @Mock private NotificationService notificationService;
    @Mock private UserRepository userRepository;
    @Mock private com.exe.skillverse_backend.portfolio_service.repository.PortfolioExtendedProfileRepository portfolioExtendedProfileRepository;

    @InjectMocks
    private FinalVerificationGateServiceImpl service;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(service, "jitsiBaseUrl", "https://meet.jit.si");
    }

    @Test
    @DisplayName("evaluateGate: should block if PASS report is missing")
    void evaluateGate_ShouldBlockIfReportMissing() {
        Long journeyId = 100L;
        Long callerId = 1L;
        Journey journey = Journey.builder()
                .id(journeyId)
                .user(User.builder().id(callerId).build())
                .finalVerificationRequired(true)
                .build();

        when(resolver.resolveJourney(journeyId)).thenReturn(journey);
        when(completionReportRepo.existsByJourneyIdAndGateDecision(journeyId, GateDecision.PASS)).thenReturn(false);

        JourneyCompletionGateResponse response = service.evaluateGate(callerId, journeyId);

        assertEquals(FinalGateStatus.BLOCKED, response.getFinalGateStatus());
        assertTrue(response.getBlockingReasons().contains("A mentor completion report with gateDecision=PASS is required"));
    }

    @Test
    @DisplayName("assessOutput: APPROVED should auto-complete journey")
    void assessOutput_Approved_ShouldAutoCompleteJourney() {
        Long mentorId = 10L;
        Long journeyId = 100L;
        AssessJourneyOutputRequest request = new AssessJourneyOutputRequest();
        request.setAssessmentStatus(AssessmentStatus.APPROVED);
        request.setFeedback("Great work!");

        Journey journey = Journey.builder()
                .id(journeyId)
                .user(User.builder().id(20L).build())
                .skillName("Java")
                .finalVerificationRequired(true)
                .journeyOutputVerificationRequired(true)
                .status(JourneyStatus.NOT_STARTED)
                .build();

        JourneyOutputAssessment assessment = JourneyOutputAssessment.builder()
                .id(50L)
                .journeyId(journeyId)
                .assessmentStatus(AssessmentStatus.PENDING)
                .build();

        when(resolver.resolveJourneyWithRoadmap(journeyId)).thenReturn(journey);
        when(bookingRepository.existsActiveJourneyBookingForMentor(eq(mentorId), eq(journeyId), any())).thenReturn(true);
        when(outputAssessmentRepo.findFirstByJourneyIdOrderBySubmittedAtDesc(journeyId)).thenReturn(Optional.of(assessment));
        when(outputAssessmentRepo.save(any())).thenReturn(assessment);
        
        // Return false first, then true for buildGateResponse called during auto-completion
        when(completionReportRepo.existsByJourneyIdAndGateDecision(journeyId, GateDecision.PASS))
                .thenReturn(false)
                .thenReturn(true);
        when(completionReportRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.assessOutput(mentorId, journeyId, request);

        assertEquals(JourneyStatus.COMPLETED_VERIFIED, journey.getStatus());
        assertEquals(100, journey.getProgressPercentage());
        verify(completionReportRepo).save(argThat(report -> report.getGateDecision() == GateDecision.PASS));
    }

    @Test
    @DisplayName("submitEvidenceReportAndVerdict: PASS should update skill and release payment")
    void submitEvidenceReportAndVerdict_Pass_ShouldUpdateSkillAndReleasePayment() {
        Long mentorId = 10L;
        Long journeyId = 100L;
        SubmitEvidenceReportRequest request = new SubmitEvidenceReportRequest();
        request.setGateDecision(GateDecision.PASS);
        request.setSummaryReport("Excellent performance.");

        User learner = User.builder().id(20L).build();
        Journey journey = Journey.builder()
                .id(journeyId)
                .user(learner)
                .skillName("Python")
                .build();

        Booking booking = Booking.builder()
                .id(500L)
                .learner(learner)
                .mentor(User.builder().id(mentorId).build())
                .priceVnd(new BigDecimal("1000000"))
                .build();

        when(resolver.resolveJourney(journeyId)).thenReturn(journey);
        when(bookingRepository.existsActiveJourneyBookingForMentor(eq(mentorId), eq(journeyId), any())).thenReturn(true);
        when(bookingRepository.findActiveRoadmapMentoringBooking(journeyId)).thenReturn(Optional.of(booking));
        when(evidenceReportRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.submitEvidenceReportAndVerdict(mentorId, journeyId, request);

        assertEquals(JourneyStatus.COMPLETED_VERIFIED, journey.getStatus());
        verify(userVerifiedSkillRepo).save(argThat(skill -> skill.getSkillName().equals("PYTHON")));
        verify(walletService).chargeFrozenForBooking(eq(20L), eq(new BigDecimal("1000000")), eq(500L));
        verify(walletService).payMentorForBooking(eq(10L), argThat(amount -> amount.compareTo(new BigDecimal("800000.00")) == 0), eq(500L));
        verify(notificationService, times(2)).createNotification(anyLong(), anyString(), anyString(), any(), anyString(), anyLong());
    }

    @Test
    @DisplayName("submitEvidenceReportAndVerdict: FAIL should increment attempts and set cooldown")
    void submitEvidenceReportAndVerdict_Fail_ShouldSetCooldown() {
        Long mentorId = 10L;
        Long journeyId = 100L;
        SubmitEvidenceReportRequest request = new SubmitEvidenceReportRequest();
        request.setGateDecision(GateDecision.FAIL);
        request.setWeakNodeIds(List.of("node-1"));
        request.setFailReason("Needs more practice.");

        User learner = User.builder().id(20L).build();
        Booking booking = Booking.builder()
                .id(500L)
                .learner(learner)
                .mentor(User.builder().id(mentorId).build())
                .verificationAttempts(1)
                .build();

        when(resolver.resolveJourney(journeyId)).thenReturn(Journey.builder().id(journeyId).user(learner).build());
        when(bookingRepository.existsActiveJourneyBookingForMentor(eq(mentorId), eq(journeyId), any())).thenReturn(true);
        when(bookingRepository.findActiveRoadmapMentoringBooking(journeyId)).thenReturn(Optional.of(booking));
        when(evidenceReportRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.submitEvidenceReportAndVerdict(mentorId, journeyId, request);

        assertEquals(2, booking.getVerificationAttempts());
        assertNotNull(booking.getNextVerifyAllowedAt());
        verify(submissionRepo).findByJourneyIdAndNodeId(eq(journeyId), eq("node-1"));
    }
}
