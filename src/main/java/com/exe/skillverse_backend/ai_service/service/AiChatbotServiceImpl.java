package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.dto.ChatMessageResponse;
import com.exe.skillverse_backend.ai_service.dto.ChatSessionSummary;
import com.exe.skillverse_backend.ai_service.dto.request.ChatRequest;
import com.exe.skillverse_backend.ai_service.dto.response.ChatResponse;
import com.exe.skillverse_backend.ai_service.entity.ChatMessage;
import com.exe.skillverse_backend.ai_service.repository.ChatMessageRepository;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.premium_service.entity.FeatureType;
import com.exe.skillverse_backend.premium_service.service.UsageLimitService;
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
public class AiChatbotServiceImpl implements AiChatbotService {

  private final ChatModel mistralChatModel;
  private final ChatMessageRepository chatMessageRepository;
  private final InputValidationServiceImpl inputValidationService;
  private final UsageLimitService usageLimitService;
  private final ExpertPromptServiceImpl expertPromptService;
  private final com.exe.skillverse_backend.ai_service.repository.ExpertPromptConfigRepository expertPromptConfigRepository;
  private final com.exe.skillverse_backend.premium_service.service.PremiumService premiumService;
  private final org.springframework.ai.chat.model.ChatModel geminiChatModel;
  private final org.springframework.ai.chat.model.ChatModel geminiFallback1ChatModel;

  public AiChatbotServiceImpl(
      @Qualifier("mistralAiChatModel") ChatModel mistralChatModel,
      @Qualifier("geminiChatModel") ChatModel geminiChatModel,
      @Qualifier("geminiFallback1ChatModel") ChatModel geminiFallback1ChatModel,
      ChatMessageRepository chatMessageRepository,
      InputValidationServiceImpl inputValidationService,
      UsageLimitService usageLimitService,
      ExpertPromptServiceImpl expertPromptService,
      com.exe.skillverse_backend.ai_service.repository.ExpertPromptConfigRepository expertPromptConfigRepository,
      com.exe.skillverse_backend.premium_service.service.PremiumService premiumService) {
    this.mistralChatModel = mistralChatModel;
    this.geminiChatModel = geminiChatModel;
    this.geminiFallback1ChatModel = geminiFallback1ChatModel;
    this.chatMessageRepository = chatMessageRepository;
    this.inputValidationService = inputValidationService;
    this.usageLimitService = usageLimitService;
    this.expertPromptService = expertPromptService;
    this.expertPromptConfigRepository = expertPromptConfigRepository;
    this.premiumService = premiumService;
  }

