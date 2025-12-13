package com.exe.skillverse_backend.ai_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(name = "ClarificationQuestion", description = "Câu hỏi làm rõ thông tin trước khi generate roadmap")
public class ClarificationQuestion {
    @Schema(description = "Tên trường cần bổ sung", example = "target")
    private String field;
    @Schema(description = "Câu hỏi gửi tới người dùng", example = "Mục tiêu cụ thể của bạn là gì?")
    private String question;
    @Schema(description = "Ví dụ gợi ý để trả lời", example = "[\"ReactJS\", \"Frontend Developer\"]")
    private List<String> examples;
    @Schema(description = "Có bắt buộc trả lời không", example = "true")
    private Boolean required;
}
