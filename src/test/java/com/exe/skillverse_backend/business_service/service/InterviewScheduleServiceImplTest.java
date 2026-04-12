package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.dto.request.CreateInterviewRequest;
import com.exe.skillverse_backend.business_service.dto.response.InterviewScheduleResponse;
import com.exe.skillverse_backend.business_service.entity.InterviewSchedule;
import com.exe.skillverse_backend.business_service.entity.InterviewSchedule.InterviewStatus;
import com.exe.skillverse_backend.business_service.entity.InterviewSchedule.MeetingType;
import com.exe.skillverse_backend.business_service.entity.JobApplication;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.entity.enums.JobApplicationStatus;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import com.exe.skillverse_backend.business_service.repository.InterviewScheduleRepository;
import com.exe.skillverse_backend.business_service.repository.JobApplicationRepository;
import com.exe.skillverse_backend.business_service.service.impl.InterviewScheduleServiceImpl;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ForbiddenException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for InterviewScheduleServiceImpl
 * Covers: Schedule Interview, Complete Interview, Cancel Interview, Get By Application
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InterviewScheduleServiceImplTest {

    @Mock
    private InterviewScheduleRepository interviewScheduleRepository;

    @Mock
    private JobApplicationRepository jobApplicationRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private InterviewScheduleServiceImpl interviewScheduleService;

    private User recruiter;
    private User applicant;
    private RecruiterProfile recruiterProfile;
    private JobPosting remoteJob;
    private JobPosting onsiteJob;
    private JobApplication acceptedApplication;
    private JobApplication pendingApplication;
    private InterviewSchedule interviewSchedule;
    private CreateInterviewRequest createRequest;

    @BeforeEach
    void setUp() {
        // 1. Recruiter
        recruiter = new User();
        recruiter.setId(1L);
        recruiter.setEmail("recruiter@company.com");
        recruiter.setFirstName("Jane");
        recruiter.setLastName("Recruiter");

        // 2. Applicant
        applicant = new User();
        applicant.setId(2L);
        applicant.setEmail("candidate@test.com");
        applicant.setFirstName("John");
        applicant.setLastName("Candidate");

        // 3. Recruiter Profile
        recruiterProfile = new RecruiterProfile();
        recruiterProfile.setUser(recruiter);
        recruiterProfile.setCompanyName("TechCorp");

        // 4. REMOTE Job
        remoteJob = JobPosting.builder()
                .id(100L)
                .title("Senior Java Developer")
                .status(JobStatus.OPEN)
                .recruiterProfile(recruiterProfile)
                .isRemote(true)
                .build();

        // 5. ONSITE Job
        onsiteJob = JobPosting.builder()
                .id(101L)
                .title("Onsite Python Developer")
                .status(JobStatus.OPEN)
                .recruiterProfile(recruiterProfile)
                .isRemote(false)
                .build();

        // 6. ACCEPTED Application (for remote job)
        acceptedApplication = JobApplication.builder()
                .id(500L)
                .user(applicant)
                .jobPosting(remoteJob)
                .status(JobApplicationStatus.ACCEPTED)
                .build();

        // 7. PENDING Application (for remote job — not eligible)
        pendingApplication = JobApplication.builder()
                .id(501L)
                .user(applicant)
                .jobPosting(remoteJob)
                .status(JobApplicationStatus.PENDING)
                .build();

        // 8. Interview Schedule
        interviewSchedule = InterviewSchedule.builder()
                .id(1000L)
                .application(acceptedApplication)
                .scheduledAt(LocalDateTime.now().plusDays(1))
                .durationMinutes(60)
                .meetingType(MeetingType.GOOGLE_MEET)
                .meetingLink("https://meet.google.com/abc-def-ghi")
                .status(InterviewStatus.PENDING)
                .interviewerName("Jane Recruiter")
                .build();

        // 9. Create Request
        createRequest = CreateInterviewRequest.builder()
                .applicationId(500L)
                .scheduledAt(LocalDateTime.now().plusDays(1))
                .durationMinutes(60)
                .meetingType(MeetingType.GOOGLE_MEET)
                .interviewerName("Jane Recruiter")
                .interviewNotes("Technical round")
                .build();

        // Inject jitsiBaseUrl via reflection (normally set by @Value)
        ReflectionTestUtils.setField(interviewScheduleService, "jitsiBaseUrl", "https://meet.jit.si");
    }

    // ==================== SCHEDULE INTERVIEW TESTS ====================

    @Test
    void scheduleInterview_Success_RemoteJob_AcceptedApplication() {
        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(acceptedApplication));
        when(interviewScheduleRepository.existsByApplicationId(500L)).thenReturn(false);
        when(interviewScheduleRepository.save(any(InterviewSchedule.class))).thenReturn(interviewSchedule);
        when(jobApplicationRepository.save(any(JobApplication.class))).thenReturn(acceptedApplication);

        InterviewScheduleResponse response = interviewScheduleService.scheduleInterview(1L, createRequest);

        assertNotNull(response);
        assertEquals(500L, response.getApplicationId());
        assertEquals(MeetingType.GOOGLE_MEET, response.getMeetingType());
        assertEquals("https://meet.google.com/abc-def-ghi", response.getMeetingLink());
        assertEquals("Jane Recruiter", response.getInterviewerName());
        verify(interviewScheduleRepository).save(any(InterviewSchedule.class));
        verify(emailService).sendInterviewScheduled(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void scheduleInterview_Success_SkillverseRoom() {
        createRequest.setMeetingType(MeetingType.SKILLVERSE_ROOM);
        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(acceptedApplication));
        when(interviewScheduleRepository.existsByApplicationId(500L)).thenReturn(false);
        when(interviewScheduleRepository.save(any(InterviewSchedule.class))).thenAnswer(invocation -> {
            InterviewSchedule saved = invocation.getArgument(0);
            saved.setId(1000L);
            return saved;
        });
        when(jobApplicationRepository.save(any(JobApplication.class))).thenReturn(acceptedApplication);

        InterviewScheduleResponse response = interviewScheduleService.scheduleInterview(1L, createRequest);

        assertEquals(MeetingType.SKILLVERSE_ROOM, response.getMeetingType());
        assertNotNull(response.getSkillverseRoomId());
        assertTrue(response.getSkillverseRoomId().startsWith("sv-room-"));
        // meetingLink is auto-generated Jitsi link: {jitsiBaseUrl}/{roomId}
        assertNotNull(response.getMeetingLink());
        assertTrue(response.getMeetingLink().startsWith("https://meet.jit.si/sv-room-"));
    }

    @Test
    void scheduleInterview_Success_CustomLink() {
        createRequest.setMeetingType(MeetingType.ZOOM);
        createRequest.setMeetingLink("https://zoom.us/j/123456789");
        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(acceptedApplication));
        when(interviewScheduleRepository.existsByApplicationId(500L)).thenReturn(false);
        when(interviewScheduleRepository.save(any(InterviewSchedule.class))).thenAnswer(invocation -> {
            InterviewSchedule saved = invocation.getArgument(0);
            saved.setId(1000L);
            return saved;
        });
        when(jobApplicationRepository.save(any(JobApplication.class))).thenReturn(acceptedApplication);

        InterviewScheduleResponse response = interviewScheduleService.scheduleInterview(1L, createRequest);

        assertEquals(MeetingType.ZOOM, response.getMeetingType());
        assertEquals("https://zoom.us/j/123456789", response.getMeetingLink());
    }

    @Test
    void scheduleInterview_Success_UpdatesApplicationStatus_ToInterviewScheduled() {
        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(acceptedApplication));
        when(interviewScheduleRepository.existsByApplicationId(500L)).thenReturn(false);
        when(interviewScheduleRepository.save(any(InterviewSchedule.class))).thenAnswer(invocation -> {
            InterviewSchedule saved = invocation.getArgument(0);
            saved.setId(1000L);
            return saved;
        });
        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(invocation -> {
            JobApplication app = invocation.getArgument(0);
            return app;
        });

        interviewScheduleService.scheduleInterview(1L, createRequest);

        assertEquals(JobApplicationStatus.INTERVIEW_SCHEDULED, acceptedApplication.getStatus());
        verify(jobApplicationRepository).save(acceptedApplication);
    }

    @Test
    void scheduleInterview_Fail_ApplicationNotFound() {
        when(jobApplicationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> interviewScheduleService.scheduleInterview(1L, createRequest));
    }

    @Test
    void scheduleInterview_Fail_NotOwner() {
        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(acceptedApplication));

        assertThrows(ForbiddenException.class,
                () -> interviewScheduleService.scheduleInterview(999L, createRequest));
    }

    @Test
    void scheduleInterview_Fail_NotAcceptedStatus() {
        when(jobApplicationRepository.findById(501L)).thenReturn(Optional.of(pendingApplication));

        assertThrows(BadRequestException.class,
                () -> interviewScheduleService.scheduleInterview(1L, CreateInterviewRequest.builder()
                        .applicationId(501L)
                        .scheduledAt(LocalDateTime.now().plusDays(1))
                        .meetingType(MeetingType.GOOGLE_MEET)
                        .build()));
    }

    @Test
    void scheduleInterview_Fail_OnsiteJob_WithOnlineMeetingType() {
        // ONSITE jobs must use ONSITE meeting type — GOOGLE_MEET should be rejected
        JobApplication onsiteAccepted = JobApplication.builder()
                .id(502L)
                .user(applicant)
                .jobPosting(onsiteJob)
                .status(JobApplicationStatus.ACCEPTED)
                .build();
        when(jobApplicationRepository.findById(502L)).thenReturn(Optional.of(onsiteAccepted));

        assertThrows(BadRequestException.class,
                () -> interviewScheduleService.scheduleInterview(1L, CreateInterviewRequest.builder()
                        .applicationId(502L)
                        .scheduledAt(LocalDateTime.now().plusDays(1))
                        .meetingType(MeetingType.GOOGLE_MEET)
                        .build()));
    }

    @Test
    void scheduleInterview_Success_OnsiteJob_WithOnsiteMeetingType() {
        // ONSITE jobs: scheduling with ONSITE meeting type should succeed
        JobApplication onsiteAccepted = JobApplication.builder()
                .id(502L)
                .user(applicant)
                .jobPosting(onsiteJob)
                .status(JobApplicationStatus.ACCEPTED)
                .build();
        CreateInterviewRequest onsiteRequest = CreateInterviewRequest.builder()
                .applicationId(502L)
                .scheduledAt(LocalDateTime.now().plusDays(1))
                .durationMinutes(90)
                .meetingType(MeetingType.ONSITE)
                .location("Tầng 5, Tòa nhà ABC, 123 Nguyễn Huệ, Q1, TP.HCM")
                .interviewerName("Nguyễn Văn B - HR Manager")
                .interviewNotes("Phỏng vấn vòng 1")
                .build();
        when(jobApplicationRepository.findById(502L)).thenReturn(Optional.of(onsiteAccepted));
        when(interviewScheduleRepository.existsByApplicationId(502L)).thenReturn(false);
        when(interviewScheduleRepository.save(any(InterviewSchedule.class))).thenAnswer(invocation -> {
            InterviewSchedule saved = invocation.getArgument(0);
            saved.setId(1001L);
            return saved;
        });
        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(invocation -> {
            JobApplication app = invocation.getArgument(0);
            return app;
        });

        InterviewScheduleResponse response = interviewScheduleService.scheduleInterview(1L, onsiteRequest);

        assertEquals(MeetingType.ONSITE, response.getMeetingType());
        assertEquals("Tầng 5, Tòa nhà ABC, 123 Nguyễn Huệ, Q1, TP.HCM", response.getLocation());
        assertEquals("Nguyễn Văn B - HR Manager", response.getInterviewerName());
        assertEquals("Phỏng vấn vòng 1", response.getInterviewNotes());
        assertNull(response.getMeetingLink());
        assertNull(response.getSkillverseRoomId());
    }

    @Test
    void scheduleInterview_Fail_AlreadyScheduled() {
        InterviewSchedule existingSchedule = InterviewSchedule.builder()
                .id(999L)
                .application(acceptedApplication)
                .status(InterviewStatus.PENDING)
                .build();

        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(acceptedApplication));
        when(interviewScheduleRepository.findByApplicationId(500L)).thenReturn(Optional.of(existingSchedule));

        assertThrows(BadRequestException.class,
                () -> interviewScheduleService.scheduleInterview(1L, createRequest));
    }

    // ==================== GET INTERVIEW BY APPLICATION ID TESTS ====================

    @Test
    void getInterviewByApplicationId_Success() {
        when(interviewScheduleRepository.findByApplicationId(500L)).thenReturn(Optional.of(interviewSchedule));

        InterviewScheduleResponse response = interviewScheduleService.getInterviewByApplicationId(500L);

        assertNotNull(response);
        assertEquals(1000L, response.getId());
        assertEquals(500L, response.getApplicationId());
        assertEquals("John Candidate", response.getCandidateName());
        assertEquals("candidate@test.com", response.getCandidateEmail());
        assertEquals("Senior Java Developer", response.getJobTitle());
    }

    @Test
    void getInterviewByApplicationId_NotFound() {
        when(interviewScheduleRepository.findByApplicationId(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> interviewScheduleService.getInterviewByApplicationId(999L));
    }

    // ==================== COMPLETE INTERVIEW TESTS ====================

    @Test
    void completeInterview_Success_PendingStatus() {
        interviewSchedule.setStatus(InterviewStatus.PENDING);
        when(interviewScheduleRepository.findById(1000L)).thenReturn(Optional.of(interviewSchedule));
        when(interviewScheduleRepository.save(any(InterviewSchedule.class))).thenAnswer(invocation -> {
            InterviewSchedule saved = invocation.getArgument(0);
            return saved;
        });
        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(invocation -> {
            JobApplication app = invocation.getArgument(0);
            return app;
        });

        InterviewScheduleResponse response = interviewScheduleService.completeInterview(1L, 1000L, "Great candidate!");

        assertEquals(InterviewStatus.COMPLETED, response.getStatus());
        assertEquals(JobApplicationStatus.INTERVIEWED, acceptedApplication.getStatus());
        assertEquals("Great candidate!", acceptedApplication.getInterviewResult());
        verify(interviewScheduleRepository).save(interviewSchedule);
        verify(jobApplicationRepository).save(acceptedApplication);
    }

    @Test
    void completeInterview_Success_ConfirmedStatus() {
        interviewSchedule.setStatus(InterviewStatus.CONFIRMED);
        when(interviewScheduleRepository.findById(1000L)).thenReturn(Optional.of(interviewSchedule));
        when(interviewScheduleRepository.save(any(InterviewSchedule.class))).thenAnswer(invocation -> {
            InterviewSchedule saved = invocation.getArgument(0);
            return saved;
        });
        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(invocation -> {
            JobApplication app = invocation.getArgument(0);
            return app;
        });

        InterviewScheduleResponse response = interviewScheduleService.completeInterview(1L, 1000L, null);

        assertEquals(InterviewStatus.COMPLETED, response.getStatus());
        assertNull(acceptedApplication.getInterviewResult()); // No notes provided
    }

    @Test
    void completeInterview_Fail_NotOwner() {
        interviewSchedule.setStatus(InterviewStatus.PENDING);
        when(interviewScheduleRepository.findById(1000L)).thenReturn(Optional.of(interviewSchedule));

        assertThrows(ForbiddenException.class,
                () -> interviewScheduleService.completeInterview(999L, 1000L, null));
    }

    @Test
    void completeInterview_Fail_NotFound() {
        when(interviewScheduleRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> interviewScheduleService.completeInterview(1L, 999L, null));
    }

    @Test
    void completeInterview_Fail_AlreadyCompleted() {
        interviewSchedule.setStatus(InterviewStatus.COMPLETED);
        when(interviewScheduleRepository.findById(1000L)).thenReturn(Optional.of(interviewSchedule));

        assertThrows(BadRequestException.class,
                () -> interviewScheduleService.completeInterview(1L, 1000L, null));
    }

    @Test
    void completeInterview_Fail_Cancelled() {
        interviewSchedule.setStatus(InterviewStatus.CANCELLED);
        when(interviewScheduleRepository.findById(1000L)).thenReturn(Optional.of(interviewSchedule));

        assertThrows(BadRequestException.class,
                () -> interviewScheduleService.completeInterview(1L, 1000L, null));
    }

    // ==================== CANCEL INTERVIEW TESTS ====================

    @Test
    void cancelInterview_Success() {
        interviewSchedule.setStatus(InterviewStatus.PENDING);
        when(interviewScheduleRepository.findById(1000L)).thenReturn(Optional.of(interviewSchedule));
        when(interviewScheduleRepository.save(any(InterviewSchedule.class))).thenAnswer(invocation -> {
            InterviewSchedule saved = invocation.getArgument(0);
            return saved;
        });
        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(invocation -> {
            JobApplication app = invocation.getArgument(0);
            return app;
        });

        InterviewScheduleResponse response = interviewScheduleService.cancelInterview(1L, 1000L);

        assertEquals(InterviewStatus.CANCELLED, response.getStatus());
        assertEquals(JobApplicationStatus.ACCEPTED, acceptedApplication.getStatus()); // Reverted
        verify(interviewScheduleRepository).save(interviewSchedule);
        verify(jobApplicationRepository).save(acceptedApplication);
    }

    @Test
    void cancelInterview_Success_ConfirmedStatus() {
        interviewSchedule.setStatus(InterviewStatus.CONFIRMED);
        when(interviewScheduleRepository.findById(1000L)).thenReturn(Optional.of(interviewSchedule));
        when(interviewScheduleRepository.save(any(InterviewSchedule.class))).thenAnswer(invocation -> {
            InterviewSchedule saved = invocation.getArgument(0);
            return saved;
        });
        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(invocation -> {
            JobApplication app = invocation.getArgument(0);
            return app;
        });

        InterviewScheduleResponse response = interviewScheduleService.cancelInterview(1L, 1000L);

        assertEquals(InterviewStatus.CANCELLED, response.getStatus());
        assertEquals(JobApplicationStatus.ACCEPTED, acceptedApplication.getStatus());
    }

    @Test
    void cancelInterview_Fail_NotOwner() {
        interviewSchedule.setStatus(InterviewStatus.PENDING);
        when(interviewScheduleRepository.findById(1000L)).thenReturn(Optional.of(interviewSchedule));

        assertThrows(ForbiddenException.class,
                () -> interviewScheduleService.cancelInterview(999L, 1000L));
    }

    @Test
    void cancelInterview_Fail_NotFound() {
        when(interviewScheduleRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> interviewScheduleService.cancelInterview(1L, 999L));
    }

    @Test
    void cancelInterview_Fail_AlreadyCancelled() {
        interviewSchedule.setStatus(InterviewStatus.CANCELLED);
        when(interviewScheduleRepository.findById(1000L)).thenReturn(Optional.of(interviewSchedule));

        assertThrows(BadRequestException.class,
                () -> interviewScheduleService.cancelInterview(1L, 1000L));
    }
}