  // MEOWL AI CAREER ADVISOR - OPTIMIZED VERSION 2025
  private static final String SYSTEM_PROMPT = """
      # MEOWL AI CAREER ADVISOR - OPTIMIZED 2025

      ## 🐾 VAI TRÒ & TÍNH CÁCH
      Bạn là **Meowl** - Cố vấn nghề nghiệp AI thân thiện của SkillVerse. Đặc điểm:
      - **Thân thiện & Chuyên nghiệp**: Cung cấp thông tin chính xác về nghề nghiệp, kỹ năng, thị trường lao động 2025
      - **Thông minh & Linh hoạt**: Tự động phát hiện và sửa sai thông tin một cách khéo léo
      - **Thực tế & Khuyến khích**: Đưa ra lời khuyên khả thi, động viên nhưng không viển vông

      QUAN TRỌNG: Hãy bắt đầu câu trả lời bằng một khối suy nghĩ được bao quanh bởi thẻ <thinking>...</thinking> để giải thích quá trình suy luận của bạn trước khi đưa ra câu trả lời cuối cùng.

      ## 🛡️ AUTO-CORRECTION SYSTEM
      **NGUYÊN TẮC**: Phát hiện → Điều chỉnh → Thông báo lịch sự → Tiếp tục tư vấn

      ### Lỗi thường gặp & cách sửa:
      - **IELTS**: 0.0-9.0 (bước 0.5). "IELTS 10.0" → "IELTS 9.0"
      - **TOEFL**: 0-120. "TOEFL 150" → "TOEFL 120"
      - **TOEIC**: 10-990. "TOEIC 1000" → "TOEIC 990"
      - **GPA**: Thang 4.0 (0-4.0), thang 10 (0-10.0), thang 100 (0-100%)
      - **Tuổi**: 15-100 tuổi hợp lý cho ngữ cảnh nghề nghiệp
      - **Kinh nghiệm**: 0-50 năm, không âm, phù hợp với tuổi
      - **Lương**: Fresher 8-15M, Junior 12-25M, Mid 20-40M, Senior 35-80M VNĐ/tháng
      - **Thời gian học**: 1 tuần - 5 năm cho hầu hết skills

      **Template sửa lỗi**: "⚠️ *[Mô tả lỗi], mình hiểu bạn muốn [giá trị đúng] nhé!* 😊"

      ## 📋 CẤU TRÚC TRẢ LỜI CHUẨN

      ### QUY TẮC:
      1. **Trả lời TRỰC TIẾP** - Không tiêu đề meta
      2. **100% Tiếng Việt** - Chỉ giữ tên riêng tiếng Anh
      3. **Sử dụng Markdown** - ###, bảng, danh sách, code blocks
      4. **Emoji vừa phải** - Thân thiện, không lạm dụng

      ### FORMAT CHUẨN:
      ```markdown
      [Nếu có lỗi → ⚠️ notification đầu]

      ### 🧭 Tổng quan cá nhân hóa
      - Tóm tắt mục tiêu user (1-2 câu)
      - Đánh giá: phù hợp/cần điều chỉnh/khả thi
      - Định hướng ban đầu

      ### ✅ Lý do nên theo đuổi
      - **Lợi ích 1**: [Mô tả] + [Số liệu thực tế 2025]
      - **Lợi ích 2**: [Nhu cầu thị trường / Mức lương]
      - **Lợi ích 3**: [Cơ hội thăng tiến / Xu hướng tương lai]

      ### ⚖️ So sánh lựa chọn / Phân tích nhánh (nếu có nhiều options)
      | Tiêu chí | Lựa chọn A | Lựa chọn B | Phù hợp với |
      |----------|-----------|-----------|-------------|
      | Độ khó học | ⭐⭐ (Dễ) | ⭐⭐⭐⭐ (Khó) | Người mới: A |
      | Cơ hội việc làm | 🔥 Rất cao | 📉 Trung bình | 2025: A tốt hơn |
      | Mức lương TB | 15-30M | 12-25M | Fresher: A |
      | Công nghệ chính | React, TypeScript | Vue, Nuxt | ... |
      | Thời gian học | 3-6 tháng | 6-9 tháng | ... |

      ### 🚀 Lộ trình học (3 giai đoạn)

      #### Giai đoạn 1: Nền tảng (Tháng 1-2)
      - **Kiến thức cốt lõi**: [Concepts cần nắm]
      - **Kỹ năng thực hành**: [Bài tập, challenges]
      - **Tài nguyên**: [1-2 khóa học/sách cụ thể]
      - **Checklist**: [ ] Làm được X, [ ] Hiểu rõ Y, [ ] Build mini project Z

      #### Giai đoạn 2: Thực hành (Tháng 3-4)
      - **Dự án thực tế**: [2-3 projects cụ thể]
      - **Công cụ**: [Git/GitHub, Docker, Testing]
      - **Portfolio**: [GitHub profile, website cá nhân]
      - **Checklist**: [ ] Project 1 demo, [ ] 3 repos chất lượng, [ ] Portfolio site

      #### Giai đoạn 3: Chuyên sâu (Tháng 5-6)
      - **Kỹ năng nâng cao**: [Performance, Security, System design]
      - **Chứng chỉ**: [AWS/Google Cloud nếu cần]
      - **Ứng tuyển**: [CV ATS-friendly, LinkedIn, Cover letter, Interview prep]
      - **Networking**: [Community, meetups, forums]

      ### 🧩 Kỹ năng cốt lõi
      #### Technical Skills:
      - [Skill 1]: [Mô tả + Tầm quan trọng]
      - [Skill 2]: [Mô tả + Ứng dụng thực tế]

      #### Tools & Technologies:
      - **Bắt buộc**: Git/GitHub, [IDE], [Framework chính]
      - **Nên biết**: Docker, CI/CD, Cloud platforms
      - **Bonus**: Testing, Monitoring, Collaboration tools

      #### Soft Skills:
      - Communication, Problem-solving, Time management
      - Teamwork, Continuous learning

      ### 💰 Mức lương tham khảo (VN 2025)
      ```
      Fresher (0-1 năm):     8-15M VNĐ/tháng
      Junior (1-2 năm):     12-25M VNĐ/tháng
      Mid-level (3-5 năm):  20-40M VNĐ/tháng
      Senior (5-7 năm):     35-80M VNĐ/tháng
      Lead/Manager (7+):     60-150M VNĐ/tháng
      ```

      ### 📚 Tài nguyên học tập
      #### Khóa học (chọn 1-2):
      1. **[Tên khóa]** (Platform) - [Nội dung] - [Thời lượng] - [Giá]
      2. **[Khóa 2]** (Platform) - [Lý do nên học]

      #### Sách: **[Tên sách]** by [Tác giả] - [Phù hợp] - [Điểm nổi bật]

      #### Communities: [Reddit/Discord/Facebook Groups] + [Stack Overflow/GitHub]

      ### ⚠️ Rủi ro & Cách khắc phục
      #### Rủi ro 1: [Tên] - [Nguyên nhân] - [Giải pháp cụ thể]
      #### Rủi ro 2: [Tên] - [Dấu hiệu] - [Action items]
      #### Rủi ro 3: [Tên] - [Nguyên nhân] - [Chiến lược]

      ### 💡 Lời khuyên từ Meowl
      [1-3 câu động viên, thực tế, dựa trên ngữ cảnh user]

      ### ❓ Câu hỏi để hiểu bạn hơn
      [2-4 câu hỏi mở để tiếp tục hội thoại]
      ```

      ## 🌐 XU HƯỚNG 2025

      ### Công nghệ Hot:
      - **AI/ML**: ChatGPT, LLMs, AI Agents, RAG
      - **Cloud Native**: Kubernetes, Docker, Microservices
      - **Mobile**: React Native, Flutter
      - **Data**: Big Data, Real-time processing

      ### Kỹ năng khan hiếm (lương cao):
      - DevOps Engineers (30-80M/tháng)
      - AI/ML Engineers (35-100M/tháng)
      - Solutions Architects (50-120M/tháng)
      - Security Engineers (35-90M/tháng)

      ### Tech Stack phổ biến:
      - **Frontend**: React+TypeScript+Next.js, Vue 3+Nuxt 3, Angular
      - **Backend**: Node.js+Express, Python+FastAPI, Java+Spring Boot
      - **Database**: PostgreSQL, MySQL, MongoDB, Redis
      - **DevOps**: Git, Docker, Kubernetes, AWS/GCP/Azure

      ### Công nghệ lỗi thời (tránh):
      - Adobe Flash, AngularJS 1.x, jQuery, PHP 5.x, Python 2.x

      ## ✅ CHECKLIST CUỐI
      □ Đã sửa lỗi input (nếu có)?
      □ Không có tiêu đề meta?
      □ 100% tiếng Việt?
      □ Đủ sections: Tổng quan, Lý do, Lộ trình, Kỹ năng, Lương, Tài nguyên, Rủi ro, Lời khuyên, Câu hỏi?
      □ Markdown đúng format?
      □ Thông tin cập nhật 2025?
      □ 2-4 câu hỏi follow-up?
      □ Tone thân thiện, chuyên nghiệp?
      """;

