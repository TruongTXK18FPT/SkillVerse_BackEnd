package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.dto.request.ChatRequest;
import com.exe.skillverse_backend.ai_service.dto.response.ChatResponse;
import com.exe.skillverse_backend.ai_service.entity.ChatMessage;
import com.exe.skillverse_backend.ai_service.repository.ChatMessageRepository;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for AI-powered career counseling chatbot using Spring AI
 * Uses Mistral AI for latest career trends, with Gemini AI fallback
 * Provides guidance on majors, career trends, skill development, and
 * educational paths
 */
@Service
@Slf4j
public class AiChatbotService {

    private final ChatModel mistralChatModel;
    private final ChatMessageRepository chatMessageRepository;
    private final InputValidationService inputValidationService;

    public AiChatbotService(
            @Qualifier("mistralAiChatModel") ChatModel mistralChatModel,
            ChatMessageRepository chatMessageRepository,
            InputValidationService inputValidationService) {
        this.mistralChatModel = mistralChatModel;
        this.chatMessageRepository = chatMessageRepository;
        this.inputValidationService = inputValidationService;
    }

    // Career counseling system prompt (100% Vietnamese, chi tiết như bản tiếng Anh)
    private static final String SYSTEM_PROMPT = """
            Bạn là Meowl, cố vấn nghề nghiệp AI thân thiện của SkillVerse. 🐾
            NGÔN NGỮ: Luôn trả lời 100% bằng TIẾNG VIỆT chuẩn, có dấu, dễ đọc. Chỉ giữ vài tên nghề/công nghệ tiếng Anh (Data Scientist, React, DevOps...).
            BẢO VỆ: Nếu đầu vào vô lý (IELTS 10.0, thô tục), từ chối lịch sự và gợi ý cách nhập hợp lệ.

            BỐI CẢNH 2025:
            - Cập nhật xu hướng việc làm, công nghệ, mức lương 2025; hybrid/remote phổ biến.
            - Ưu tiên công nghệ hiện hành; công nghệ cũ chỉ để so sánh lịch sử.

            CẤU TRÚC TRẢ LỜI (dùng Markdown, tiếng Việt thuần):

            ### 🧭 Tổng quan cá nhân hóa
            - Tóm tắt câu hỏi và mục tiêu của người dùng (1–2 câu)
            - Gợi ý định hướng phù hợp dựa trên bối cảnh/ngành

            ### ✅ Lý do nên theo đuổi
            - Lợi ích 1 (kèm ví dụ hoặc số liệu thực tế nếu có)
            - Lợi ích 2 (nhu cầu tuyển dụng/mức lương tham khảo 2025)
            - Lợi ích 3 (cơ hội thăng tiến, đa dạng vai trò)

            ### ⚖️ So sánh lựa chọn/nhánh lộ trình (nếu phù hợp)
            | Tiêu chí | Phương án A | Phương án B | Phù hợp với |
            |---|---|---|---|
            | Độ dễ học | ✅ Dễ | ❌ Khó | Người mới |
            | Cơ hội việc làm | 🔥 Cao | 📉 Trung bình | 2025: A |
            | Công cụ | React, Spring | Django, Vue | ... |

            ### 🚀 Lộ trình học theo mốc thời gian
            1) Tháng 1: Nền tảng (kiến thức cốt lõi, tài nguyên gợi ý)
            2) Tháng 2–3: Thực hành (mini project/portfolio, checklist kỹ năng)
            3) Tháng 4: Chứng chỉ/ứng tuyển (CV, GitHub, networking)

            ### 🧩 Kỹ năng cốt lõi & công cụ
            - Kỹ năng: thuật toán, OOP, hệ thống, SQL/NoSQL, cloud cơ bản...
            - Công cụ: Git/GitHub, Docker cơ bản, CI/CD đơn giản...

            ### 💰 Mức lương tham khảo (nếu liên quan)
            ```
            Fresher/Junior: [khoảng lương VNĐ]
            Mid-level: [khoảng lương VNĐ]
            Senior: [khoảng lương VNĐ]
            ```

            ### 📚 Tài nguyên gợi ý (chọn lọc)
            - 1–3 khóa học/channels/tài liệu chất lượng, ghi rõ mục đích sử dụng

            ### ⚠️ Rủi ro & cách khắc phục
            - Rủi ro A → Giải pháp ngắn gọn
            - Rủi ro B → Giải pháp ngắn gọn

            ### 💡 Lời khuyên của Meowl
            - 1–2 câu định hướng, động viên thực tế

            ### ❓Câu hỏi tiếp theo để cá nhân hóa hơn
            - Bạn có bao nhiêu thời gian mỗi tuần cho việc học?
            - Bạn thích hướng Frontend/Backend/Data/AI hay lĩnh vực khác?
            - Bạn muốn nhắm tới mức lương/mốc thời gian nào?

            QUY TẮC TRÌNH BÀY:
            - Dùng tiêu đề ###, danh sách gọn gàng, emoji vừa phải.
            - Luôn có mục "⚠️ Rủi ro & cách khắc phục" khi trả lời chi tiết.
            - Dùng bảng khi so sánh; dùng ``` cho dữ liệu định dạng (mức lương,...).
            - Kết thúc bằng 1–3 câu hỏi để tiếp tục hội thoại.
            - 100% tiếng Việt; chỉ giữ tên riêng tiếng Anh khi cần.
            """;

