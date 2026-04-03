package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.dto.request.CreateRecruitmentSessionRequest;
import com.exe.skillverse_backend.business_service.dto.request.SendRecruitmentMessageRequest;
import com.exe.skillverse_backend.business_service.dto.response.RecruitmentMessageResponse;
import com.exe.skillverse_backend.business_service.dto.response.RecruitmentSessionResponse;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.entity.RecruitmentMessage;
import com.exe.skillverse_backend.business_service.entity.RecruitmentSession;
import com.exe.skillverse_backend.business_service.repository.JobPostingRepository;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.business_service.repository.RecruitmentMessageRepository;
import com.exe.skillverse_backend.business_service.repository.RecruitmentSessionRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobRepository;
import com.exe.skillverse_backend.business_service.service.impl.RecruitmentChatServiceImpl;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.portfolio_service.entity.PortfolioExtendedProfile;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioExtendedProfileRepository;
import com.exe.skillverse_backend.shared.exception.ForbiddenException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecruitmentChatServiceImplTest {

    @Mock
    private RecruitmentSessionRepository sessionRepository;

    @Mock
    private RecruitmentMessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JobPostingRepository jobPostingRepository;

    @Mock
    private ShortTermJobRepository shortTermJobRepository;

    @Mock
    private RecruiterProfileRepository recruiterProfileRepository;

    @Mock
    private PortfolioExtendedProfileRepository portfolioExtendedProfileRepository;

    @Mock
    private NotificationService notificationService;

    private RecruitmentChatServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RecruitmentChatServiceImpl(
                sessionRepository,
                messageRepository,
                userRepository,
                jobPostingRepository,
                shortTermJobRepository,
                recruiterProfileRepository,
                portfolioExtendedProfileRepository,
                notificationService);

        lenient().when(sessionRepository.save(any(RecruitmentSession.class))).thenAnswer(invocation -> {
            RecruitmentSession session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId(100L);
            }
            return session;
        });
        lenient().when(messageRepository.save(any(RecruitmentMessage.class))).thenAnswer(invocation -> {
            RecruitmentMessage message = invocation.getArgument(0);
            if (message.getId() == null) {
                message.setId(200L);
            }
            return message;
        });
    }

    @Test
    @DisplayName("createSession should reuse an existing recruiter-candidate session")
    void createSession_ShouldReuseExistingSession() {
        User recruiter = user(1L, "recruiter@skillverse.vn", "Recruiter", "One");
        User candidate = user(2L, "candidate@skillverse.vn", "Candidate", "One");
        RecruitmentSession existing = RecruitmentSession.builder()
                .id(5L)
                .recruiter(recruiter)
                .candidate(candidate)
                .createdAt(LocalDateTime.now())
                .status(com.exe.skillverse_backend.business_service.entity.enums.RecruitmentSessionStatus.CONTACTED)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(recruiter));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidate));
        when(sessionRepository.findByRecruiterIdAndCandidateId(1L, 2L)).thenReturn(Optional.of(existing));
        when(messageRepository.findTopBySessionIdOrderByCreatedAtDesc(5L)).thenReturn(null);
        when(portfolioExtendedProfileRepository.existsByUserId(2L)).thenReturn(false);
        when(portfolioExtendedProfileRepository.findByUserId(2L)).thenReturn(Optional.empty());

        RecruitmentSessionResponse response = service.createSession(1L, CreateRecruitmentSessionRequest.builder()
                .candidateId(2L)
                .build());

        assertEquals(5L, response.getId());
        assertEquals(2L, response.getCandidateId());
        verify(messageRepository, never()).save(any(RecruitmentMessage.class));
        verify(notificationService, never()).createNotification(anyLong(), anyString(), anyString(), any(), anyString(), anyLong());
    }

    @Test
    @DisplayName("createSession should create an initial message and notify the candidate")
    void createSession_ShouldCreateInitialMessageAndNotifyCandidate() {
        User recruiter = user(1L, "recruiter@skillverse.vn", "Recruiter", "One");
        User candidate = user(2L, "candidate@skillverse.vn", "Candidate", "One");
        RecruitmentMessage latestMessage = RecruitmentMessage.builder()
                .id(200L)
                .content("Hello candidate")
                .session(RecruitmentSession.builder().id(100L).build())
                .sender(recruiter)
                .senderRole("RECRUITER")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(recruiter));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidate));
        when(sessionRepository.findByRecruiterIdAndCandidateId(1L, 2L)).thenReturn(Optional.empty());
        when(recruiterProfileRepository.findByUserId(1L)).thenReturn(Optional.of(RecruiterProfile.builder()
                .userId(1L)
                .companyName("SkillVerse Ltd")
                .build()));
        when(portfolioExtendedProfileRepository.findByUserId(2L)).thenReturn(Optional.of(PortfolioExtendedProfile.builder()
                .userId(2L)
                .professionalTitle("Backend Engineer")
                .customUrlSlug("candidate-one")
                .build()));
        when(portfolioExtendedProfileRepository.existsByUserId(2L)).thenReturn(true);
        when(messageRepository.findTopBySessionIdOrderByCreatedAtDesc(100L)).thenReturn(latestMessage);

        RecruitmentSessionResponse response = service.createSession(1L, CreateRecruitmentSessionRequest.builder()
                .candidateId(2L)
                .initialMessage("Hello candidate")
                .build());

        assertEquals(100L, response.getId());
        assertEquals("Hello candidate", response.getLastMessagePreview());
        verify(notificationService).createNotification(
                eq(2L),
                anyString(),
                eq("Recruiter One: Hello candidate"),
                eq(NotificationType.RECRUITMENT_MESSAGE),
                eq("100"),
                eq(1L));
    }

    @Test
    @DisplayName("sendMessage should reject users outside the session")
    void sendMessage_ShouldRejectUsersOutsideTheSession() {
        RecruitmentSession session = RecruitmentSession.builder()
                .id(7L)
                .recruiter(user(1L, "recruiter@skillverse.vn", "Recruiter", "One"))
                .candidate(user(2L, "candidate@skillverse.vn", "Candidate", "One"))
                .build();
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(session));

        assertThrows(ForbiddenException.class, () -> service.sendMessage(99L, SendRecruitmentMessageRequest.builder()
                .sessionId(7L)
                .content("No access")
                .build()));
    }

    @Test
    @DisplayName("sendMessage should increment recruiter unread count when the candidate replies")
    void sendMessage_ShouldIncrementRecruiterUnreadCountWhenCandidateReplies() {
        User recruiter = user(1L, "recruiter@skillverse.vn", "Recruiter", "One");
        User candidate = user(2L, "candidate@skillverse.vn", "Candidate", "One");
        RecruitmentSession session = RecruitmentSession.builder()
                .id(8L)
                .recruiter(recruiter)
                .candidate(candidate)
                .jobTitle("Java Intern")
                .unreadCountRecruiter(0)
                .unreadCountCandidate(0)
                .build();

        when(sessionRepository.findById(8L)).thenReturn(Optional.of(session));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidate));

        RecruitmentMessageResponse response = service.sendMessage(2L, SendRecruitmentMessageRequest.builder()
                .sessionId(8L)
                .content("I am interested")
                .build());

        assertEquals("CANDIDATE", response.getSenderRole());
        assertEquals(1, session.getUnreadCountRecruiter());
        verify(notificationService).createNotification(
                eq(1L),
                anyString(),
                eq("Candidate One: I am interested"),
                eq(NotificationType.RECRUITMENT_MESSAGE),
                eq("8"),
                eq(2L));
    }

    private User user(Long id, String email, String firstName, String lastName) {
        return User.builder()
                .id(id)
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .build();
    }
}