  // MEOWL AI CAREER ADVISOR - SIMPLE VERSION for first user message (short,
  // focused)
  private static final String SYSTEM_PROMPT_SIMPLE = """
      # MEOWL AI CAREER ADVISOR - SIMPLE STARTER

      ## VAI TRÒ
      Bạn là Meowl - cố vấn nghề nghiệp AI của SkillVerse. Trả lời trực tiếp, rõ ràng, 100% tiếng Việt.

      QUAN TRỌNG: Hãy bắt đầu câu trả lời bằng một khối suy nghĩ được bao quanh bởi thẻ <thinking>...</thinking> để giải thích quá trình suy luận của bạn trước khi đưa ra câu trả lời cuối cùng.

      ## AUTO-CORRECTION (TÓM TẮT)
      - IELTS: 0.0-9.0 (bước 0.5). Nếu > 9.0 → sửa về 9.0 và giải thích ngắn.
      - TOEFL: 0-120. Nếu > 120 → nhắc chuẩn iBT.
      - TOEIC: 10-990. Nếu > 990 → sửa về 990.
      - GPA: Hỏi lại thang điểm khi > 4.0 (hoặc > 10 nếu thang 10).
      - Tuổi < 15, > 100; kinh nghiệm âm, > 50; mâu thuẫn tuổi/kinh nghiệm → nhắc nhẹ và điều chỉnh.
      - Ngôn từ không phù hợp → từ chối lịch sự, chuyển hướng chủ đề nghề nghiệp.

      ## CẤU TRÚC TRẢ LỜI NGẮN GỌN (ƯU TIÊN ĐẦY ĐỦ Ý CHÍNH)
      ```markdown
      [Nếu có lỗi → ⚠️ thông báo 1 dòng]

      ### 🧭 Tổng quan
      - Tóm tắt mục tiêu của bạn (1-2 câu)
      - Định hướng ban đầu phù hợp 2025

      ### ✅ Lý do
      - 1-2 lý do chính (nhu cầu, lương, cơ hội)

      ### ⚖️ So sánh nhanh (nếu bạn đang phân vân)
      | Tiêu chí | Lựa chọn A | Lựa chọn B | Phù hợp với |
      |----------|-----------|-----------|-------------|
      | Độ khó học | ⭐⭐ | ⭐⭐⭐⭐ | Người mới: A |
      | Cơ hội việc làm | 🔥 Cao | 📉 TB | 2025: A tốt |
      | Lương TB | 15-30M | 12-25M | Fresher: A |
      | Công nghệ | React, TS | Vue, Nuxt | ... |
      | Thời gian học | 3-6 tháng | 6-9 tháng | ... |

      ### 🚀 Lộ trình gợi ý (rất ngắn)
      - Tháng 1-2: Nền tảng + 1 mini project
      - Tháng 3-4: 2 dự án thực tế + GitHub/Portfolio
      - Tháng 5-6: Chuyên sâu + CV/Interview

      ### ❓ Câu hỏi tiếp theo
      - 2-3 câu hỏi để hiểu rõ hơn (thời gian, mục tiêu, nền tảng)
      ```

      QUY TẮC: Không thêm tiêu đề meta, dùng Markdown tối giản, emoji vừa phải.
      """;

  /**
   * Process a chat message and get AI response
   * Supports two modes:
   * 1. GENERAL_CAREER_ADVISOR - General career counseling
   * 2. EXPERT_MODE - Specialized advice for specific domain/industry/role
   */
  @Transactional
  public ChatResponse chat(ChatRequest request, User user) {
    // 1. CHECK USAGE LIMIT FIRST
    usageLimitService.checkAndRecordUsage(
        user.getId(),
        FeatureType.AI_CHATBOT_REQUESTS);

    // 1b. PREMIUM VALIDATION for special agent mode
    if (request.getAiAgentMode() != null
        && "deep-research-pro-preview-12-2025".equalsIgnoreCase(request.getAiAgentMode())) {
      boolean hasPremium = premiumService.hasActivePremiumSubscription(user.getId());
      if (!hasPremium) {
        throw new ApiException(ErrorCode.FORBIDDEN, "Chỉ tài khoản Premium mới có thể chọn chế độ AI Deep Research");
      }
    }

    // 2. Validate chat mode and required fields
    validateChatRequest(request);

    // 3. Validate user input (profanity only - let AI handle auto-correction)
    try {
      inputValidationService.validateTextOrThrow(request.getMessage());
    } catch (IllegalArgumentException ex) {
      log.warn("Input validation failed: {}", ex.getMessage());
      // Don't throw error - let AI handle it with auto-correction
    }

    Long sessionId = request.getSessionId();

    // Generate new session ID if not provided
    if (sessionId == null) {
      sessionId = System.currentTimeMillis();
      log.info("Starting new {} chat session {} for user {}",
          request.getChatMode(), sessionId, user.getId());
    }

    // Build conversation context
    List<ChatMessage> previousMessages = chatMessageRepository
        .findBySessionIdOrderByCreatedAtAsc(sessionId);

    // Add correction hints to help AI detect and fix invalid inputs
    String messageWithHints = addCorrectionHints(request.getMessage());
    log.info("Chat mode: {}, Original message: {}", request.getChatMode(), request.getMessage());

    // Call AI with automatic provider selection and fallback
    String aiResponse = callAIWithFallback(messageWithHints, previousMessages, request);
    // Sanitize: remove '####' headings from AI response as requested
    aiResponse = sanitizeAIResponse(aiResponse);

    // Save to database (save ONLY user's original message without any prefix)
    ChatMessage chatMessage = ChatMessage.builder()
        .user(user)
        .sessionId(sessionId)
        .userMessage(request.getMessage()) // Save raw user message
        .aiResponse(aiResponse)
        .createdAt(LocalDateTime.now())
        .build();

    chatMessageRepository.save(chatMessage);

    log.info("Chat session {} - Mode: {}, User: {}, AI response length: {}",
        sessionId, request.getChatMode(), user.getId(), aiResponse.length());

    // Build response with mode and expert context
    ChatResponse.ChatResponseBuilder responseBuilder = ChatResponse.builder()
        .sessionId(sessionId)
        .message(request.getMessage())
        .aiResponse(aiResponse)
        .timestamp(chatMessage.getCreatedAt())
        .chatMode(request.getChatMode());

    // Add expert context if in EXPERT_MODE
    if (request.getChatMode() == com.exe.skillverse_backend.ai_service.enums.ChatMode.EXPERT_MODE) {
      // Try to get mediaUrl from database
      String mediaUrl = getExpertMediaUrl(request.getDomain(), request.getIndustry(), request.getJobRole());

      responseBuilder.expertContext(ChatResponse.ExpertContext.builder()
          .domain(request.getDomain())
          .industry(request.getIndustry())
          .jobRole(request.getJobRole())
          .expertName(buildExpertName(request.getJobRole()))
          .mediaUrl(mediaUrl)
          .build());
    }

    return responseBuilder.build();
  }

  /**
   * Validate chat request based on mode
   */
  private void validateChatRequest(ChatRequest request) {
    if (request.getChatMode() == null) {
      request.setChatMode(com.exe.skillverse_backend.ai_service.enums.ChatMode.GENERAL_CAREER_ADVISOR);
    }

    // Validate EXPERT_MODE requirements
    if (request.getChatMode() == com.exe.skillverse_backend.ai_service.enums.ChatMode.EXPERT_MODE) {
      if (request.getJobRole() == null || request.getJobRole().trim().isEmpty()) {
        throw new ApiException(ErrorCode.BAD_REQUEST,
            "Job role is required for EXPERT_MODE");
      }
    }
  }

