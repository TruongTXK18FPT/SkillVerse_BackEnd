package com.exe.skillverse_backend.business_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.dto.request.CreateRecruitmentSessionRequest;
import com.exe.skillverse_backend.business_service.dto.request.SendRecruitmentMessageRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateRecruitmentStatusRequest;
import com.exe.skillverse_backend.business_service.dto.response.RecruitmentMessageResponse;
import com.exe.skillverse_backend.business_service.dto.response.RecruitmentSessionResponse;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.entity.RecruitmentMessage;
import com.exe.skillverse_backend.business_service.entity.RecruitmentSession;
import com.exe.skillverse_backend.business_service.entity.ShortTermJob;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import com.exe.skillverse_backend.business_service.entity.enums.MessageType;
import com.exe.skillverse_backend.business_service.entity.enums.RecruitmentJobContextType;
import com.exe.skillverse_backend.business_service.entity.enums.RecruitmentSessionSource;
import com.exe.skillverse_backend.business_service.entity.enums.RecruitmentSessionStatus;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermJobStatus;
import com.exe.skillverse_backend.business_service.repository.JobPostingRepository;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.business_service.repository.RecruitmentMessageRepository;
import com.exe.skillverse_backend.business_service.repository.RecruitmentSessionRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobRepository;
import com.exe.skillverse_backend.business_service.service.RecruitmentChatService;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.portfolio_service.entity.PortfolioExtendedProfile;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioExtendedProfileRepository;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ForbiddenException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RecruitmentChatServiceImpl implements RecruitmentChatService {

    private final RecruitmentSessionRepository sessionRepository;
    private final RecruitmentMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final JobPostingRepository jobPostingRepository;
    private final ShortTermJobRepository shortTermJobRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final PortfolioExtendedProfileRepository portfolioExtendedProfileRepository;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public RecruitmentSessionResponse createSession(Long recruiterId, CreateRecruitmentSessionRequest request) {
        log.info("Creating recruitment session: recruiter={}, candidate={}, job={}, context={}",
                recruiterId, request.getCandidateId(), request.getJobId(), request.getJobContextType());

        User recruiter = userRepository.findById(recruiterId)
                .orElseThrow(() -> new NotFoundException("Recruiter not found"));
        User candidate = userRepository.findById(request.getCandidateId())
                .orElseThrow(() -> new NotFoundException("Candidate not found"));

        RecruitmentJobContextType jobContextType = normalizeJobContextType(
                request.getJobContextType(),
                request.getJobId());

        RecruitmentSession existingSession = findExistingSession(
                recruiterId,
                request.getCandidateId(),
                request.getJobId(),
                jobContextType);
        if (existingSession != null) {
            validateChatAvailable(existingSession);
            log.info("Session already exists: {}", existingSession.getId());
            return mapToSessionResponse(existingSession, recruiterId);
        }

        JobContextSnapshot jobContext = resolveJobContext(recruiterId, request.getJobId(), jobContextType);

        String recruiterCompany = null;
        RecruiterProfile recruiterProfile = recruiterProfileRepository.findByUserId(recruiterId).orElse(null);
        if (recruiterProfile != null) {
            recruiterCompany = recruiterProfile.getCompanyName();
        }

        String candidateProfessionalTitle = null;
        PortfolioExtendedProfile profile = portfolioExtendedProfileRepository.findByUserId(request.getCandidateId()).orElse(null);
        if (profile != null) {
            candidateProfessionalTitle = profile.getProfessionalTitle();
        }

        RecruitmentSession session = RecruitmentSession.builder()
                .recruiter(recruiter)
                .candidate(candidate)
                .jobPosting(jobContext.jobPosting)
                .shortTermJob(jobContext.shortTermJob)
                .jobContextType(jobContext.contextType)
                .jobContextId(jobContext.jobId)
                .status(RecruitmentSessionStatus.CONTACTED)
                .sourceType(request.getSourceType() != null ? request.getSourceType() : RecruitmentSessionSource.MANUAL)
                .matchScore(request.getMatchScore())
                .skillMatchPercent(request.getSkillMatchPercent())
                .candidateTitle(candidateProfessionalTitle)
                .candidateAvatar(candidate.getAvatarUrl())
                .recruiterCompany(recruiterCompany)
                .jobTitle(jobContext.jobTitle)
                .unreadCountRecruiter(0)
                .unreadCountCandidate(0)
                .isArchivedByRecruiter(false)
                .isArchivedByCandidate(false)
                .build();

        RecruitmentSession savedSession = sessionRepository.save(session);

        if (request.getInitialMessage() != null && !request.getInitialMessage().isBlank()) {
            RecruitmentMessage message = RecruitmentMessage.builder()
                    .session(savedSession)
                    .sender(recruiter)
                    .senderRole("RECRUITER")
                    .content(request.getInitialMessage())
                    .messageType(MessageType.TEXT)
                    .isRead(false)
                    .build();
            messageRepository.save(message);

            savedSession.setLastMessageAt(LocalDateTime.now());
            savedSession.setUnreadCountCandidate(savedSession.getUnreadCountCandidate() + 1);
            savedSession = sessionRepository.save(savedSession);

            notifySessionParticipant(
                    savedSession,
                    recruiterId,
                    recruiter.getFullName(),
                    buildNotificationTitle(savedSession, true),
                    request.getInitialMessage());
        } else {
            notifySessionParticipant(
                    savedSession,
                    recruiterId,
                    recruiter.getFullName(),
                    buildSessionOpenedTitle(savedSession),
                    buildSessionOpenedMessage(savedSession, recruiter.getFullName()));
        }

        log.info("Created recruitment session: {}", savedSession.getId());
        return mapToSessionResponse(savedSession, recruiterId);
    }

    @Override
    public RecruitmentSessionResponse getSessionById(Long userId, Long sessionId) {
        RecruitmentSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        assertSessionParticipant(session, userId);
        return mapToSessionResponse(session, userId);
    }

    @Override
    public Page<RecruitmentSessionResponse> getRecruiterSessions(Long recruiterId, Pageable pageable) {
        Page<RecruitmentSession> sessions = sessionRepository.findByRecruiterId(recruiterId, pageable);
        List<RecruitmentSessionResponse> responses = sessions.getContent().stream()
                .map(session -> mapToSessionResponse(session, recruiterId))
                .collect(Collectors.toList());
        return new PageImpl<>(responses, pageable, sessions.getTotalElements());
    }

    @Override
    public Page<RecruitmentSessionResponse> getCandidateSessions(Long candidateId, Pageable pageable) {
        Page<RecruitmentSession> sessions = sessionRepository.findByCandidateId(candidateId, pageable);
        List<RecruitmentSessionResponse> responses = sessions.getContent().stream()
                .map(session -> mapToSessionResponse(session, candidateId))
                .collect(Collectors.toList());
        return new PageImpl<>(responses, pageable, sessions.getTotalElements());
    }

    @Override
    public Page<RecruitmentSessionResponse> searchRecruiterSessions(Long recruiterId, String query, Pageable pageable) {
        Page<RecruitmentSession> sessions = sessionRepository.searchByRecruiterId(recruiterId, query, pageable);
        List<RecruitmentSessionResponse> responses = sessions.getContent().stream()
                .map(session -> mapToSessionResponse(session, recruiterId))
                .collect(Collectors.toList());
        return new PageImpl<>(responses, pageable, sessions.getTotalElements());
    }

    @Override
    public List<RecruitmentSessionResponse> getSessionsByJob(Long recruiterId, Long jobId, RecruitmentJobContextType jobContextType) {
        List<RecruitmentSession> sessions;
        if (jobContextType == RecruitmentJobContextType.SHORT_TERM_JOB) {
            sessions = sessionRepository.findByRecruiterIdAndShortTermJobId(recruiterId, jobId);
        } else {
            sessions = sessionRepository.findByRecruiterIdAndJobPostingId(recruiterId, jobId);
        }
        return sessions.stream()
                .map(session -> mapToSessionResponse(session, recruiterId))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RecruitmentMessageResponse sendMessage(Long userId, SendRecruitmentMessageRequest request) {
        log.info("Sending message: user={}, session={}", userId, request.getSessionId());

        RecruitmentSession session = sessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new NotFoundException("Session not found"));

        boolean isRecruiter = session.getRecruiter().getId().equals(userId);
        boolean isCandidate = session.getCandidate().getId().equals(userId);
        if (!isRecruiter && !isCandidate) {
            throw new ForbiddenException("User not authorized to send message in this session");
        }

        validateChatAvailable(session);

        User sender = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        RecruitmentMessage message = RecruitmentMessage.builder()
                .session(session)
                .sender(sender)
                .senderRole(isRecruiter ? "RECRUITER" : "CANDIDATE")
                .content(request.getContent())
                .messageType(MessageType.valueOf(request.getMessageType() != null ? request.getMessageType() : "TEXT"))
                .actionType(request.getActionType())
                .actionData(request.getActionData())
                .isRead(false)
                .build();

        RecruitmentMessage savedMessage = messageRepository.save(message);

        session.setLastMessageAt(LocalDateTime.now());
        if (isRecruiter) {
            session.setUnreadCountCandidate(session.getUnreadCountCandidate() + 1);
        } else {
            session.setUnreadCountRecruiter(session.getUnreadCountRecruiter() + 1);
        }
        sessionRepository.save(session);

        notifySessionParticipant(
                session,
                userId,
                sender.getFullName(),
                buildNotificationTitle(session, isRecruiter),
                request.getContent());

        RecruitmentMessageResponse response = mapToMessageResponse(savedMessage, resolveRecruiterDisplayAvatar(session.getRecruiter()));
        
        try {
            String destination = "/topic/recruitment." + session.getId();
            messagingTemplate.convertAndSend(destination, response);
            log.info("Broadcasted recruitment message to destination: {}", destination);
        } catch (Exception e) {
            log.error("Failed to broadcast recruitment message to STOMP", e);
        }

        return response;
    }

    @Override
    public Page<RecruitmentMessageResponse> getSessionMessages(Long userId, Long sessionId, Pageable pageable) {
        RecruitmentSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        assertSessionParticipant(session, userId);

        Page<RecruitmentMessage> messages = messageRepository.findBySessionIdOrderByCreatedAtDesc(sessionId, pageable);
        String recruiterAvatar = resolveRecruiterDisplayAvatar(session.getRecruiter());
        List<RecruitmentMessageResponse> responses = messages.getContent().stream()
                .map(message -> mapToMessageResponse(message, recruiterAvatar))
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, messages.getTotalElements());
    }

    @Override
    @Transactional
    public void markMessagesAsRead(Long userId, Long sessionId) {
        RecruitmentSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        assertSessionParticipant(session, userId);

        messageRepository.markAllAsReadBySessionIdAndUserId(sessionId, userId);

        if (session.getRecruiter().getId().equals(userId)) {
            session.setUnreadCountRecruiter(0);
        } else {
            session.setUnreadCountCandidate(0);
        }
        sessionRepository.save(session);
    }

    @Override
    @Transactional
    public RecruitmentSessionResponse updateSessionStatus(Long userId, Long sessionId, UpdateRecruitmentStatusRequest request) {
        RecruitmentSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        if (!session.getRecruiter().getId().equals(userId)) {
            throw new ForbiddenException("Only recruiter can update session status");
        }

        session.setStatus(request.getStatus());
        RecruitmentSession saved = sessionRepository.save(session);
        return mapToSessionResponse(saved, userId);
    }

    @Override
    @Transactional
    public void archiveSession(Long userId, Long sessionId) {
        RecruitmentSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        if (session.getRecruiter().getId().equals(userId)) {
            session.setIsArchivedByRecruiter(true);
        } else if (session.getCandidate().getId().equals(userId)) {
            session.setIsArchivedByCandidate(true);
        } else {
            throw new ForbiddenException("User not authorized");
        }
        sessionRepository.save(session);
    }

    @Override
    @Transactional
    public void deleteSession(Long userId, Long sessionId) {
        archiveSession(userId, sessionId);
    }

    @Override
    public long getUnreadCount(Long userId) {
        long asRecruiter = sessionRepository.countUnreadByRecruiterId(userId);
        long asCandidate = sessionRepository.countUnreadByCandidateId(userId);
        return asRecruiter + asCandidate;
    }

    @Override
    @Transactional
    public RecruitmentSessionResponse getOrCreateSession(Long recruiterId, Long candidateId, Long jobId,
                                                         RecruitmentSessionSource source,
                                                         RecruitmentJobContextType jobContextType) {
        RecruitmentJobContextType normalizedContextType = normalizeJobContextType(jobContextType, jobId);
        RecruitmentSession existingSession = findExistingSession(recruiterId, candidateId, jobId, normalizedContextType);

        if (existingSession != null) {
            validateChatAvailable(existingSession);
            return mapToSessionResponse(existingSession, recruiterId);
        }

        CreateRecruitmentSessionRequest request = CreateRecruitmentSessionRequest.builder()
                .candidateId(candidateId)
                .jobId(jobId)
                .jobContextType(normalizedContextType)
                .sourceType(source)
                .build();

        return createSession(recruiterId, request);
    }

    private void assertSessionParticipant(RecruitmentSession session, Long userId) {
        if (!session.getRecruiter().getId().equals(userId) && !session.getCandidate().getId().equals(userId)) {
            throw new ForbiddenException("User not authorized to access this session");
        }
    }

    private RecruitmentSession findExistingSession(Long recruiterId, Long candidateId, Long jobId,
                                                   RecruitmentJobContextType jobContextType) {
        if (jobId == null || jobContextType == null) {
            return sessionRepository.findByRecruiterIdAndCandidateId(recruiterId, candidateId).orElse(null);
        }

        if (jobContextType == RecruitmentJobContextType.SHORT_TERM_JOB) {
            return sessionRepository.findByRecruiterIdAndCandidateIdAndShortTermJobId(
                    recruiterId, candidateId, jobId).orElse(null);
        }

        if (jobContextType == RecruitmentJobContextType.JOB_POSTING) {
            return sessionRepository.findByRecruiterIdAndCandidateIdAndJobPostingId(
                    recruiterId, candidateId, jobId).orElse(null);
        }

        return null;
    }

    private RecruitmentJobContextType normalizeJobContextType(RecruitmentJobContextType jobContextType, Long jobId) {
        if (jobId == null) {
            return null;
        }
        return jobContextType != null ? jobContextType : RecruitmentJobContextType.JOB_POSTING;
    }

    private JobContextSnapshot resolveJobContext(Long recruiterId, Long jobId,
                                                 RecruitmentJobContextType jobContextType) {
        if (jobId == null || jobContextType == null) {
            return JobContextSnapshot.empty();
        }

        if (jobContextType == RecruitmentJobContextType.SHORT_TERM_JOB) {
            ShortTermJob shortTermJob = shortTermJobRepository.findById(jobId)
                    .orElseThrow(() -> new NotFoundException("Short-term job not found"));

            if (!shortTermJob.getRecruiterProfile().getUserId().equals(recruiterId)) {
                throw new ForbiddenException("Recruiter does not own this short-term job");
            }

            validateShortTermJobIsChatActive(shortTermJob);
            return JobContextSnapshot.forShortTermJob(shortTermJob);
        }

        JobPosting jobPosting = jobPostingRepository.findByIdAndRecruiterProfileUserId(jobId, recruiterId)
                .orElseThrow(() -> new NotFoundException("Job posting not found"));

        validateJobPostingIsChatActive(jobPosting);
        return JobContextSnapshot.forJobPosting(jobPosting);
    }

    private void validateChatAvailable(RecruitmentSession session) {
        ChatAvailability availability = resolveChatAvailability(session);
        if (!availability.available) {
            throw new BadRequestException(availability.reason);
        }
    }

    private ChatAvailability resolveChatAvailability(RecruitmentSession session) {
        RecruitmentJobContextType contextType = resolveContextType(session);

        if (contextType == null) {
            return ChatAvailability.available();
        }

        if (contextType == RecruitmentJobContextType.SHORT_TERM_JOB) {
            ShortTermJob shortTermJob = session.getShortTermJob();
            if (shortTermJob == null) {
                return ChatAvailability.unavailable("Short-term job no longer exists.", null);
            }

            String currentStatus = shortTermJob.getStatus().name();
            if (!isShortTermChatActive(shortTermJob.getStatus())) {
                return ChatAvailability.unavailable(
                        "Job đã đóng hoặc không còn hoạt động. Không thể tiếp tục nhắn tin.",
                        currentStatus);
            }

            return ChatAvailability.available(currentStatus);
        }

        JobPosting jobPosting = session.getJobPosting();
        if (jobPosting == null) {
            return ChatAvailability.unavailable("Job posting no longer exists.", null);
        }

        String currentStatus = jobPosting.getStatus().name();
        if (jobPosting.getStatus() == JobStatus.CLOSED) {
            return ChatAvailability.unavailable(
                    "Job đã đóng. Không thể tiếp tục nhắn tin trong cuộc trò chuyện này.",
                    currentStatus);
        }

        return ChatAvailability.available(currentStatus);
    }

    private RecruitmentSessionResponse mapToSessionResponse(RecruitmentSession session, Long currentUserId) {
        boolean isRecruiter = session.getRecruiter().getId().equals(currentUserId);
        int unreadCount = isRecruiter ? session.getUnreadCountRecruiter() : session.getUnreadCountCandidate();

        RecruitmentMessage lastMessage = messageRepository.findTopBySessionIdOrderByCreatedAtDesc(session.getId());
        String lastMessagePreview = lastMessage != null ? lastMessage.getContent() : null;

        boolean hasPortfolio = portfolioExtendedProfileRepository.existsByUserId(session.getCandidate().getId());
        String candidateSlug = null;
        PortfolioExtendedProfile candidateProfile = portfolioExtendedProfileRepository.findByUserId(session.getCandidate().getId()).orElse(null);
        if (candidateProfile != null) {
            candidateSlug = candidateProfile.getCustomUrlSlug();
        }

        RecruitmentJobContextType contextType = resolveContextType(session);
        Long contextJobId = resolveContextJobId(session);
        ChatAvailability availability = resolveChatAvailability(session);
        String recruiterAvatar = resolveRecruiterDisplayAvatar(session.getRecruiter());

        return RecruitmentSessionResponse.builder()
                .id(session.getId())
                .recruiterId(session.getRecruiter().getId())
                .recruiterName(session.getRecruiter().getFullName())
                .recruiterAvatar(recruiterAvatar)
                .recruiterCompany(session.getRecruiterCompany())
                .candidateId(session.getCandidate().getId())
                .candidateFullName(session.getCandidate().getFullName())
                .candidateTitle(session.getCandidateTitle())
                .candidateAvatar(session.getCandidateAvatar())
                .candidateHasPortfolio(hasPortfolio)
                .candidateSlug(candidateSlug)
                .jobId(contextJobId)
                .jobTitle(session.getJobTitle())
                .jobContextType(contextType)
                .jobStatus(availability.jobStatus)
                .isRemote(session.getJobPosting() != null ? session.getJobPosting().getIsRemote() : null)
                .jobLocation(session.getJobPosting() != null ? session.getJobPosting().getLocation() : null)
                .isChatAvailable(availability.available)
                .chatDisabledReason(availability.reason)
                .status(session.getStatus())
                .sourceType(session.getSourceType())
                .matchScore(session.getMatchScore())
                .skillMatchPercent(session.getSkillMatchPercent())
                .unreadCount(unreadCount)
                .lastMessageAt(session.getLastMessageAt())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .lastMessagePreview(lastMessagePreview)
                .build();
    }

    private RecruitmentMessageResponse mapToMessageResponse(RecruitmentMessage message, String recruiterAvatar) {
        String senderAvatar = "RECRUITER".equalsIgnoreCase(message.getSenderRole())
                ? recruiterAvatar
                : message.getSender().getAvatarUrl();
        return RecruitmentMessageResponse.builder()
                .id(message.getId())
                .sessionId(message.getSession().getId())
                .senderId(message.getSender().getId())
                .senderName(message.getSender().getFullName())
                .senderAvatar(senderAvatar)
                .senderRole(message.getSenderRole())
                .content(message.getContent())
                .messageType(message.getMessageType())
                .actionType(message.getActionType())
                .actionData(message.getActionData())
                .isRead(message.getIsRead())
                .readAt(message.getReadAt())
                .createdAt(message.getCreatedAt())
                .build();
    }

    private String resolveRecruiterDisplayAvatar(User recruiter) {
        if (recruiter == null) {
            return null;
        }

        RecruiterProfile recruiterProfile = recruiterProfileRepository.findByUserId(recruiter.getId()).orElse(null);
        if (recruiterProfile != null
                && recruiterProfile.getCompanyLogoUrl() != null
                && !recruiterProfile.getCompanyLogoUrl().isBlank()) {
            return recruiterProfile.getCompanyLogoUrl();
        }

        return recruiter.getAvatarUrl();
    }

    private RecruitmentJobContextType resolveContextType(RecruitmentSession session) {
        if (session.getJobContextType() != null) {
            return session.getJobContextType();
        }
        return session.getJobPosting() != null ? RecruitmentJobContextType.JOB_POSTING : null;
    }

    private Long resolveContextJobId(RecruitmentSession session) {
        if (session.getShortTermJob() != null) {
            return session.getShortTermJob().getId();
        }
        if (session.getJobPosting() != null) {
            return session.getJobPosting().getId();
        }
        return session.getJobContextId();
    }

    private void validateJobPostingIsChatActive(JobPosting jobPosting) {
        if (jobPosting.getStatus() == JobStatus.CLOSED) {
            throw new BadRequestException("Job đã đóng. Không thể mở hoặc tiếp tục cuộc trò chuyện.");
        }
    }

    private void validateShortTermJobIsChatActive(ShortTermJob shortTermJob) {
        if (!isShortTermChatActive(shortTermJob.getStatus())) {
            throw new BadRequestException("Job đã đóng hoặc không còn hoạt động. Không thể mở hoặc tiếp tục cuộc trò chuyện.");
        }
    }

    private boolean isShortTermChatActive(ShortTermJobStatus status) {
        return status == ShortTermJobStatus.PUBLISHED
                || status == ShortTermJobStatus.APPLIED
                || status == ShortTermJobStatus.IN_PROGRESS
                || status == ShortTermJobStatus.SUBMITTED
                || status == ShortTermJobStatus.UNDER_REVIEW
                || status == ShortTermJobStatus.APPROVED;
    }

    private void notifySessionParticipant(RecruitmentSession session,
                                          Long senderId,
                                          String senderName,
                                          String title,
                                          String rawMessage) {
        Long recipientId = session.getRecruiter().getId().equals(senderId)
                ? session.getCandidate().getId()
                : session.getRecruiter().getId();

        String message = abbreviate(rawMessage);
        notificationService.createNotification(
                recipientId,
                title,
                senderName == null || senderName.isBlank() ? message : senderName + ": " + message,
                NotificationType.RECRUITMENT_MESSAGE,
                String.valueOf(session.getId()),
                senderId);
    }

    private String buildNotificationTitle(RecruitmentSession session, boolean sentByRecruiter) {
        if (sentByRecruiter) {
            return session.getJobTitle() != null
                    ? "Tin nhắn tuyển dụng mới: " + session.getJobTitle()
                    : "Tin nhắn tuyển dụng mới";
        }
        return session.getJobTitle() != null
                ? "Ứng viên đã phản hồi: " + session.getJobTitle()
                : "Ứng viên đã phản hồi";
    }

    private String buildSessionOpenedTitle(RecruitmentSession session) {
        return session.getJobTitle() != null
                ? "Nhà tuyển dụng đã mở chat: " + session.getJobTitle()
                : "Nhà tuyển dụng đã mở cuộc trò chuyện";
    }

    private String buildSessionOpenedMessage(RecruitmentSession session, String recruiterName) {
        String safeRecruiterName = recruiterName == null || recruiterName.isBlank() ? "Nhà tuyển dụng" : recruiterName;
        if (session.getJobTitle() != null) {
            return safeRecruiterName + " đã bắt đầu trao đổi với bạn về job " + session.getJobTitle();
        }
        return safeRecruiterName + " đã bắt đầu một cuộc trò chuyện tuyển dụng với bạn";
    }

    private String abbreviate(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return "Bạn có tin nhắn tuyển dụng mới.";
        }
        String trimmed = rawMessage.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= 180) {
            return trimmed;
        }
        return trimmed.substring(0, 177) + "...";
    }

    private static final class JobContextSnapshot {
        private final RecruitmentJobContextType contextType;
        private final Long jobId;
        private final JobPosting jobPosting;
        private final ShortTermJob shortTermJob;
        private final String jobTitle;

        private JobContextSnapshot(RecruitmentJobContextType contextType,
                                   Long jobId,
                                   JobPosting jobPosting,
                                   ShortTermJob shortTermJob,
                                   String jobTitle) {
            this.contextType = contextType;
            this.jobId = jobId;
            this.jobPosting = jobPosting;
            this.shortTermJob = shortTermJob;
            this.jobTitle = jobTitle;
        }

        private static JobContextSnapshot empty() {
            return new JobContextSnapshot(null, null, null, null, null);
        }

        private static JobContextSnapshot forJobPosting(JobPosting jobPosting) {
            return new JobContextSnapshot(
                    RecruitmentJobContextType.JOB_POSTING,
                    jobPosting.getId(),
                    jobPosting,
                    null,
                    jobPosting.getTitle());
        }

        private static JobContextSnapshot forShortTermJob(ShortTermJob shortTermJob) {
            return new JobContextSnapshot(
                    RecruitmentJobContextType.SHORT_TERM_JOB,
                    shortTermJob.getId(),
                    null,
                    shortTermJob,
                    shortTermJob.getTitle());
        }
    }

    private static final class ChatAvailability {
        private final boolean available;
        private final String reason;
        private final String jobStatus;

        private ChatAvailability(boolean available, String reason, String jobStatus) {
            this.available = available;
            this.reason = reason;
            this.jobStatus = jobStatus;
        }

        private static ChatAvailability available() {
            return new ChatAvailability(true, null, null);
        }

        private static ChatAvailability available(String jobStatus) {
            return new ChatAvailability(true, null, jobStatus);
        }

        private static ChatAvailability unavailable(String reason, String jobStatus) {
            return new ChatAvailability(false, reason, jobStatus);
        }
    }
}
