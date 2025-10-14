package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.dto.ChatMessageResponse;
import com.exe.skillverse_backend.ai_service.dto.ChatSessionSummary;
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
import java.util.stream.Collectors;

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

            QUY TẮC TRẢ LỜI:
            - Trả lời TRỰC TIẾP bằng tiếng Việt, không nhắc lại câu hỏi
            - KHÔNG thêm tiêu đề như "Trả lời:", "Câu hỏi:", "Trả lời bằng tiếng Việt..."
            - Bắt đầu NGAY bằng nội dung câu trả lời
            - Sử dụng tiếng Việt chuẩn, có dấu, dễ đọc
            - Giữ tên nghề/công nghệ tiếng Anh (Data Scientist, React, DevOps...)

            XỬ LÝ INPUT SAI/VÔ LÝ (AUTO-CORRECTION):
            - Nếu phát hiện thông tin sai (IELTS 10.0, GPA 5.0, tuổi âm, v.v.):
              1) TỰ ĐỘNG ĐIỀU CHỈNH về giá trị hợp lý (IELTS 10.0 → 9.0, GPA 5.0 → 4.0)
              2) THÔNG BÁO điều chỉnh một cách LỊCh SỰ ngay đầu response:
                 "⚠️ *Mình nhận thấy bạn nhập IELTS 10.0, nhưng thang điểm IELTS chỉ từ 0-9.0. Mình đã hiểu là bạn đạt **9.0** (xuất sắc) nhé!*"
              3) Tiếp tục TƯ VẤN như bình thường với giá trị đã điều chỉnh
            - Với thông tin thô tục/không phù hợp: Từ chối lịch sự, không trả lời

            QUY TẮC ĐIỀU CHỈNH:
            - IELTS: 0-9.0 (max 9.0, các mốc 0.5)
            - TOEFL: 0-120 (max 120)
            - GPA: 0-4.0 (max 4.0, thang điểm Mỹ) hoặc 0-10 (thang điểm Việt Nam)
            - Tuổi: 15-100 (hợp lý cho học tập/nghề nghiệp)
            - Kinh nghiệm: 0-50 năm (hợp lý)
            - Nếu không chắc chắn: Hỏi lại user thay vì đoán

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

        // Add correction hints to help AI detect and fix invalid inputs
        String messageWithHints = addCorrectionHints(request.getMessage());

        // Call AI with automatic provider selection and fallback
        String aiResponse = callAIWithFallback(messageWithHints, previousMessages);

        // Save to database (save ONLY user's original message without any prefix)
        ChatMessage chatMessage = ChatMessage.builder()
                .user(user)
                .sessionId(sessionId)
                .userMessage(request.getMessage()) // Save raw user message
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
     * Returns DTOs to avoid lazy loading issues
     * DEFENSIVE: Strips echo prefix from old database messages
     */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getConversationHistory(Long sessionId, Long userId) {
        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);

        // Verify user owns this session
        if (!messages.isEmpty() && !messages.get(0).getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Access denied to this conversation");
        }

        // Convert to DTOs and clean old echo prefix
        return messages.stream()
                .map(msg -> {
                    ChatMessageResponse response = convertToResponse(msg);
                    // DEFENSIVE: Clean any old echo prefix from database
                    response.setUserMessage(cleanEchoPrefix(response.getUserMessage()));
                    return response;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get all sessions for a user with titles
     * Returns session summaries with title preview from first message
     */
    @Transactional(readOnly = true)
    public List<ChatSessionSummary> getUserSessions(Long userId) {
        List<Long> sessionIds = chatMessageRepository.findSessionIdsByUserId(userId);

        return sessionIds.stream()
                .map(sessionId -> {
                    List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
                    if (messages.isEmpty()) {
                        return null;
                    }

                    // Use custom title if set, otherwise auto-generate from first message
                    ChatMessage firstMessage = messages.get(0);
                    String title;
                    if (firstMessage.getCustomTitle() != null && !firstMessage.getCustomTitle().isEmpty()) {
                        title = firstMessage.getCustomTitle();
                    } else {
                        title = extractTitle(firstMessage.getUserMessage());
                    }

                    return ChatSessionSummary.builder()
                            .sessionId(sessionId)
                            .title(title)
                            .lastMessageAt(messages.get(messages.size() - 1).getCreatedAt())
                            .messageCount(messages.size())
                            .build();
                })
                .filter(summary -> summary != null)
                .collect(Collectors.toList());
    }

    /**
     * Convert ChatMessage entity to response DTO
     */
    private ChatMessageResponse convertToResponse(ChatMessage message) {
        return ChatMessageResponse.builder()
                .id(message.getId())
                .sessionId(message.getSessionId())
                .userMessage(message.getUserMessage())
                .aiResponse(message.getAiResponse())
                .createdAt(message.getCreatedAt())
                .userId(message.getUser().getId())
                .userEmail(message.getUser().getEmail())
                .build();
    }

    /**
     * Delete a chat session and all its messages
     */
    @Transactional
    public void deleteSession(Long sessionId, Long userId) {
        // Verify user owns this session
        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);

        if (messages.isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Phiên trò chuyện không tồn tại");
        }

        if (!messages.get(0).getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Bạn không có quyền xóa phiên này");
        }

        // Delete all messages in this session
        chatMessageRepository.deleteBySessionId(sessionId);
        log.info("Deleted session {} with {} messages for user {}", sessionId, messages.size(), userId);
    }

    /**
     * Rename a chat session by updating custom title
     * Note: Currently stores title in first message's metadata.
     * Future improvement: Add ChatSession entity with customTitle field
     */
    @Transactional
    public ChatSessionSummary renameSession(Long sessionId, Long userId, String newTitle) {
        // Verify user owns this session
        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);

        if (messages.isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Phiên trò chuyện không tồn tại");
        }

        if (!messages.get(0).getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Bạn không có quyền đổi tên phiên này");
        }

        // Validate title
        if (newTitle == null || newTitle.trim().isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Tiêu đề không được để trống");
        }

        if (newTitle.length() > 100) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Tiêu đề không được vượt quá 100 ký tự");
        }

        String trimmedTitle = newTitle.trim();

        // Store custom title in first message's customTitle field
        ChatMessage firstMessage = messages.get(0);
        firstMessage.setCustomTitle(trimmedTitle);
        chatMessageRepository.save(firstMessage);

        log.info("Renamed session {} to '{}' for user {}", sessionId, trimmedTitle, userId);

        return ChatSessionSummary.builder()
                .sessionId(sessionId)
                .title(trimmedTitle)
                .lastMessageAt(messages.get(messages.size() - 1).getCreatedAt())
                .messageCount(messages.size())
                .build();
    }

    /**
     * Extract a meaningful title from user message
     * Summarizes user request into short, clear title (50 chars max)
     * Uses smart keyword extraction to generate concise titles
     * Example: "xin chào, tôi muốn tìm hiểu về trending ngành học năm 2025 và những
     * môn đáng học" → "Trending ngành học 2025"
     */
    private String extractTitle(String userMessage) {
        if (userMessage == null || userMessage.isEmpty()) {
            return "Cuộc trò chuyện mới";
        }

        // FIRST: Remove echo prefix from old database messages
        String cleaned = cleanEchoPrefix(userMessage);

        // Remove greetings at start
        cleaned = cleaned.replaceAll("(?i)^(xin chào|hello|hi|chào|meowl)[,!.\\s]*", "");

        // Extract main topic (intelligent keyword extraction)
        cleaned = extractKeywords(cleaned);

        // Remove question words at end
        cleaned = cleaned.replaceAll("(?i)\\s+(như thế nào|thế nào|ra sao|không|chứ|nhỉ|à|hả)\\s*[?!.]*$", "");

        // Remove trailing punctuation
        cleaned = cleaned.replaceAll("[?!.,;:]+$", "").trim();

        // Fallback if too short
        if (cleaned.length() < 3) {
            cleaned = cleanEchoPrefix(userMessage).trim();
            if (cleaned.length() > 50) {
                return cleaned.substring(0, 47) + "...";
            }
        }

        // Capitalize first letter
        if (cleaned.length() > 0) {
            cleaned = cleaned.substring(0, 1).toUpperCase() + cleaned.substring(1);
        }

        // Truncate to 50 chars
        if (cleaned.length() > 50) {
            return cleaned.substring(0, 47) + "...";
        }

        return cleaned;
    }

    /**
     * Extract keywords from user message for title generation
     * Removes filler words and focuses on main topic
     * Example: "tôi muốn tìm hiểu về trending ngành học năm 2025" → "trending ngành
     * học năm 2025"
     */
    private String extractKeywords(String message) {
        // Remove filler phrases at start
        String result = message
                .replaceAll("(?i)^(tôi muốn|cho tôi|tôi cần|hãy|giúp tôi|bạn có thể|mình muốn|em muốn)\\s+", "")
                .replaceAll("(?i)^(tìm hiểu về|biết về|học về|xem|tìm|hỏi về|hỏi|hỏi xem)\\s+", "")
                .replaceAll("(?i)^(thông tin về|thông tin|chi tiết về|chi tiết)\\s+", "");

        // Remove expansion tails like "và những...", "và các..."
        result = result.replaceAll("(?i)\\s+(và những|và các|cùng với|bao gồm|kèm theo)\\s+.*", "");

        // Remove date/time context at end if message is already descriptive
        if (result.split("\\s+").length > 4) {
            result = result.replaceAll("(?i)\\s+(trong năm|năm|tháng|tuần|ngày)\\s+\\d{4}\\s*$", " năm $2");
        }

        return result.trim();
    }

    /**
     * Detect and add correction hints to user message for AI to process
     * Example: "IELTS 10.0" → adds hint "[Note: IELTS max is 9.0, user likely meant
     * 9.0]"
     */
    private String addCorrectionHints(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        StringBuilder hints = new StringBuilder();

        // Check for IELTS score > 9.0
        if (message.matches(".*[Ii][Ee][Ll][Tt][Ss].*\\b([1-9]\\d|10)(\\.\\d+)?\\b.*")) {
            hints.append("[⚠️ Phát hiện điểm IELTS > 9.0. IELTS max = 9.0, hãy auto-correct và giải thích.] ");
        }

        // Check for TOEFL score > 120
        if (message.matches(".*[Tt][Oo][Ee][Ff][Ll].*\\b(1[3-9]\\d|[2-9]\\d{2})\\b.*")) {
            hints.append("[⚠️ Phát hiện điểm TOEFL > 120. TOEFL max = 120, hãy auto-correct và giải thích.] ");
        }

        // Check for GPA > 4.0 (assuming 4.0 scale)
        if (message.matches(".*[Gg][Pp][Aa].*\\b([5-9]|\\d{2,})(\\.\\d+)?\\b.*") && !message.contains("thang 10")) {
            hints.append("[⚠️ Phát hiện GPA > 4.0. Nếu thang 4.0, max = 4.0. Hỏi user làm rõ thang điểm.] ");
        }

        // Check for unrealistic age
        if (message.matches(".*(tuổi|năm sinh|age).*\\b([0-9]|1[0-4]|[1-9]\\d{2,})\\b.*")) {
            hints.append("[⚠️ Phát hiện tuổi bất thường (<15 hoặc >100). Hãy hỏi lại user xác nhận.] ");
        }

        // If hints found, prepend to message for AI to see
        if (hints.length() > 0) {
            return hints.toString() + "\n\nCâu hỏi gốc: " + message;
        }

        return message;
    }

    /**
     * Clean echo prefix from old database messages
     * Removes "Trả lời bằng tiếng Việt... Câu hỏi:" that leaked from system prompt
     */
    private String cleanEchoPrefix(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        // Remove various forms of echo prefix (case insensitive)
        String cleaned = message
                .replaceAll("(?i)^Trả lời bằng tiếng Việt[^.]*\\.\\s*Câu hỏi:\\s*", "")
                .replaceAll("(?i)^Answer in Vietnamese[^.]*\\.\\s*Question:\\s*", "")
                .trim();

        // If cleaning removed everything, return original
        return cleaned.isEmpty() ? message : cleaned;
    }
}
