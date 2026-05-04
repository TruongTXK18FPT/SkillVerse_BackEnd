package com.exe.skillverse_backend.business_service.service.impl;

import com.exe.skillverse_backend.business_service.dto.request.CreateInterviewRequest;
import com.exe.skillverse_backend.business_service.dto.response.InterviewScheduleResponse;
import com.exe.skillverse_backend.business_service.entity.InterviewSchedule.CancelledBy;
import com.exe.skillverse_backend.business_service.entity.InterviewSchedule;
import com.exe.skillverse_backend.business_service.entity.InterviewSchedule.InterviewStatus;
import com.exe.skillverse_backend.business_service.entity.InterviewSchedule.MeetingType;
import com.exe.skillverse_backend.business_service.entity.JobApplication;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.enums.JobApplicationStatus;
import com.exe.skillverse_backend.business_service.repository.InterviewScheduleRepository;
import com.exe.skillverse_backend.business_service.repository.JobApplicationRepository;
import com.exe.skillverse_backend.business_service.service.InterviewScheduleService;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ForbiddenException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class InterviewScheduleServiceImpl implements InterviewScheduleService {

    private final InterviewScheduleRepository interviewScheduleRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final EmailService emailService;

    private static final String GOOGLE_MEET_CHARS = "abcdefghijklmnopqrstuvwxyz";
    private static final int MIN_CONFIRMATION_BUFFER_MINUTES = 30;
    private static final int STANDARD_CONFIRMATION_WINDOW_HOURS = 24;
    private static final String CANDIDATE_DECLINE_DEFAULT_REASON = "Ứng viên từ chối tham gia phỏng vấn.";
    private static final String CANDIDATE_TIMEOUT_REASON = "Ứng viên không xác nhận lịch phỏng vấn đúng hạn.";

    @Value("${jitsi.base-url:https://meet.jit.si}")
    private String jitsiBaseUrl;

    @Override
    @Transactional
    public InterviewScheduleResponse scheduleInterview(Long userId, CreateInterviewRequest request) {
        log.info("Scheduling interview for application ID: {} by user ID: {}", request.getApplicationId(), userId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime responseDeadlineAt = calculateResponseDeadline(now, request.getScheduledAt());

        // 1. Find application
        JobApplication application = jobApplicationRepository.findById(request.getApplicationId())
                .orElseThrow(() -> new NotFoundException("Application not found with ID: " + request.getApplicationId()));

        // 2. Validate ownership (recruiter who owns the job)
        JobPosting job = application.getJobPosting();
        if (!job.getRecruiterProfile().getUser().getId().equals(userId)) {
            throw new ForbiddenException("You don't have permission to schedule interview for this application");
        }

        // 2.5 Guard: Check if job is CLOSED
        if (job.getStatus() == com.exe.skillverse_backend.business_service.entity.enums.JobStatus.CLOSED) {
            throw new BadRequestException("Job đã đóng, không thể tạo lịch phỏng vấn.");
        }

        // 3. Validate current status
        if (application.getStatus() != JobApplicationStatus.ACCEPTED) {
            throw new BadRequestException("Interview can only be scheduled for applications with ACCEPTED status. " +
                    "Current status: " + application.getStatus());
        }

        // 4. Validate ONSITE jobs must use ONSITE meeting type
        if (!Boolean.TRUE.equals(job.getIsRemote())
                && request.getMeetingType() != MeetingType.ONSITE) {
            throw new BadRequestException(
                    "Công việc Onsite chỉ hỗ trợ phỏng vấn trực tiếp tại công ty.");
        }

        // 5. Check existing interview — COMPLETED cannot be re-scheduled
        InterviewSchedule existing = interviewScheduleRepository.findByApplicationId(request.getApplicationId())
                .orElse(null);
        if (existing != null) {
            if (existing.getStatus() == InterviewStatus.COMPLETED) {
                throw new BadRequestException("Cannot re-schedule a completed interview");
            }
            if (existing.getStatus() == InterviewStatus.PENDING || existing.getStatus() == InterviewStatus.CONFIRMED) {
                throw new BadRequestException("Interview has already been scheduled for this application");
            }
            // CANCELLED: re-use the existing record
            existing.setScheduledAt(request.getScheduledAt());
            existing.setDurationMinutes(request.getDurationMinutes() != null ? request.getDurationMinutes() : 60);
            existing.setMeetingType(request.getMeetingType());
            existing.setLocation(request.getLocation());
            existing.setInterviewerName(request.getInterviewerName());
            existing.setInterviewNotes(request.getInterviewNotes());
            existing.setStatus(InterviewStatus.PENDING);
            existing.setResponseDeadlineAt(responseDeadlineAt);
            existing.setRespondedAt(null);
            existing.setCancelledBy(null);
            existing.setCancelReason(null);
            existing.setCompletedAt(null);

            if (request.getMeetingType() == MeetingType.GOOGLE_MEET) {
                existing.setMeetingLink(
                    request.getMeetingLink() != null && !request.getMeetingLink().isBlank()
                        ? request.getMeetingLink()
                        : generateGoogleMeetLink()
                );
                existing.setSkillverseRoomId(null);
            } else if (request.getMeetingType() == MeetingType.SKILLVERSE_ROOM) {
                String roomId = generateSkillverseRoomId();
                existing.setSkillverseRoomId(roomId);
                existing.setMeetingLink(generateSkillverseRoomLink(roomId));
            } else {
                existing.setMeetingLink(request.getMeetingLink());
                existing.setSkillverseRoomId(null);
            }

            InterviewSchedule saved = interviewScheduleRepository.save(existing);
            application.setStatus(JobApplicationStatus.INTERVIEW_SCHEDULED);
            jobApplicationRepository.save(application);
            sendInterviewScheduledEmail(application, saved);
            log.info("Interview re-scheduled successfully for cancelled record ID: {}", existing.getId());
            return mapToResponse(saved);
        }

        // 6. Build new interview entity
        InterviewSchedule interview = InterviewSchedule.builder()
                .application(application)
                .scheduledAt(request.getScheduledAt())
                .durationMinutes(request.getDurationMinutes() != null ? request.getDurationMinutes() : 60)
                .meetingType(request.getMeetingType())
                .location(request.getLocation())
                .interviewerName(request.getInterviewerName())
                .interviewNotes(request.getInterviewNotes())
                .responseDeadlineAt(responseDeadlineAt)
                .status(InterviewStatus.PENDING)
                .build();

        // 7. Generate meeting link if needed
        if (request.getMeetingType() == MeetingType.GOOGLE_MEET) {
            // Use user-provided link, or auto-generate if not provided
            interview.setMeetingLink(
                request.getMeetingLink() != null && !request.getMeetingLink().isBlank()
                    ? request.getMeetingLink()
                    : generateGoogleMeetLink()
            );
        } else if (request.getMeetingType() == MeetingType.SKILLVERSE_ROOM) {
            String roomId = generateSkillverseRoomId();
            interview.setSkillverseRoomId(roomId);
            interview.setMeetingLink(generateSkillverseRoomLink(roomId));
        } else {
            interview.setMeetingLink(request.getMeetingLink());
        }

        // 8. Save
        InterviewSchedule saved = interviewScheduleRepository.save(interview);

        // 9. Update application status
        application.setStatus(JobApplicationStatus.INTERVIEW_SCHEDULED);
        jobApplicationRepository.save(application);

        // 10. Send email
        sendInterviewScheduledEmail(application, saved);

        log.info("Interview scheduled successfully with ID: {}", saved.getId());
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public InterviewScheduleResponse confirmInterview(Long userId, Long interviewId) {
        log.info("Candidate user ID: {} confirming interview ID: {}", userId, interviewId);

        InterviewSchedule interview = interviewScheduleRepository.findByIdAndApplicationUserId(interviewId, userId)
                .orElseThrow(() -> new NotFoundException("Interview not found with ID: " + interviewId));

        if (interview.getStatus() != InterviewStatus.PENDING) {
            throw new BadRequestException("Only PENDING interviews can be confirmed. Current status: " + interview.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        if (interview.getResponseDeadlineAt() != null && now.isAfter(interview.getResponseDeadlineAt())) {
            markInterviewAsTimedOut(interview, now);
            throw new BadRequestException("Interview confirmation deadline has passed");
        }

        interview.setStatus(InterviewStatus.CONFIRMED);
        interview.setRespondedAt(now);
        interview.setCancelledBy(null);
        interview.setCancelReason(null);

        InterviewSchedule saved = interviewScheduleRepository.save(interview);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public InterviewScheduleResponse declineInterview(Long userId, Long interviewId, String reason) {
        log.info("Candidate user ID: {} declining interview ID: {}", userId, interviewId);

        InterviewSchedule interview = interviewScheduleRepository.findByIdAndApplicationUserId(interviewId, userId)
                .orElseThrow(() -> new NotFoundException("Interview not found with ID: " + interviewId));

        if (interview.getStatus() != InterviewStatus.PENDING && interview.getStatus() != InterviewStatus.CONFIRMED) {
            throw new BadRequestException("Only PENDING or CONFIRMED interviews can be declined. Current status: " + interview.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        String normalizedReason = reason != null && !reason.trim().isEmpty()
                ? reason.trim()
                : CANDIDATE_DECLINE_DEFAULT_REASON;

        interview.setStatus(InterviewStatus.CANCELLED);
        interview.setRespondedAt(now);
        interview.setCancelledBy(CancelledBy.CANDIDATE);
        interview.setCancelReason(normalizedReason);
        InterviewSchedule saved = interviewScheduleRepository.save(interview);

        JobApplication application = interview.getApplication();
        application.setStatus(JobApplicationStatus.REJECTED);
        application.setRejectionReason(normalizedReason);
        application.setProcessedAt(now);
        jobApplicationRepository.save(application);

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewScheduleResponse getInterviewByApplicationId(Long applicationId) {
        InterviewSchedule interview = interviewScheduleRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new NotFoundException("Interview schedule not found for application ID: " + applicationId));
        return mapToResponse(interview);
    }

    @Override
    @Transactional
    public InterviewScheduleResponse completeInterview(Long userId, Long interviewId, String notes) {
        log.info("Marking interview ID: {} as completed by user ID: {}", interviewId, userId);

        InterviewSchedule interview = interviewScheduleRepository.findById(interviewId)
                .orElseThrow(() -> new NotFoundException("Interview not found with ID: " + interviewId));

        // Validate ownership
        JobPosting job = interview.getApplication().getJobPosting();
        if (!job.getRecruiterProfile().getUser().getId().equals(userId)) {
            throw new ForbiddenException("You don't have permission to complete this interview");
        }

        // Validate current status
        if (interview.getStatus() != InterviewStatus.PENDING && interview.getStatus() != InterviewStatus.CONFIRMED) {
            throw new BadRequestException("Cannot complete interview with status: " + interview.getStatus());
        }

        LocalDateTime scheduledEnd = interview.getScheduledAt()
                .plusMinutes(interview.getDurationMinutes() != null ? interview.getDurationMinutes() : 60);
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(scheduledEnd)) {
            throw new BadRequestException("Interview can only be completed after its scheduled end time");
        }

        // Update interview
        interview.setStatus(InterviewStatus.COMPLETED);
        interview.setCompletedAt(now);
        if (notes != null && !notes.trim().isEmpty()) {
            interview.setInterviewNotes(notes);
        }
        InterviewSchedule saved = interviewScheduleRepository.save(interview);

        // Update application status
        JobApplication application = interview.getApplication();
        application.setStatus(JobApplicationStatus.INTERVIEWED);
        if (notes != null && !notes.trim().isEmpty()) {
            application.setInterviewResult(notes);
        }
        jobApplicationRepository.save(application);

        log.info("Interview {} marked as completed, application status updated to INTERVIEWED", interviewId);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public InterviewScheduleResponse cancelInterview(Long userId, Long interviewId) {
        log.info("Cancelling interview ID: {} by user ID: {}", interviewId, userId);

        InterviewSchedule interview = interviewScheduleRepository.findById(interviewId)
                .orElseThrow(() -> new NotFoundException("Interview not found with ID: " + interviewId));

        // Validate ownership
        JobPosting job = interview.getApplication().getJobPosting();
        if (!job.getRecruiterProfile().getUser().getId().equals(userId)) {
            throw new ForbiddenException("You don't have permission to cancel this interview");
        }

        // Validate current status
        if (interview.getStatus() == InterviewStatus.CANCELLED) {
            throw new BadRequestException("Interview is already cancelled");
        }
        if (interview.getStatus() == InterviewStatus.COMPLETED) {
            throw new BadRequestException("Cannot cancel a completed interview");
        }

        interview.setStatus(InterviewStatus.CANCELLED);
        interview.setRespondedAt(LocalDateTime.now());
        interview.setCancelledBy(CancelledBy.RECRUITER);
        interview.setCancelReason("Recruiter cancelled the interview.");
        InterviewSchedule saved = interviewScheduleRepository.save(interview);

        // Revert application status to ACCEPTED
        JobApplication application = interview.getApplication();
        application.setStatus(JobApplicationStatus.ACCEPTED);
        jobApplicationRepository.save(application);

        log.info("Interview {} cancelled, application status reverted to ACCEPTED", interviewId);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterviewScheduleResponse> getInterviewsByJobPostingId(Long jobPostingId) {
        log.info("Fetching all interviews for job posting ID: {}", jobPostingId);
        List<InterviewSchedule> interviews = interviewScheduleRepository.findByJobPostingId(jobPostingId);
        return interviews.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterviewScheduleResponse> getMyInterviews(Long userId) {
        log.info("Fetching my interviews for user ID: {}", userId);
        List<InterviewSchedule> interviews = interviewScheduleRepository.findByUserId(userId);
        return interviews.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private String generateGoogleMeetLink() {
        SecureRandom random = new SecureRandom();
        String[] parts = new String[3];
        for (int i = 0; i < 3; i++) {
            parts[i] = String.valueOf(GOOGLE_MEET_CHARS.charAt(random.nextInt(GOOGLE_MEET_CHARS.length())))
                    + GOOGLE_MEET_CHARS.charAt(random.nextInt(GOOGLE_MEET_CHARS.length()))
                    + GOOGLE_MEET_CHARS.charAt(random.nextInt(GOOGLE_MEET_CHARS.length()));
        }
        return "https://meet.google.com/" + String.join("-", parts);
    }

    private String generateSkillverseRoomId() {
        return "sv-room-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    private String generateSkillverseRoomLink(String roomId) {
        return jitsiBaseUrl + "/" + roomId;
    }

    private LocalDateTime calculateResponseDeadline(LocalDateTime now, LocalDateTime scheduledAt) {
        if (!scheduledAt.isAfter(now.plusMinutes(MIN_CONFIRMATION_BUFFER_MINUTES))) {
            throw new BadRequestException("Interview must be scheduled at least 30 minutes in advance");
        }

        LocalDateTime twentyFourHoursFromNow = now.plusHours(STANDARD_CONFIRMATION_WINDOW_HOURS);
        if (!scheduledAt.isBefore(twentyFourHoursFromNow)) {
            return twentyFourHoursFromNow;
        }

        return scheduledAt.minusMinutes(MIN_CONFIRMATION_BUFFER_MINUTES);
    }

    private void markInterviewAsTimedOut(InterviewSchedule interview, LocalDateTime now) {
        interview.setStatus(InterviewStatus.CANCELLED);
        interview.setRespondedAt(now);
        interview.setCancelledBy(CancelledBy.AUTO);
        interview.setCancelReason(CANDIDATE_TIMEOUT_REASON);
        interviewScheduleRepository.save(interview);

        JobApplication application = interview.getApplication();
        application.setStatus(JobApplicationStatus.REJECTED);
        application.setRejectionReason(CANDIDATE_TIMEOUT_REASON);
        application.setProcessedAt(now);
        jobApplicationRepository.save(application);
    }

    private void sendInterviewScheduledEmail(JobApplication application, InterviewSchedule interview) {
        try {
            String email = application.getUser().getEmail();
            String fullName = getUserFullName(application.getUser());
            String jobTitle = application.getJobPosting().getTitle();
            String meetingType = interview.getMeetingType().name().replace("_", " ");
            String meetingLink = interview.getMeetingLink();
            String roomId = interview.getSkillverseRoomId();

            emailService.sendInterviewScheduled(email, fullName, jobTitle,
                    interview.getScheduledAt(), interview.getDurationMinutes(),
                    meetingType, meetingLink, roomId,
                    interview.getLocation(), interview.getInterviewerName());
            log.info("Sent INTERVIEW_SCHEDULED email to {}", email);
        } catch (Exception e) {
            log.error("Failed to send interview scheduled email for application ID: {}", application.getId(), e);
        }
    }

    private String getUserFullName(com.exe.skillverse_backend.auth_service.entity.User user) {
        String firstName = user.getFirstName() != null ? user.getFirstName() : "";
        String lastName = user.getLastName() != null ? user.getLastName() : "";
        return (firstName + " " + lastName).trim();
    }

    private InterviewScheduleResponse mapToResponse(InterviewSchedule interview) {
        JobApplication application = interview.getApplication();
        return InterviewScheduleResponse.builder()
                .id(interview.getId())
                .applicationId(application.getId())
                .candidateName(getUserFullName(application.getUser()))
                .candidateEmail(application.getUser().getEmail())
                .candidateAvatarUrl(application.getUser().getAvatarUrl())
                .jobTitle(application.getJobPosting().getTitle())
                .scheduledAt(interview.getScheduledAt())
                .durationMinutes(interview.getDurationMinutes())
                .meetingType(interview.getMeetingType())
                .meetingLink(interview.getMeetingLink())
                .skillverseRoomId(interview.getSkillverseRoomId())
                .location(interview.getLocation())
                .interviewerName(interview.getInterviewerName())
                .interviewNotes(interview.getInterviewNotes())
                .responseDeadlineAt(interview.getResponseDeadlineAt())
                .respondedAt(interview.getRespondedAt())
                .cancelledBy(interview.getCancelledBy())
                .cancelReason(interview.getCancelReason())
                .completedAt(interview.getCompletedAt())
                .status(interview.getStatus())
                .createdAt(interview.getCreatedAt())
                .updatedAt(interview.getUpdatedAt())
                .build();
    }
}