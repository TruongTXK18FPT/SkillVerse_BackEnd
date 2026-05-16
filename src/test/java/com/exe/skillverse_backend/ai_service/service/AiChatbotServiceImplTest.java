package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.dto.request.ChatRequest;
import com.exe.skillverse_backend.ai_service.entity.ChatSession;
import com.exe.skillverse_backend.ai_service.enums.ChatMode;
import com.exe.skillverse_backend.ai_service.repository.ChatMessageRepository;
import com.exe.skillverse_backend.ai_service.repository.ChatSessionRepository;
import com.exe.skillverse_backend.ai_service.repository.TaxonomyEntryRepository;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.premium_service.entity.FeatureType;
import com.exe.skillverse_backend.premium_service.service.PremiumService;
import com.exe.skillverse_backend.premium_service.service.UsageLimitService;
import com.exe.skillverse_backend.ai_service.service.LocalAiGateway;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.mockito.Mockito;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatbotServiceImplTest {

    @Mock
    private ChatModel mistralChatModel;

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private TaxonomyEntryRepository taxonomyEntryRepository;

    @Mock
    private InputValidationServiceImpl inputValidationService;

    @Mock
    private UsageLimitService usageLimitService;

    @Mock
    private ExpertPromptServiceImpl expertPromptService;

    @Mock
    private PremiumService premiumService;

    @Mock
    private LocalAiGateway localAiGateway;

    private AiChatbotServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiChatbotServiceImpl(
                mistralChatModel,
                chatSessionRepository,
                chatMessageRepository,
                taxonomyEntryRepository,
                inputValidationService,
                usageLimitService,
                expertPromptService,
                premiumService,
                null,
                null,
                null);
    }

    @Test
    @DisplayName("chat should not call localAiGateway when it is null (local disabled path)")
    void chat_WithNullLocalGateway_UsesCloudPath() {
        service = new AiChatbotServiceImpl(
                mistralChatModel,
                chatSessionRepository,
                chatMessageRepository,
                taxonomyEntryRepository,
                inputValidationService,
                usageLimitService,
                expertPromptService,
                premiumService,
                null,
                null,
                null);

        Mockito.verifyNoInteractions(localAiGateway);
    }

    @Test
    @DisplayName("chat should block deep research for non-premium users")
    void chat_ShouldBlockDeepResearchForNonPremiumUsers() {
        User user = User.builder().id(1L).email("learner@skillverse.vn").build();
        ChatRequest request = ChatRequest.builder()
                .message("Need deep research")
                .aiAgentMode("deep-research-pro-preview-12-2025")
                .build();
        when(premiumService.hasActivePremiumSubscription(user.getId())).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class, () -> service.chat(request, user));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        verify(usageLimitService).checkAndRecordUsage(user.getId(), FeatureType.AI_CHATBOT_REQUESTS);
    }

    @Test
    @DisplayName("chat should require jobRole for expert mode")
    void chat_ShouldRequireJobRoleForExpertMode() {
        User user = User.builder().id(1L).email("learner@skillverse.vn").build();
        ChatRequest request = ChatRequest.builder()
                .message("Help me")
                .chatMode(ChatMode.EXPERT_MODE)
                .domain("IT")
                .industry("Software")
                .build();

        ApiException exception = assertThrows(ApiException.class, () -> service.chat(request, user));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    @DisplayName("getConversationHistory should deny access to sessions owned by another user")
    void getConversationHistory_ShouldDenyAccessToSessionsOwnedByAnotherUser() {
        ChatSession session = ChatSession.builder()
                .id(10L)
                .user(User.builder().id(2L).build())
                .createdAt(LocalDateTime.now())
                .lastMessageAt(LocalDateTime.now())
                .build();
        when(chatSessionRepository.findById(10L)).thenReturn(Optional.of(session));

        ApiException exception = assertThrows(ApiException.class, () -> service.getConversationHistory(10L, 1L));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
    }

    @Test
    @DisplayName("renameSession should reject blank titles for the owner")
    void renameSession_ShouldRejectBlankTitlesForOwner() {
        ChatSession session = ChatSession.builder()
                .id(11L)
                .user(User.builder().id(1L).build())
                .chatMode(ChatMode.GENERAL_CAREER_ADVISOR)
                .createdAt(LocalDateTime.now())
                .lastMessageAt(LocalDateTime.now())
                .build();
        when(chatSessionRepository.findById(11L)).thenReturn(Optional.of(session));

        ApiException exception = assertThrows(ApiException.class, () -> service.renameSession(11L, 1L, " "));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }
}