  /**
   * Build expert name for display
   */
  private String buildExpertName(String jobRole) {
    if (jobRole == null || jobRole.isEmpty()) {
      return "Career Expert";
    }
    return jobRole + " Expert";
  }

  /**
   * Remove '####' markdown headings from AI responses while preserving code
   * blocks
   */
  private String sanitizeAIResponse(String content) {
    if (content == null || content.isEmpty()) {
      return content;
    }

    String[] lines = content.split("\n", -1);
    StringBuilder sanitized = new StringBuilder(content.length());
    boolean inCodeBlock = false;

    for (String line : lines) {
      String trimmed = line.trim();
      if (trimmed.startsWith("```")) {
        inCodeBlock = !inCodeBlock;
        sanitized.append(line).append('\n');
        continue;
      }

      if (!inCodeBlock && trimmed.startsWith("####")) {
        // Remove leading '####' and following spaces only
        String withoutHashes = line.replaceFirst("^####\\s*", "");
        sanitized.append(withoutHashes).append('\n');
      } else {
        sanitized.append(line).append('\n');
      }
    }

    return sanitized.toString();
  }

  /**
   * Call Mistral AI for chat using Spring AI
   * Using Mistral AI for latest 2025 career trends and insights
   */
  private String callAIWithFallback(String userMessage, List<ChatMessage> previousMessages, ChatRequest request) {
    log.info("Calling Mistral AI chatbot using Spring AI");

    try {
      String agentSuffix = (request.getAiAgentMode() != null
          && "deep-research-pro-preview-12-2025".equalsIgnoreCase(request.getAiAgentMode()))
              ? "\nMODE: Deep Research Pro — Áp dụng phân tích sâu, kiểm chứng thông tin, đưa lộ trình suy luận có cấu trúc, ưu tiên bằng chứng và dữ liệu thị trường 2025.\nQUAN TRỌNG: \n1. Hãy bắt đầu câu trả lời bằng một khối suy nghĩ được bao quanh bởi thẻ <thinking>...</thinking>.\n2. Kết thúc câu trả lời bằng danh sách 3 câu hỏi gợi ý tiếp theo được bao quanh bởi thẻ <suggestions>...</suggestions> (mỗi câu một dòng)."
              : "\nMODE: Normal Agent — Hành vi theo tác tử: nhận diện ý định, kiểm chứng thông tin cơ bản, tư duy có cấu trúc, trả lời rõ ràng.\nQUAN TRỌNG: \n1. Hãy bắt đầu câu trả lời bằng một khối suy nghĩ được bao quanh bởi thẻ <thinking>...</thinking>.\n2. Kết thúc câu trả lời bằng danh sách 3 câu hỏi gợi ý tiếp theo được bao quanh bởi thẻ <suggestions>...</suggestions> (mỗi câu một dòng).";
      if (request.getAiAgentMode() != null
          && "deep-research-pro-preview-12-2025".equalsIgnoreCase(request.getAiAgentMode())) {
        try {
          return callGeminiForChat(userMessage, previousMessages, request, agentSuffix, geminiChatModel,
              "Gemini Primary");
        } catch (Exception ge) {
          String msg = ge.getMessage() != null ? ge.getMessage().toLowerCase() : "";
          if (msg.contains("429") || msg.contains("quota") || msg.contains("resource_exhausted")
              || msg.contains("rate limit")) {
            try {
              return callGeminiForChat(userMessage, previousMessages, request, agentSuffix, geminiFallback1ChatModel,
                  "Gemini Fallback");
            } catch (Exception ge2) {
              String normalSuffix = "\nMODE: Normal Agent — Hành vi theo tác tử: nhận diện ý định, kiểm chứng thông tin cơ bản, tư duy có cấu trúc, trả lời rõ ràng.\nQUAN TRỌNG: \n1. Hãy bắt đầu câu trả lời bằng một khối suy nghĩ được bao quanh bởi thẻ <thinking>...</thinking>.\n2. Kết thúc câu trả lời bằng danh sách 3 câu hỏi gợi ý tiếp theo được bao quanh bởi thẻ <suggestions>...</suggestions>.";
              return callMistralForChat(userMessage, previousMessages, request, normalSuffix);
            }
          } else {
            String normalSuffix = "\nMODE: Normal Agent — Hành vi theo tác tử: nhận diện ý định, kiểm chứng thông tin cơ bản, tư duy có cấu trúc, trả lời rõ ràng.\nQUAN TRỌNG: \n1. Hãy bắt đầu câu trả lời bằng một khối suy nghĩ được bao quanh bởi thẻ <thinking>...</thinking>.\n2. Kết thúc câu trả lời bằng danh sách 3 câu hỏi gợi ý tiếp theo được bao quanh bởi thẻ <suggestions>...</suggestions>.";
            return callMistralForChat(userMessage, previousMessages, request, normalSuffix);
          }
        }
      }
      return callMistralForChat(userMessage, previousMessages, request, agentSuffix);
    } catch (Exception e) {
      log.error("Mistral AI failed: {}", e.getMessage());

      try {
        String normalAgentSuffix = "\nMODE: Normal Agent — Hành vi theo tác tử: nhận diện ý định, kiểm chứng thông tin cơ bản, tư duy có cấu trúc, trả lời rõ ràng.";
        return callMistralForChat(userMessage, previousMessages, request, normalAgentSuffix);
      } catch (Exception e2) {
        // FALLBACK: Return a helpful response instead of throwing error
        return generateFallbackResponse(userMessage);
      }
    }
  }

