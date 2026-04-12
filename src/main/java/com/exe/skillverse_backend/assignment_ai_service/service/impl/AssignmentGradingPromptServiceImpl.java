package com.exe.skillverse_backend.assignment_ai_service.service.impl;

import com.exe.skillverse_backend.assignment_ai_service.service.AssignmentGradingPromptService;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.AssignmentCriteria;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AssignmentGradingPromptServiceImpl implements AssignmentGradingPromptService {

    private static final String USER_PROMPT_TEMPLATE = """
        ## Thông tin bài tập
        Tên: %s
        Mô tả: %s
        Mục tiêu học tập: %s

        ## Rubric (Tiêu chí chấm điểm)
        %s

        ## Nội dung bài nộp của học viên
        %s

        ## Yêu cầu chấm điểm
        Hãy đánh giá bài nộp theo từng tiêu chí trong rubric.
        Với mỗi tiêu chí:
        - Chấm điểm từ 0 đến maxPoints
        - Viết feedback ngắn gọn bằng tiếng Việt (2-3 câu)
        - Đánh giá mức độ tự tin của bạn (0.0 - 1.0)

        Grading style: %s

        %s

        ## Output format (JSON only)
        Trả lời CHỈ bằng JSON, không có markdown, không có giải thích thêm:
        {
          "criteriaScores": [
            {
              "criteriaId": <number>,
              "criteriaName": "<name>",
              "score": <number>,
              "maxPoints": <number>,
              "passingPoints": <number>,
              "passed": <boolean>,
              "feedback": "<tiếng Việt, 2-3 câu>",
              "confidence": <0.0-1.0>
            }
          ],
          "totalScore": <number>,
          "overallFeedback": "<tiếng Việt, tổng quan 3-5 câu>",
          "overallConfidence": <0.0-1.0>
        }
        """;

    @Override
    public String buildGradingPrompt(Assignment assignment, String submissionTextExtracted,
            String gradingStyle, String customPrompt) {
        String criteriaBlock = buildCriteriaBlock(assignment.getCriteria());
        String gradingInstruction = getGradingInstruction(gradingStyle);
        String customPromptSection = (customPrompt != null && !customPrompt.isBlank())
                ? "## Hướng dẫn thêm từ mentor\n" + customPrompt + "\n"
                : "";

        return String.format(USER_PROMPT_TEMPLATE,
                assignment.getTitle(),
                assignment.getDescription() != null ? assignment.getDescription() : "",
                assignment.getLearningOutcome() != null ? assignment.getLearningOutcome() : "",
                criteriaBlock,
                submissionTextExtracted,
                gradingStyle,
                customPromptSection + gradingInstruction
        );
    }

    private String buildCriteriaBlock(List<AssignmentCriteria> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            return "(Không có tiêu chí chấm điểm — chấm theo tổng điểm)";
        }
        StringBuilder sb = new StringBuilder();
        for (AssignmentCriteria c : criteria) {
            sb.append(String.format("- [%s] %s (id: %d, max: %s, passing: %s)%n",
                    c.isRequired() ? "BẮT BUỘC" : "TÙY CHỌN",
                    c.getName(),
                    c.getId(),
                    c.getMaxPoints(),
                    c.getPassingPoints()));
            if (c.getDescription() != null && !c.getDescription().isBlank()) {
                sb.append("  Mô tả: ").append(c.getDescription()).append("\n");
            }
        }
        return sb.toString();
    }

    private String getGradingInstruction(String style) {
        if (style == null) {
            style = "STANDARD";
        }
        return switch (style) {
            case "STRICT" -> "Áp dụng tiêu chuẩn NGHIÊM NGẶT. Điểm cao chỉ khi bài làm thực sự xuất sắc theo từng tiêu chí.";
            case "LENIENT" -> "Áp dụng tiêu chuẩn LINH HOẠT. Cộng điểm cho nỗ lực và cải thiện, không chỉ kết quả hoàn hảo.";
            default -> "Áp dụng tiêu chuẩn CÂN BẰNG. Đánh giá công bằng dựa trên yêu cầu thực tế của từng tiêu chí.";
        };
    }
}
