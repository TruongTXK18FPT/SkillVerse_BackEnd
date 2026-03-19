package com.exe.skillverse_backend.business_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.dto.request.CreateRecruitmentSessionRequest;
import com.exe.skillverse_backend.business_service.dto.request.SendRecruitmentMessageRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateRecruitmentStatusRequest;
import com.exe.skillverse_backend.business_service.dto.response.RecruitmentMessageResponse;
import com.exe.skillverse_backend.business_service.dto.response.RecruitmentSessionResponse;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.RecruitmentMessage;
import com.exe.skillverse_backend.business_service.entity.RecruitmentSession;
import com.exe.skillverse_backend.business_service.entity.enums.MessageType;
import com.exe.skillverse_backend.business_service.entity.enums.RecruitmentSessionSource;
import com.exe.skillverse_backend.business_service.entity.enums.RecruitmentSessionStatus;
import com.exe.skillverse_backend.business_service.repository.JobPostingRepository;
import com.exe.skillverse_backend.business_service.repository.RecruitmentMessageRepository;
import com.exe.skillverse_backend.business_service.repository.RecruitmentSessionRepository;
import com.exe.skillverse_backend.business_service.service.RecruitmentChatService;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.portfolio_service.entity.PortfolioExtendedProfile;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioExtendedProfileRepository;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RecruitmentChatServiceImpl implements RecruitmentChatService {

    private final RecruitmentSessionRepository sessionRepository;
    private final RecruitmentMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final JobPostingRepository jobPostingRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final PortfolioExtendedProfileRepository portfolioExtendedProfileRepository;

    @Override
    @Transactional
    public RecruitmentSessionResponse createSession(Long recruiterId, CreateRecruitmentSessionRequest request) {
        log.info("Creating recruitment session: recruiter={}, candidate={}, job={}",
                recruiterId, request.getCandidateId(), request.getJobId());

        // Find recruiter and candidate
        User recruiter = userRepository.findById(recruiterId)
                .orElseThrow(() -> new NotFoundException("Recruiter not found"));
        User candidate = userRepository.findById(request.getCandidateId())
                .orElseThrow(() -> new NotFoundException("Candidate not found"));

        // Check if session already exists (for same recruiter-candidate-job)
        RecruitmentSession existingSession = null;
        if (request.getJobId() != null) {
            existingSession = sessionRepository.findByRecruiterIdAndCandidateIdAndJobPostingId(
                    recruiterId, request.getCandidateId(), request.getJobId()).orElse(null);
        } else {
            existingSession = sessionRepository.findByRecruiterIdAndCandidateId(
                    recruiterId, request.getCandidateId()).orElse(null);
        }

        if (existingSession != null) {
            log.info("Session already exists: {}", existingSession.getId());
            return mapToSessionResponse(existingSession, recruiterId);
        }

        // Get job if provided
        JobPosting job = null;
        if (request.getJobId() != null) {
            job = jobPostingRepository.findById(request.getJobId()).orElse(null);
        }

        // Get recruiter company
        String recruiterCompany = null;
        RecruiterProfile recruiterProfile = recruiterProfileRepository.findByUserId(recruiterId).orElse(null);
        if (recruiterProfile != null) {
            recruiterCompany = recruiterProfile.getCompanyName();
        }

        // Get candidate's professional title from portfolio
        String candidateProfessionalTitle = null;
        PortfolioExtendedProfile profile = portfolioExtendedProfileRepository.findByUserId(request.getCandidateId()).orElse(null);
        if (profile != null) {
            candidateProfessionalTitle = profile.getProfessionalTitle();
        }

        // Build session
        RecruitmentSession session = RecruitmentSession.builder()
                .recruiter(recruiter)
                .candidate(candidate)
                .jobPosting(job)
                .status(RecruitmentSessionStatus.CONTACTED)
                .sourceType(request.getSourceType() != null ? request.getSourceType() : RecruitmentSessionSource.MANUAL)
                .matchScore(request.getMatchScore())
                .skillMatchPercent(request.getSkillMatchPercent())
                .candidateTitle(candidateProfessionalTitle)
                .candidateAvatar(candidate.getAvatarUrl())
                .recruiterCompany(recruiterCompany)
                .jobTitle(job != null ? job.getTitle() : null)
                .unreadCountRecruiter(0)
                .unreadCountCandidate(0)
                .isArchivedByRecruiter(false)
                .isArchivedByCandidate(false)
                .build();

        RecruitmentSession savedSession = sessionRepository.save(session);

        // Send initial message if provided
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
        }

        log.info("Created recruitment session: {}", savedSession.getId());
        return mapToSessionResponse(savedSession, recruiterId);
    }

    @Override
    public RecruitmentSessionResponse getSessionById(Long userId, Long sessionId) {
        RecruitmentSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        // Verify user is part of session
        if (!session.getRecruiter().getId().equals(userId) && !session.getCandidate().getId().equals(userId)) {
            throw new IllegalStateException("User not authorized to view this session");
        }

        return mapToSessionResponse(session, userId);
    }

    @Override
    public Page<RecruitmentSessionResponse> getRecruiterSessions(Long recruiterId, Pageable pageable) {
        Page<RecruitmentSession> sessions = sessionRepository.findByRecruiterId(recruiterId, pageable);
        List<RecruitmentSessionResponse> responses = sessions.getContent().stream()
                .map(s -> mapToSessionResponse(s, recruiterId))
                .collect(Collectors.toList());
        return new PageImpl<>(responses, pageable, sessions.getTotalElements());
    }

    @Override
    public Page<RecruitmentSessionResponse> getCandidateSessions(Long candidateId, Pageable pageable) {
        Page<RecruitmentSession> sessions = sessionRepository.findByCandidateId(candidateId, pageable);
        List<RecruitmentSessionResponse> responses = sessions.getContent().stream()
                .map(s -> mapToSessionResponse(s, candidateId))
                .collect(Collectors.toList());
        return new PageImpl<>(responses, pageable, sessions.getTotalElements());
    }

    @Override
    public Page<RecruitmentSessionResponse> searchRecruiterSessions(Long recruiterId, String query, Pageable pageable) {
        Page<RecruitmentSession> sessions = sessionRepository.searchByRecruiterId(recruiterId, query, pageable);
        List<RecruitmentSessionResponse> responses = sessions.getContent().stream()
                .map(s -> mapToSessionResponse(s, recruiterId))
                .collect(Collectors.toList());
        return new PageImpl<>(responses, pageable, sessions.getTotalElements());
    }

    @Override
    public List<RecruitmentSessionResponse> getSessionsByJob(Long recruiterId, Long jobId) {
        List<RecruitmentSession> sessions = sessionRepository.findByRecruiterIdAndJobPostingId(recruiterId, jobId);
        return sessions.stream()
                .map(s -> mapToSessionResponse(s, recruiterId))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RecruitmentMessageResponse sendMessage(Long userId, SendRecruitmentMessageRequest request) {
        log.info("Sending message: user={}, session={}", userId, request.getSessionId());

        RecruitmentSession session = sessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new NotFoundException("Session not found"));

        // Verify user is part of session
        boolean isRecruiter = session.getRecruiter().getId().equals(userId);
        boolean isCandidate = session.getCandidate().getId().equals(userId);
        if (!isRecruiter && !isCandidate) {
            throw new IllegalStateException("User not authorized to send message in this session");
        }

        User sender = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Build message
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

        // Update session
        session.setLastMessageAt(LocalDateTime.now());
        if (isRecruiter) {
            session.setUnreadCountCandidate(session.getUnreadCountCandidate() + 1);
        } else {
            session.setUnreadCountRecruiter(session.getUnreadCountRecruiter() + 1);
        }
        sessionRepository.save(session);

        return mapToMessageResponse(savedMessage);
    }

    @Override
    public Page<RecruitmentMessageResponse> getSessionMessages(Long userId, Long sessionId, Pageable pageable) {
        RecruitmentSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        // Verify user is part of session
        if (!session.getRecruiter().getId().equals(userId) && !session.getCandidate().getId().equals(userId)) {
            throw new IllegalStateException("User not authorized to view this session");
        }

        // Get messages (newest first for display)
        Page<RecruitmentMessage> messages = messageRepository.findBySessionIdOrderByCreatedAtDesc(sessionId, pageable);
        List<RecruitmentMessageResponse> responses = messages.getContent().stream()
                .map(this::mapToMessageResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, messages.getTotalElements());
    }

    @Override
    @Transactional
    public void markMessagesAsRead(Long userId, Long sessionId) {
        RecruitmentSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        // Verify user is part of session
        if (!session.getRecruiter().getId().equals(userId) && !session.getCandidate().getId().equals(userId)) {
            throw new IllegalStateException("User not authorized");
        }

        // Mark messages as read
        messageRepository.markAllAsReadBySessionIdAndUserId(sessionId, userId);

        // Reset unread count
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

        // Only recruiter can update status
        if (!session.getRecruiter().getId().equals(userId)) {
            throw new IllegalStateException("Only recruiter can update session status");
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
            throw new IllegalStateException("User not authorized");
        }
        sessionRepository.save(session);
    }

    @Override
    @Transactional
    public void deleteSession(Long userId, Long sessionId) {
        // Same as archive for now
        archiveSession(userId, sessionId);
    }

    @Override
    public long getUnreadCount(Long userId) {
        // Check if user is recruiter or candidate
        long asRecruiter = sessionRepository.countUnreadByRecruiterId(userId);
        long asCandidate = sessionRepository.countUnreadByCandidateId(userId);
        return asRecruiter + asCandidate;
    }

    @Override
    @Transactional
    public RecruitmentSessionResponse getOrCreateSession(Long recruiterId, Long candidateId, Long jobId, RecruitmentSessionSource source) {
        // Try to find existing session
        RecruitmentSession existingSession = null;
        if (jobId != null) {
            existingSession = sessionRepository.findByRecruiterIdAndCandidateIdAndJobPostingId(
                    recruiterId, candidateId, jobId).orElse(null);
        }
        if (existingSession == null) {
            existingSession = sessionRepository.findByRecruiterIdAndCandidateId(recruiterId, candidateId).orElse(null);
        }

        if (existingSession != null) {
            return mapToSessionResponse(existingSession, recruiterId);
        }

        // Create new session
        CreateRecruitmentSessionRequest request = CreateRecruitmentSessionRequest.builder()
                .candidateId(candidateId)
                .jobId(jobId)
                .sourceType(source)
                .build();

        return createSession(recruiterId, request);
    }

    // Mapping methods
    private RecruitmentSessionResponse mapToSessionResponse(RecruitmentSession session, Long currentUserId) {
        boolean isRecruiter = session.getRecruiter().getId().equals(currentUserId);
        int unreadCount = isRecruiter ? session.getUnreadCountRecruiter() : session.getUnreadCountCandidate();

        // Get last message preview
        RecruitmentMessage lastMessage = messageRepository.findTopBySessionIdOrderByCreatedAtDesc(session.getId());
        String lastMessagePreview = lastMessage != null ? lastMessage.getContent() : null;

        // Check if candidate has portfolio and get slug
        boolean hasPortfolio = portfolioExtendedProfileRepository.existsByUserId(session.getCandidate().getId());
        String candidateSlug = null;
        PortfolioExtendedProfile candidateProfile = portfolioExtendedProfileRepository.findByUserId(session.getCandidate().getId()).orElse(null);
        if (candidateProfile != null) {
            candidateSlug = candidateProfile.getCustomUrlSlug();
        }

        return RecruitmentSessionResponse.builder()
                .id(session.getId())
                .recruiterId(session.getRecruiter().getId())
                .recruiterName(session.getRecruiter().getFullName())
                .recruiterCompany(session.getRecruiterCompany())
                .candidateId(session.getCandidate().getId())
                .candidateFullName(session.getCandidate().getFullName())
                .candidateTitle(session.getCandidateTitle())
                .candidateAvatar(session.getCandidateAvatar())
                .candidateHasPortfolio(hasPortfolio)
                .candidateSlug(candidateSlug)
                .jobId(session.getJobPosting() != null ? session.getJobPosting().getId() : null)
                .jobTitle(session.getJobTitle())
                .isRemote(session.getJobPosting() != null ? session.getJobPosting().getIsRemote() : null)
                .jobLocation(session.getJobPosting() != null ? session.getJobPosting().getLocation() : null)
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

    private RecruitmentMessageResponse mapToMessageResponse(RecruitmentMessage message) {
        return RecruitmentMessageResponse.builder()
                .id(message.getId())
                .sessionId(message.getSession().getId())
                .senderId(message.getSender().getId())
                .senderName(message.getSender().getFullName())
                .senderAvatar(message.getSender().getAvatarUrl())
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
}
