package com.exe.skillverse_backend.question_bank_service.service.impl;

import com.exe.skillverse_backend.ai_service.service.ExpertPromptServiceImpl;
import com.exe.skillverse_backend.question_bank_service.dto.request.AiGenerateDraftRequest;
import com.exe.skillverse_backend.question_bank_service.dto.response.AiDraftResponse;
import com.exe.skillverse_backend.question_bank_service.entity.QuestionBank;
import com.exe.skillverse_backend.question_bank_service.repository.QuestionBankRepository;
import com.exe.skillverse_backend.question_bank_service.service.AiDraftGenerationService;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.exe.skillverse_backend.career_taxonomy_service.entity.JobPosition;
import com.exe.skillverse_backend.career_taxonomy_service.repository.JobPositionRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AiDraftGenerationServiceImpl implements AiDraftGenerationService {

    private final QuestionBankRepository questionBankRepository;
    private final ObjectMapper objectMapper;
    private final ChatModel generateTestChatModel;
    private final ExpertPromptServiceImpl expertPromptService;
    private final JobPositionRepository jobPositionRepository;

    @Override
    public AiDraftResponse generateDraftQuestions(Long bankId, AiGenerateDraftRequest request) {
        QuestionBank bank = questionBankRepository.findById(bankId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Question bank not found: " + bankId));

        int questionCount = request.getQuestionCount() != null ? request.getQuestionCount() : 25;
        if (questionCount < 5) {
            questionCount = 5;
        }
        if (questionCount > 50) {
            questionCount = 50;
        }

        String difficultyDistJson = bank.getDifficultyDistribution();
        if (request.getDifficultyDistribution() != null && !request.getDifficultyDistribution().isEmpty()) {
            try {
                difficultyDistJson = objectMapper.writeValueAsString(request.getDifficultyDistribution());
            } catch (Exception e) {
                log.warn("Failed to serialize difficulty distribution from request: {}", e.getMessage());
            }
        }

        String prompt = buildBankGenerationPrompt(bank, questionCount, difficultyDistJson, request);

        String aiResponse;
        try {
            aiResponse = ChatClient.create(generateTestChatModel)
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("AI draft generation failed for bank {}: {}", bankId, e.getMessage(), e);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "AI generation failed: " + e.getMessage());
        }

        List<AiDraftResponse.QuestionDraft> drafts = parseDraftQuestions(aiResponse);

        log.info("Generated {} draft questions for bank {}", drafts.size(), bankId);
        return AiDraftResponse.builder()
                .drafts(drafts)
                .totalGenerated(drafts.size())
                .build();
    }

    private String buildBankGenerationPrompt(QuestionBank bank, int questionCount,
            String difficultyDistJson, AiGenerateDraftRequest request) {
        StringBuilder prompt = new StringBuilder();
        String jobPositionName = "";
        if (bank.getJobPositionId() != null) {
            jobPositionName = jobPositionRepository.findById(bank.getJobPositionId())
                    .map(JobPosition::getName)
                    .orElse("");
        }

        String expertSystemPrompt = expertPromptService.getSystemPrompt(
                bank.getDomain(),
                null,
                jobPositionName);

        prompt.append("Bạn là chuyên gia thiết kế bài đánh giá đầu vào cho SkillVerse.\n");
        prompt.append("Mục tiêu là tạo bộ câu hỏi sàng lọc đầu vào đúng chuẩn nghề nghiệp, đúng bối cảnh công việc và đúng quy tắc của ngành.\n\n");

        if (expertSystemPrompt != null && !expertSystemPrompt.isBlank()) {
            prompt.append("=== HỆ QUY CHIẾU CHUYÊN GIA BẮT BUỘC PHẢI TUÂN THỦ ===\n");
            prompt.append(expertSystemPrompt.trim()).append("\n");
            prompt.append("=== KẾT THÚC HỆ QUY CHIẾU CHUYÊN GIA ===\n\n");
        }

        prompt.append("Thông tin ngân hàng câu hỏi:\n");
        prompt.append("- Lĩnh vực: ").append(safe(bank.getDomain())).append("\n");
        prompt.append("- Vị trí công việc (Job Position): ").append(safe(jobPositionName)).append("\n");
        prompt.append("- Tên bank: ").append(safe(bank.getTitle())).append("\n");
        if (bank.getDescription() != null && !bank.getDescription().isBlank()) {
            prompt.append("- Mô tả bank: ").append(bank.getDescription()).append("\n");
        }
        prompt.append("- Số lượng câu hỏi cần tạo: ").append(questionCount).append("\n");
        if (request.getFocusSkillAreas() != null && !request.getFocusSkillAreas().isEmpty()) {
            prompt.append("- Kỹ năng cần ưu tiên: ")
                    .append(String.join(", ", request.getFocusSkillAreas()))
                    .append("\n");
        }
        prompt.append("\n");

        Map<String, Double> distribution = parseDistribution(difficultyDistJson);
        int beginnerCount = (int) Math.round(questionCount * distribution.getOrDefault("BEGINNER", 0.20));
        int intermediateCount = (int) Math.round(questionCount * distribution.getOrDefault("INTERMEDIATE", 0.35));
        int advancedCount = (int) Math.round(questionCount * distribution.getOrDefault("ADVANCED", 0.30));
        int expertCount = questionCount - beginnerCount - intermediateCount - advancedCount;

        prompt.append("Phân bổ độ khó mong muốn:\n");
        prompt.append("- BEGINNER: ").append(beginnerCount).append(" câu\n");
        prompt.append("- INTERMEDIATE: ").append(intermediateCount).append(" câu\n");
        prompt.append("- ADVANCED: ").append(advancedCount).append(" câu\n");
        prompt.append("- EXPERT: ").append(expertCount).append(" câu\n\n");

        prompt.append("Yêu cầu bắt buộc:\n");
        prompt.append("1. Phải tuân thủ đúng rule ngành, domain rules, role prompt và ngữ cảnh nghề nghiệp ở phần hệ quy chiếu chuyên gia.\n");
        prompt.append("2. Tạo đúng ").append(questionCount).append(" câu hỏi trắc nghiệm 4 đáp án.\n");
        prompt.append("3. Mỗi câu hỏi phải có các field sau:\n");
        prompt.append("   - question: Nội dung câu hỏi bằng tiếng Việt.\n");
        prompt.append("   - options: Mảng 4 đáp án theo format \"A. ...\", \"B. ...\", \"C. ...\", \"D. ...\".\n");
        prompt.append("   - correctAnswer: Chỉ nhận A, B, C hoặc D.\n");
        prompt.append("   - explanation: Giải thích vì sao đáp án đúng và vì sao các đáp án còn lại chưa đúng.\n");
        prompt.append("   - difficulty: BEGINNER / INTERMEDIATE / ADVANCED / EXPERT.\n");
        prompt.append("   - skillArea: Kỹ năng hoặc năng lực đang được đánh giá.\n");
        prompt.append("   - category: KNOWLEDGE / SKILL / SITUATION / ANALYSIS.\n");
        prompt.append("4. Câu hỏi phải bám sát lĩnh vực và vị trí công việc đã chọn, không lệch chủ đề.\n");
        prompt.append("5. Câu hỏi phải có tính thực tế, đủ chất lượng để dùng cho bài quiz đầu vào.\n");
        prompt.append("6. Nếu có kỹ năng ưu tiên thì tăng tỷ trọng câu hỏi liên quan nhưng vẫn giữ đủ độ phủ nền tảng.\n");
        prompt.append("7. Đáp án đúng phải được phân bổ cân bằng giữa A/B/C/D.\n");
        prompt.append("8. Không trả lời ngoài JSON, không dùng markdown bao quanh JSON.\n\n");

        prompt.append("Chỉ trả lời bằng JSON hợp lệ theo đúng format sau:\n");
        prompt.append("""
            {
              "questions": [
                {
                  "question": "...",
                  "options": ["A. ...", "B. ...", "C. ...", "D. ..."],
                  "correctAnswer": "A",
                  "explanation": "...",
                  "difficulty": "BEGINNER",
                  "skillArea": "...",
                  "category": "KNOWLEDGE"
                }
              ]
            }
            """);

        return prompt.toString();
    }

    @SuppressWarnings("unchecked")
    private List<AiDraftResponse.QuestionDraft> parseDraftQuestions(String aiResponse) {
        String jsonStr = extractJsonFromResponse(aiResponse);

        try {
            Map<String, Object> parsed = objectMapper.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});
            List<?> questions = (List<?>) parsed.get("questions");

            List<AiDraftResponse.QuestionDraft> drafts = new ArrayList<>();
            int draftId = 1;

            if (questions != null) {
                for (Object q : questions) {
                    if (!(q instanceof Map)) {
                        continue;
                    }
                    Map<String, Object> question = (Map<String, Object>) q;

                    String questionText = (String) question.get("question");
                    String correctAnswer = (String) question.get("correctAnswer");
                    String difficulty = (String) question.get("difficulty");
                    String skillArea = (String) question.get("skillArea");
                    String category = (String) question.getOrDefault("category", "KNOWLEDGE");
                    String explanation = (String) question.get("explanation");

                    List<String> options = new ArrayList<>();
                    Object optionsObj = question.get("options");
                    if (optionsObj instanceof List) {
                        List<?> opts = (List<?>) optionsObj;
                        for (int i = 0; i < opts.size(); i++) {
                            String val = opts.get(i) != null ? opts.get(i).toString().trim() : "";
                            String prefix = (char) ('A' + i) + ". ";
                            if (!val.startsWith(prefix)) {
                                val = prefix + val;
                            }
                            options.add(val);
                        }
                    }

                    String normalizedAnswer = correctAnswer != null ? correctAnswer.trim().toUpperCase() : "A";
                    if (normalizedAnswer.length() > 1) {
                        normalizedAnswer = normalizedAnswer.substring(0, 1);
                    }
                    if (!normalizedAnswer.matches("[A-D]")) {
                        normalizedAnswer = "A";
                    }

                    drafts.add(AiDraftResponse.QuestionDraft.builder()
                            .draftId(draftId++)
                            .questionText(questionText)
                            .options(options.size() == 4 ? options : null)
                            .correctAnswer(normalizedAnswer)
                            .explanation(explanation)
                            .difficulty(difficulty != null ? difficulty.toUpperCase() : "INTERMEDIATE")
                            .skillArea(skillArea)
                            .category(category != null ? category.toUpperCase() : "KNOWLEDGE")
                            .edited(false)
                            .build());
                }
            }

            return drafts;
        } catch (Exception e) {
            log.error("Failed to parse AI draft response: {}", e.getMessage());
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to parse AI response: " + e.getMessage());
        }
    }

    private String extractJsonFromResponse(String response) {
        String jsonStr = response.trim();
        if (jsonStr.startsWith("```json")) {
            jsonStr = jsonStr.substring(7);
        } else if (jsonStr.startsWith("```")) {
            jsonStr = jsonStr.substring(3);
        }
        if (jsonStr.endsWith("```")) {
            jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
        }
        return jsonStr.trim();
    }

    private Map<String, Double> parseDistribution(String json) {
        if (json == null || json.isBlank()) {
            return Map.of("BEGINNER", 0.20, "INTERMEDIATE", 0.35, "ADVANCED", 0.30, "EXPERT", 0.15);
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Double>>() {});
        } catch (Exception e) {
            return Map.of("BEGINNER", 0.20, "INTERMEDIATE", 0.35, "ADVANCED", 0.30, "EXPERT", 0.15);
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "Không có" : value;
    }
}
