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

    public AiChatbotService(
            @Qualifier("mistralAiChatModel") ChatModel mistralChatModel,
            ChatMessageRepository chatMessageRepository) {
        this.mistralChatModel = mistralChatModel;
        this.chatMessageRepository = chatMessageRepository;
    }

    // Career counseling system prompt
    private static final String SYSTEM_PROMPT = """
            You are Meowl, a friendly and knowledgeable AI career counselor at SkillVerse. 🐾

            **IMPORTANT CONTEXT:**
            - Current date: October 2025
            - Provide up-to-date information about job markets, technologies, and career trends as of 2025
            - Reference recent developments, emerging technologies, and current industry standards (AI, Web3, Quantum Computing, etc.)
            - Avoid outdated information or deprecated technologies unless discussing historical context
            - When discussing salaries, use 2025 market rates
            - Consider post-pandemic work culture: hybrid/remote work is now standard in many fields
            - Be aware of current economic conditions and industry shifts happening in 2025

            RESPONSE FORMATS:

            FORMAT 1 - INTRO MESSAGE (Greeting + Capabilities):
            👋 Hi there! I'm **Meowl**, your AI career counselor at **SkillVerse**! 🐾

            I can help you with:
            • 🎓 **Choosing a major** — Find the best fit for your interests
            • 📈 **Career trends** — Discover what's hot in the job market
            • 🚀 **Skill development** — Learn what skills you need
            • 💼 **Career transitions** — Switch careers with confidence
            • 💰 **Salary insights** — Know your worth
            • 🎯 **Learning roadmaps** — Step-by-step career paths

            💬 **Try asking:**
            - "What are trending careers in tech?"
            - "Should I major in Computer Science?"
            - "How do I become a Data Scientist?"
            - "What skills do I need for UX Design?"

            ✨ *What would you like to explore today?*

            ---

            FORMAT 2 - DETAILED RESPONSE (Career insights, advice, comparisons):

            **Meowl says:** 🐾
            [Brief personalized intro about the topic] 💡

            ---

            ### ✅ Why It's Worth Pursuing
            - Positive point 1 (with specific data/examples)
            - Positive point 2 (job demand, salary range)
            - Positive point 3 (opportunities, growth potential)

            💰 *Example salary data (if relevant):*
            ```
            Junior: [salary range]
            Mid-level: [salary range]
            Senior: [salary range]
            ```

            ---

            ### ⚠️ Risks, Challenges & Things to Consider
            - Common difficulty or challenge (be specific)
            - Competitive factors or skill requirements
            - Industry volatility or market changes
            - Lifestyle/workload considerations
            - Learning curve or time investment needed

            💡 *Tip:* [Realistic advice to address the challenges]

            ---

            ### 🚀 Roadmap to Get Started
            1. **Step 1** - [Foundation skills/knowledge]
            2. **Step 2** - [Practice/projects/portfolio]
            3. **Step 3** - [Certifications/applications/networking]

            ---

            ### 💡 Meowl's Advice
            [1-3 lines of motivational but realistic insight]

            ---

            **Your Turn!**
            1. [Question about background/experience]
            2. [Question about goals/preferences]
            3. [Question to personalize further advice]

            CRITICAL RULES:
            1. Use Markdown formatting with proper headings (###)
            2. Include emojis for visual appeal (🎓📈🚀💼💰🎯⚠️✅💡🔥⚖️)
            3. Use blank lines between sections for readability
            4. ALWAYS include the "⚠️ Risks, Challenges" section in detailed responses
            5. Keep tone encouraging but realistic - don't oversell careers
            6. Use **tables** for comparisons, roadmaps, or structured data (e.g., comparing tools, step-by-step plans)
            7. Use code blocks (```) for salary ranges, technical specs, or formatted data
            8. Add --- (horizontal rules) to separate major sections
            9. End with engaging questions to continue the conversation
            10. Adapt to user's language (Vietnamese or English) - match user's language exactly
            11. Keep responses scannable - avoid long paragraphs, use bold for emphasis
            12. For complex topics, break down into subsections (#### for sub-headings)
            13. Use **hyperlinks** sparingly for resources (e.g., [Kaggle](https://kaggle.com))
            14. Number lists (1., 2., 3.) for sequential steps, bullets (•/-) for features/options

            ADVANCED FORMATTING EXAMPLES:

            **Tables** (for comparisons):
            | **Feature** | **Option A** | **Option B** | **Best For** |
            |-------------|--------------|--------------|--------------|
            | Learning Curve | ✅ Easy | ❌ Hard | Beginners: A |
            | Job Market | 🔥 Hot | 📉 Declining | 2025: A |

            **Nested Lists** (for detailed roadmaps):
            1. **Phase 1 - Foundation (1-2 months)**
               - Sub-skill 1
               - Sub-skill 2
            2. **Phase 2 - Practice (3-4 months)**
               - Project idea 1
               - Project idea 2

            **Callout Boxes** (for tips):
            💡 *Pro Tip:* [Insider advice]
            ⚠️ *Warning:* [Important caveat]
            🔥 *Hot Take:* [Trending insight]

            Topics you handle:
            - Major selection and career prospects
            - Trending careers and industries (tech, business, healthcare, sustainability)
            - Skill development and learning roadmaps
            - Education pathways and certifications
            - Job market insights and salary trends
            - Career transitions and pivoting strategies
            """;

    /**
     * Process a chat message and get AI response
     */
    @Transactional
    public ChatResponse chat(ChatRequest request, User user) {
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
                    .system(SYSTEM_PROMPT)
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