    /**
     * Process a chat message and get AI response
     */
    @Transactional
    public ChatResponse chat(ChatRequest request, User user) {
        // Validate user input (profanity, impossible targets like IELTS 10.0)
        try {
            inputValidationService.validateTextOrThrow(request.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, ex.getMessage());
        }
        Long sessionId = request.getSessionId();

        // Generate new session ID if not provided
        if (sessionId == null) {
            sessionId = System.currentTimeMillis();
            log.info("Starting new chat session {} for user {}", sessionId, user.getId());
        }

        // Build conversation context
        List<ChatMessage> previousMessages = chatMessageRepository
                .findBySessionIdOrderByCreatedAtAsc(sessionId);

        // Call AI with automatic provider selection and fallback
        String aiResponse = callAIWithFallback(request.getMessage(), previousMessages);

        // Save to database
        ChatMessage chatMessage = ChatMessage.builder()
                .user(user)
                .sessionId(sessionId)
                .userMessage(request.getMessage())
                .aiResponse(aiResponse)
                .createdAt(LocalDateTime.now())
                .build();

        chatMessageRepository.save(chatMessage);

        log.info("Chat session {} - User: {}, AI response length: {}",
                sessionId, user.getId(), aiResponse.length());

        return ChatResponse.builder()
                .sessionId(sessionId)
                .message(request.getMessage())
                .aiResponse(aiResponse)
                .timestamp(chatMessage.getCreatedAt())
                .build();
    }

    /**
     * Call Mistral AI for chat using Spring AI
     * Using Mistral AI for latest 2025 career trends and insights
     */
    private String callAIWithFallback(String userMessage, List<ChatMessage> previousMessages) {
        log.info("Calling Mistral AI chatbot using Spring AI");

        try {
            return callMistralForChat(userMessage, previousMessages);
        } catch (Exception e) {
            log.error("Mistral AI failed: {}", e.getMessage());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "AI chatbot service is temporarily unavailable. Please try again later.");
        }
    }

    /**
     * Call Mistral AI for chat conversation with context using Spring AI ChatClient
     * Mistral provides more recent training data for 2025 career trends
     */
    private String callMistralForChat(String userMessage, List<ChatMessage> previousMessages) {
        try {
            // Build conversation history
            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append("Conversation history:\n");

            for (ChatMessage prev : previousMessages) {
                contextBuilder.append("User: ").append(prev.getUserMessage()).append("\n");
                contextBuilder.append("Assistant: ").append(prev.getAiResponse()).append("\n");
            }

            contextBuilder.append("User: ").append(userMessage);

            String conversationHistory = contextBuilder.toString();
            log.debug("Calling Mistral AI with {} previous messages", previousMessages.size());

            // Use Spring AI ChatClient for Mistral
            return ChatClient.builder(mistralChatModel)
                    .build()
                    .prompt()
                    .system(SYSTEM_PROMPT
                            + "\nCRITICAL: Hãy trả lời bằng đúng ngôn ngữ người dùng đang dùng (ưu tiên Tiếng Việt). Nếu phát hiện yêu cầu vô lý (ví dụ mục tiêu IELTS 10.0), hãy giải thích và đưa gợi ý hợp lệ bằng Tiếng Việt.")
                    .user(conversationHistory)
                    .call()
                    .content();

        } catch (Exception e) {
            log.error("Mistral chat error: {}", e.getMessage());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Mistral AI service unavailable: " + e.getMessage());
        }
    }

    /**
     * Get conversation history for a session
     */
    @Transactional(readOnly = true)
    public List<ChatMessage> getConversationHistory(Long sessionId, Long userId) {
        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);

        // Verify user owns this session
        if (!messages.isEmpty() && !messages.get(0).getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Access denied to this conversation");
        }

        return messages;
    }

    /**
     * Get all session IDs for a user
     */
    @Transactional(readOnly = true)
    public List<Long> getUserSessions(Long userId) {
        return chatMessageRepository.findSessionIdsByUserId(userId);
    }
}
