package com.exe.skillverse_backend.course_service.dto.quizdto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizAttemptSessionDTO {
    private Long quizId;
    private Long userId;
    private String sessionToken;
    private String status;
    private Instant startedAt;
    private Instant lastSeenAt;
    private Instant expiresAt;
}
