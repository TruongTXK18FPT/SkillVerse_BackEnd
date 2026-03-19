package com.exe.skillverse_backend.course_service.dto.quizdto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizAttemptSessionHeartbeatDTO {
    @NotBlank(message = "Session token is required")
    private String sessionToken;
}