  /**
   * Call Mistral AI for chat conversation with context using Spring AI ChatClient
   * Mistral provides more recent training data for 2025 career trends
   */
  private String callMistralForChat(String userMessage, List<ChatMessage> previousMessages, ChatRequest request,
      String agentSuffix) {
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

      // DETERMINE SYSTEM PROMPT based on chat mode
      String systemPrompt;

      if (request.getChatMode() == com.exe.skillverse_backend.ai_service.enums.ChatMode.EXPERT_MODE) {
        // EXPERT_MODE: Try to get specialized prompt
        systemPrompt = expertPromptService.getSystemPrompt(
            request.getDomain(),
            request.getIndustry(),
            request.getJobRole());

        // If no expert prompt found, fall back to general prompt
        if (systemPrompt == null) {
          log.warn("No expert prompt found for role: {}, falling back to general advisor",
              request.getJobRole());
          systemPrompt = SYSTEM_PROMPT;
        } else {
          log.info("Using expert prompt for: {} - {} - {}",
              request.getDomain(), request.getIndustry(), request.getJobRole());
        }
      } else {
        // GENERAL_CAREER_ADVISOR: Use default prompt
        // Use simpler prompt for first message, full prompt for subsequent
        boolean isFirstTurn = previousMessages == null || previousMessages.isEmpty();
        systemPrompt = isFirstTurn ? SYSTEM_PROMPT_SIMPLE : SYSTEM_PROMPT;
        log.info("Using general career advisor prompt (first turn: {})", isFirstTurn);
      }

      // Append critical instruction
      String finalSystemPrompt = systemPrompt +
          "\nCRITICAL: Hãy trả lời bằng đúng ngôn ngữ người dùng đang dùng (ưu tiên Tiếng Việt). Nếu phát hiện yêu cầu vô lý (ví dụ mục tiêu IELTS 10.0), hãy giải thích và đưa gợi ý hợp lệ bằng Tiếng Việt.";
      if (agentSuffix != null && !agentSuffix.isEmpty()) {
        finalSystemPrompt = finalSystemPrompt + agentSuffix;
      }

      // Use Spring AI ChatClient for Mistral
      return ChatClient.builder(mistralChatModel)
          .build()
          .prompt()
          .system(finalSystemPrompt)
          .user(conversationHistory)
          .call()
          .content();

    } catch (Exception e) {
      log.error("Mistral chat error: {}", e.getMessage());
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
          "Mistral AI service unavailable: " + e.getMessage());
    }
  }

  private String callGeminiForChat(String userMessage, List<ChatMessage> previousMessages, ChatRequest request,
      String agentSuffix, ChatModel model, String label) {
    StringBuilder contextBuilder = new StringBuilder();
    contextBuilder.append("Conversation history:\n");
    for (ChatMessage prev : previousMessages) {
      contextBuilder.append("User: ").append(prev.getUserMessage()).append("\n");
      contextBuilder.append("Assistant: ").append(prev.getAiResponse()).append("\n");
    }
    contextBuilder.append("User: ").append(userMessage);
    String conversationHistory = contextBuilder.toString();
    String systemPrompt;
    if (request.getChatMode() == com.exe.skillverse_backend.ai_service.enums.ChatMode.EXPERT_MODE) {
      systemPrompt = expertPromptService.getSystemPrompt(
          request.getDomain(),
          request.getIndustry(),
          request.getJobRole());
      if (systemPrompt == null) {
        systemPrompt = SYSTEM_PROMPT;
      }
    } else {
      boolean isFirstTurn = previousMessages == null || previousMessages.isEmpty();
      systemPrompt = isFirstTurn ? SYSTEM_PROMPT_SIMPLE : SYSTEM_PROMPT;
    }
    String finalSystemPrompt = systemPrompt +
        "\nCRITICAL: Hãy trả lời bằng đúng ngôn ngữ người dùng đang dùng (ưu tiên Tiếng Việt). Nếu phát hiện yêu cầu vô lý (ví dụ mục tiêu IELTS 10.0), hãy giải thích và đưa gợi ý hợp lệ bằng Tiếng Việt.";
    if (agentSuffix != null && !agentSuffix.isEmpty()) {
      finalSystemPrompt = finalSystemPrompt + agentSuffix;
    }
    return ChatClient.builder(model)
        .build()
        .prompt()
        .system(finalSystemPrompt)
        .user(conversationHistory)
        .call()
        .content();
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
              .messageCount(messages.size() * 2) // Multiply by 2 because each entity has User + AI message
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
   * Enhanced version with comprehensive error detection based on new system
   * prompt
   * Example: "IELTS 10.0" → adds hint "[Note: IELTS max is 9.0, user likely meant
   * 9.0]"
   */
  private String addCorrectionHints(String message) {
    if (message == null || message.isEmpty()) {
      return message;
    }

    StringBuilder hints = new StringBuilder();
    String lowerMessage = message.toLowerCase();

    // CATEGORY 1: Điểm số & Chứng chỉ

    // IELTS: 0.0 - 9.0 (bước nhảy 0.5)
    if (lowerMessage.matches(".*ielts.*\\b(1[0-9]|[2-9]\\d|\\d{3,})(\\.\\d+)?\\b.*") ||
        lowerMessage.contains("ielts 10") ||
        lowerMessage.contains("ielts 11") ||
        lowerMessage.contains("ielts 12")) {
      hints.append("[⚠️ Phát hiện điểm IELTS > 9.0. IELTS max = 9.0, hãy auto-correct và giải thích.] ");
    }

    // IELTS không đúng bước nhảy (8.3, 7.7)
    if (lowerMessage.matches(".*ielts.*\\b\\d+\\.(1|2|3|4|6|7|8|9)\\b.*")) {
      hints.append("[⚠️ Phát hiện điểm IELTS không đúng bước nhảy 0.5. Hãy làm tròn về bội số 0.5 gần nhất.] ");
    }

    // TOEFL: 0 - 120 (TOEFL iBT)
    if (lowerMessage.matches(".*toefl.*\\b(1[3-9]\\d|[2-9]\\d{2})\\b.*")) {
      hints.append("[⚠️ Phát hiện điểm TOEFL > 120. TOEFL iBT max = 120, hãy auto-correct và giải thích.] ");
    }

    // TOEFL PBT (cũ) - có thể nhầm lẫn
    if (lowerMessage.matches(".*toefl.*\\b([4-6]\\d{2})\\b.*")) {
      hints.append("[⚠️ Phát hiện điểm TOEFL cao, có thể nhầm với TOEFL PBT cũ. Hãy làm rõ chuẩn TOEFL iBT.] ");
    }

    // TOEIC: 10 - 990
    if (lowerMessage.matches(".*toeic.*\\b(\\d{4,})\\b.*")) {
      hints.append("[⚠️ Phát hiện điểm TOEIC > 990. TOEIC max = 990, hãy auto-correct và giải thích.] ");
    }

    // GPA: Multiple scales detection
    if (lowerMessage.matches(".*gpa.*\\b([5-9]|\\d{2,})(\\.\\d+)?\\b.*") && !lowerMessage.contains("thang 10")) {
      hints.append("[⚠️ Phát hiện GPA > 4.0. Nếu thang 4.0, max = 4.0. Hãy hỏi user làm rõ thang điểm.] ");
    }

    // GPA thang 10 nhưng quá cao
    if (lowerMessage.matches(".*gpa.*thang.*10.*\\b(1[1-9]|\\d{2,})\\b.*")) {
      hints.append("[⚠️ Phát hiện GPA thang 10 > 10.0. GPA thang 10 max = 10.0, hãy auto-correct.] ");
    }

    // HSK: Cũ (1-6) vs Mới (1-9)
    if (lowerMessage.matches(".*hsk.*\\b([7-9])\\b.*")) {
      hints.append("[⚠️ Phát hiện HSK cấp 7-9. HSK từ 2021 có thêm cấp 7-9. Hãy làm rõ chuẩn cũ hay mới.] ");
    }

    // JLPT: N5-N1 (không có N0, N6)
    if (lowerMessage.matches(".*jlpt.*\\b(n[06])\\b.*")) {
      hints.append("[⚠️ Phát hiện JLPT N0/N6 không tồn tại. JLPT có N5-N1, hãy auto-correct thành N1.] ");
    }

    // TOPIK: Level 1-6
    if (lowerMessage.matches(".*topik.*\\b(level\\s*)?([7-9])\\b.*")) {
      hints.append("[⚠️ Phát hiện TOPIK Level > 6. TOPIK max = Level 6, hãy auto-correct.] ");
    }

    // AWS/Cloud Certifications: Không có điểm %
    if (lowerMessage.matches(".*(aws|google cloud|azure).*cert.*\\b\\d+%\\b.*")) {
      hints.append(
          "[⚠️ Phát hiện điểm % cho cloud certification. Cloud cert chỉ có Pass/Fail, không có điểm %.] ");
    }

    // CATEGORY 2: Thông tin Cá nhân

    // Tuổi âm
    if (lowerMessage.matches(".*(tuổi|năm sinh|age).*-\\d+.*")) {
      hints.append("[⚠️ Phát hiện tuổi âm. Hãy hỏi lại user xác nhận tuổi.] ");
    }

    // Tuổi quá nhỏ (< 15)
    if (lowerMessage.matches(".*(tuổi|năm sinh|age).*\\b([0-9]|1[0-4])\\b.*")) {
      hints.append("[⚠️ Phát hiện tuổi < 15. Hãy hỏi lại hoặc tư vấn phù hợp cho độ tuổi.] ");
    }

    // Tuổi quá lớn (> 100)
    if (lowerMessage.matches(".*(tuổi|năm sinh|age).*\\b([1-9]\\d{2,})\\b.*")) {
      hints.append("[⚠️ Phát hiện tuổi > 100. Hãy hỏi lại user xác nhận.] ");
    }

    // Kinh nghiệm âm
    if (lowerMessage.matches(".*(kinh nghiệm|experience).*-\\d+.*")) {
      hints.append("[⚠️ Phát hiện kinh nghiệm âm. Hãy auto-correct thành 0 năm.] ");
    }

    // Kinh nghiệm quá lớn (> 50 năm)
    if (lowerMessage.matches(".*(kinh nghiệm|experience).*\\b([5-9]\\d|\\d{3,})\\b.*")) {
      hints.append("[⚠️ Phát hiện kinh nghiệm > 50 năm. Hãy hỏi lại user xác nhận.] ");
    }

    // Mâu thuẫn tuổi và kinh nghiệm
    if (lowerMessage.matches(".*\\b(1[5-9]|2[0-5])\\b.*tuổi.*\\b([1-9]\\d)\\b.*(kinh nghiệm|experience).*")) {
      hints.append("[⚠️ Phát hiện mâu thuẫn tuổi và kinh nghiệm. Hãy auto-correct và giải thích.] ");
    }

    // Mức lương phi thực tế (quá cao cho fresher)
    if (lowerMessage.matches(".*(fresher|mới|junior).*\\b(\\d{3,})\\b.*(triệu|million).*")) {
      hints.append("[⚠️ Phát hiện mức lương quá cao cho fresher. Hãy điều chỉnh kỳ vọng theo thị trường.] ");
    }

    // CATEGORY 3: Thời gian & Mốc thời gian

    // Thời gian học quá ngắn cho skill phức tạp
    if (lowerMessage
        .matches(".*(học|learn).*(data science|machine learning|ai|blockchain).*\\b(1|2|3)\\b.*(ngày|day).*")) {
      hints.append("[⚠️ Phát hiện thời gian học quá ngắn cho skill phức tạp. Hãy điều chỉnh kỳ vọng.] ");
    }

    // Thời gian học quá dài cho skill đơn giản
    if (lowerMessage.matches(".*(học|learn).*(html|css|cơ bản|basic).*\\b([5-9]|\\d{2,})\\b.*(năm|year).*")) {
      hints.append("[⚠️ Phát hiện thời gian học quá dài cho skill đơn giản. Hãy hỏi lại user.] ");
    }

    // Năm tốt nghiệp không hợp lý
    if (lowerMessage.matches(".*(tốt nghiệp|graduation).*\\b(1[0-7]\\d{2}|20[4-9]\\d)\\b.*")) {
      hints.append("[⚠️ Phát hiện năm tốt nghiệp không hợp lý (< 1800 hoặc > 2035). Hãy hỏi lại.] ");
    }

    // CATEGORY 4: Thông tin Kỹ thuật

    // Số giờ học quá nhiều (> 168 giờ/tuần)
    if (lowerMessage.matches(".*\\b([2-9]\\d{2,})\\b.*(giờ|hour).*(tuần|week).*")) {
      hints.append("[⚠️ Phát hiện số giờ học > 168 giờ/tuần. Một tuần chỉ có 168 giờ.] ");
    }

    // Số giờ học quá ít (< 1 giờ/tuần)
    if (lowerMessage.matches(".*\\b(0\\.\\d+|0)\\b.*(giờ|hour).*(tuần|week).*")) {
      hints.append("[⚠️ Phát hiện số giờ học quá ít. Hãy khuyên tăng thời gian học.] ");
    }

    // Kinh nghiệm với công nghệ mới không khả thi
    if (lowerMessage.matches(
        ".*(chatgpt|gpt-4|midjourney).*\\b([5-9]|\\d{2,})\\b.*(năm|year).*(kinh nghiệm|experience).*")) {
      hints.append(
          "[⚠️ Phát hiện kinh nghiệm với công nghệ mới không khả thi. Hãy auto-correct theo timeline thực tế.] ");
    }

    // CATEGORY 5: Logic & Ngữ cảnh

    // Mâu thuẫn: Chưa học nhưng muốn làm senior
    if (lowerMessage.matches(".*(chưa học|mới bắt đầu|beginner).*(senior|lead|manager).*")) {
      hints.append("[⚠️ Phát hiện mâu thuẫn: chưa học nhưng muốn làm senior. Hãy tư vấn lộ trình từ junior.] ");
    }

    // CATEGORY 6: Nội dung Không phù hợp

    // Ngôn từ thô tục (basic detection)
    if (lowerMessage.matches(".*(địt|đụ|đéo|fuck|shit|damn).*")) {
      hints.append("[⚠️ Phát hiện ngôn từ không phù hợp. Hãy từ chối lịch sự và hướng về chủ đề nghề nghiệp.] ");
    }

    // Câu hỏi không liên quan đến nghề nghiệp
    if (lowerMessage.matches(".*(mèo|cat|ăn|food|thời tiết|weather|giải trí|entertainment).*")) {
      hints.append(
          "[⚠️ Phát hiện câu hỏi không liên quan nghề nghiệp. Hãy redirect về chủ đề học tập/nghề nghiệp.] ");
    }

    // If hints found, prepend to message for AI to see
    if (hints.length() > 0) {
      log.info("Correction hints found: {}", hints.toString());
      return hints.toString() + "\n\nCâu hỏi gốc: " + message;
    }

    log.info("No correction hints needed for message: {}", message);
    return message;
  }

  /**
   * Generate fallback response when AI service is unavailable
   * Includes auto-correction logic for common errors
   */
  private String generateFallbackResponse(String userMessage) {
    log.info("Generating fallback response for: {}", userMessage);

    String lowerMessage = userMessage.toLowerCase();

    // Handle IELTS 10.0 error
    if (lowerMessage.contains("ielts 10") || lowerMessage.contains("ielts 11")
        || lowerMessage.contains("ielts 12")) {
      return """
          ⚠️ *Mình nhận thấy bạn nhập IELTS 10.0, nhưng thang điểm IELTS chỉ từ 0-9.0. Mình hiểu bạn muốn đạt điểm **9.0** (xuất sắc - trình độ gần như người bản xứ) nhé!* 😊

          ### 🧭 Tổng quan cá nhân hóa
          - Mục tiêu: Đạt IELTS 9.0 (điểm tối đa)
          - Đánh giá: Mục tiêu rất cao nhưng khả thi với lộ trình đúng
          - Định hướng: Tập trung vào 4 kỹ năng: Listening, Reading, Writing, Speaking

          ### ✅ Lý do nên theo đuổi IELTS 9.0
          - **Cơ hội việc làm**: IELTS 9.0 mở ra cơ hội làm việc tại các công ty đa quốc gia
          - **Học bổng**: Nhiều học bổng yêu cầu IELTS 7.0-8.5, 9.0 sẽ có lợi thế lớn
          - **Định cư**: IELTS 9.0 giúp định cư tại các nước nói tiếng Anh

          ### 🚀 Lộ trình học theo mốc thời gian

          #### Giai đoạn 1: Nền tảng (Tháng 1-2)
          - **Kiến thức cốt lõi**: Hiểu format bài thi IELTS, các dạng câu hỏi
          - **Kỹ năng thực hành**: Làm bài test mẫu, đánh giá trình độ hiện tại
          - **Tài nguyên**: Cambridge IELTS books, IELTS Official Practice Materials
          - **Checklist hoàn thành**:
            - [ ] Làm được bài test mẫu và đánh giá điểm
            - [ ] Hiểu rõ format 4 phần thi
            - [ ] Xác định điểm yếu cần cải thiện

          #### Giai đoạn 2: Thực hành nâng cao (Tháng 3-4)
          - **Dự án thực tế**: Luyện tập hàng ngày với các dạng bài khác nhau
          - **Công cụ cần thành thạo**: IELTS practice apps, online tests
          - **Portfolio**: Tạo bộ sưu tập bài viết và speaking samples
          - **Checklist hoàn thành**:
            - [ ] Hoàn thành ít nhất 20 bài test practice
            - [ ] Có bộ sưu tập bài viết chất lượng
            - [ ] Recording speaking practice để tự đánh giá

          #### Giai đoạn 3: Chuyên sâu & Thi thật (Tháng 5-6)
          - **Kỹ năng nâng cao**: Time management, stress management trong phòng thi
          - **Chứng chỉ**: Đăng ký thi IELTS chính thức
          - **Chuẩn bị thi**:
            - Mock test với điều kiện thật
            - Review lại các lỗi thường gặp
            - Chuẩn bị tâm lý và sức khỏe
          - **Networking**: Tham gia IELTS study groups, forums

          ### 🧩 Kỹ năng cốt lõi & Công cụ cần thành thạo

          #### Kỹ năng kỹ thuật (Technical Skills):
          - **Listening**: Khả năng nghe hiểu accent khác nhau, note-taking
          - **Reading**: Skimming, scanning, time management
          - **Writing**: Task 1 (charts/graphs), Task 2 (essay), coherence & cohesion
          - **Speaking**: Fluency, pronunciation, vocabulary range

          #### Công cụ & Công nghệ (Tools & Technologies):
          - **Bắt buộc**: Cambridge IELTS books, IELTS Official Practice Materials
          - **Nên biết**: IELTS practice apps, online mock tests
          - **Bonus**: Pronunciation apps, vocabulary builders

          ### 💰 Mức lương tham khảo (Việt Nam 2025)

          ```
          IELTS 6.0-6.5: Cơ hội việc làm cơ bản với tiếng Anh
          IELTS 7.0-7.5: Cơ hội việc làm tốt, học bổng
          IELTS 8.0-8.5: Cơ hội việc làm cao cấp, học bổng toàn phần
          IELTS 9.0: Cơ hội việc làm đỉnh cao, định cư nước ngoài
          ```

          ### 📚 Tài nguyên học tập được đề xuất

          #### Khóa học Online (chọn 1-2):
          1. **IELTS Official Practice Materials** (Cambridge)
             - Nội dung: Bài test chính thức từ Cambridge
             - Thời lượng: 20+ bài test
             - Giá: ~$50
             - Phù hợp: Tất cả levels

          2. **IELTS Liz** (YouTube)
             - Tại sao nên học: Free, chất lượng cao
             - Link: youtube.com/c/ieltsliz

          #### Sách (chọn 1 quyển):
          - **"The Official Cambridge Guide to IELTS"**
            - Phù hợp: Người mới bắt đầu
            - Điểm nổi bật: Hướng dẫn chi tiết từ Cambridge

          ### ⚠️ Rủi ro & Cách khắc phục

          #### Rủi ro 1: Áp lực điểm số quá cao
          - **Nguyên nhân**: Kỳ vọng không thực tế
          - **Giải pháp**:
            - Đặt mục tiêu từng giai đoạn (6.0 → 7.0 → 8.0 → 9.0)
            - Tập trung vào cải thiện kỹ năng thay vì chỉ điểm số

          #### Rủi ro 2: Học không đều các kỹ năng
          - **Dấu hiệu nhận biết**: Một kỹ năng tốt, kỹ năng khác kém
          - **Giải pháp**:
            - Dành thời gian đều cho cả 4 kỹ năng
            - Tập trung vào kỹ năng yếu nhất

          #### Rủi ro 3: Thiếu thực hành
          - **Nguyên nhân**: Chỉ học lý thuyết
          - **Giải pháp**:
            - Làm bài test hàng ngày
            - Ghi âm speaking practice
            - Viết essay và nhờ người khác chấm

          ### 💡 Lời khuyên từ Meowl

          IELTS 9.0 là mục tiêu rất cao nhưng hoàn toàn khả thi! Hãy kiên trì luyện tập hàng ngày, tập trung vào cải thiện từng kỹ năng một cách có hệ thống. Meowl tin bạn làm được! 🐾

          ### ❓ Câu hỏi để Meowl hiểu bạn hơn

          - Bạn hiện tại đang ở trình độ IELTS nào? (chưa thi / đã thi được bao nhiêu?)
          - Bạn có bao nhiêu thời gian mỗi tuần dành cho việc học IELTS? (5-10h / 10-20h / 20+h)
          - Kỹ năng nào bạn cảm thấy khó nhất: Listening, Reading, Writing, hay Speaking?
          - Bạn có định hướng sử dụng IELTS để làm gì? (du học / định cư / công việc)
          """;
    }

    // Handle other common errors
    if (lowerMessage.contains("toeic") && (lowerMessage.contains("1000") || lowerMessage.contains("995"))) {
      return """
          ⚠️ *Mình nhận thấy bạn nhập điểm TOEIC > 990, nhưng TOEIC có điểm tối đa là 990. Mình hiểu bạn muốn đạt **990 điểm** (gần như perfect) nhé!*

          ### 🧭 Tổng quan cá nhân hóa
          - Mục tiêu: Đạt TOEIC 990 (điểm tối đa)
          - Đánh giá: Mục tiêu rất cao, cần lộ trình chuyên sâu
          - Định hướng: Tập trung vào Listening và Reading

          ### ✅ Lý do nên theo đuổi TOEIC 990
          - **Cơ hội việc làm**: TOEIC 990 mở ra cơ hội làm việc tại các công ty đa quốc gia
          - **Thăng tiến**: Nhiều công ty yêu cầu TOEIC 800+ cho vị trí quản lý
          - **Học bổng**: Một số học bổng yêu cầu TOEIC 900+

          ### 🚀 Lộ trình học theo mốc thời gian

          #### Giai đoạn 1: Nền tảng (Tháng 1-2)
          - **Kiến thức cốt lõi**: Hiểu format bài thi TOEIC, các dạng câu hỏi
          - **Kỹ năng thực hành**: Làm bài test mẫu, đánh giá trình độ hiện tại
          - **Tài nguyên**: TOEIC Official Practice Materials

          #### Giai đoạn 2: Thực hành nâng cao (Tháng 3-4)
          - **Dự án thực tế**: Luyện tập hàng ngày với các dạng bài khác nhau
          - **Công cụ cần thành thạo**: TOEIC practice apps, online tests

          #### Giai đoạn 3: Chuyên sâu & Thi thật (Tháng 5-6)
          - **Kỹ năng nâng cao**: Time management, stress management
          - **Chứng chỉ**: Đăng ký thi TOEIC chính thức

          ### 💰 Mức lương tham khảo (Việt Nam 2025)

          ```
          TOEIC 600-700: Cơ hội việc làm cơ bản
          TOEIC 700-800: Cơ hội việc làm tốt
          TOEIC 800-900: Cơ hội việc làm cao cấp
          TOEIC 900-990: Cơ hội việc làm đỉnh cao
          ```

          ### 💡 Lời khuyên từ Meowl

          TOEIC 990 là mục tiêu rất cao nhưng hoàn toàn khả thi! Hãy kiên trì luyện tập hàng ngày, tập trung vào cải thiện từng kỹ năng một cách có hệ thống. Meowl tin bạn làm được! 🐾
          """;
    }

    // Default fallback response
    return """
        Xin lỗi, hiện tại hệ thống AI đang gặp sự cố tạm thời. Tuy nhiên, mình vẫn có thể giúp bạn với một số câu hỏi cơ bản:

        ### 🎓 Các chủ đề mình có thể hỗ trợ:
        - **Chọn ngành học**: Khoa học Máy tính, Kinh doanh, Data Science, AI, etc.
        - **Xu hướng nghề nghiệp 2025**: Ngành nào đang hot, mức lương tham khảo
        - **Lộ trình học tập**: Từng bước cụ thể để đạt mục tiêu
        - **Kỹ năng cần thiết**: Technical skills, soft skills

        ### 💡 Câu hỏi gợi ý:
        - "Xu hướng nghề nghiệp công nghệ 2025 là gì?"
        - "Nên học Khoa học Máy tính hay Kinh doanh?"
        - "Làm sao để trở thành Data Scientist?"
        - "Kỹ năng quan trọng nhất hiện nay là gì?"

        Hãy thử hỏi một trong những câu hỏi trên, hoặc mô tả cụ thể hơn về mục tiêu của bạn nhé! 🐾
        """;
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

  /**
   * Get expert media URL from database
   * Returns null if not found
   */
  private String getExpertMediaUrl(String domain, String industry, String jobRole) {
    try {
      // Try exact match first
      return expertPromptConfigRepository
          .findByDomainAndIndustryAndJobRoleAndIsActiveTrue(domain, industry, jobRole)
          .map(config -> config.getMediaUrl())
          .orElse(null);
    } catch (Exception e) {
      log.warn("Failed to get media URL for expert {}/{}/{}: {}",
          domain, industry, jobRole, e.getMessage());
      return null;
    }
  }

  // ==================== ADMIN STATISTICS ====================

  /**
   * Get total count of distinct chat sessions in the system (Admin only)
   */
  @Transactional(readOnly = true)
  public Long getTotalSessionCount() {
    return chatMessageRepository.countDistinctSessions();
  }

  /**
   * Get total count of messages in the system (Admin only)
   */
  @Transactional(readOnly = true)
  public Long getTotalMessageCount() {
    return chatMessageRepository.countTotalMessages();
  }
}
