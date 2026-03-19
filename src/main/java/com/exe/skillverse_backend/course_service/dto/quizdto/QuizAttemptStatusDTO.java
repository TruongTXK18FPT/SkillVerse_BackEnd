package com.exe.skillverse_backend.course_service.dto.quizdto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO chứa thông tin trạng thái attempt của user cho một quiz.
 * Bao gồm thông tin về số lượt đã dùng, thời gian chờ để làm lại, điểm cao nhất.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizAttemptStatusDTO {
    
    private Long quizId;
    private Long userId;
    
    /** Số lượt đã sử dụng trong 24h qua */
    private int attemptsUsed;
    
    /** Số lượt tối đa trong 24h (mặc định 3) */
    private int maxAttempts;
    
    /** Còn có thể làm lại không */
    private boolean canRetry;
    
    /** Đã pass quiz chưa */
    private boolean hasPassed;
    
    /** Điểm cao nhất từ tất cả các lần làm */
    private Integer bestScore;
    
    /** Số giây còn lại cho đến khi có thể làm lại (0 nếu có thể làm ngay) */
    private long secondsUntilRetry;
    
    /** Thời điểm có thể làm lại (null nếu có thể làm ngay) */
    private Instant nextRetryAt;
    
    /** Danh sách các lần làm gần đây (trong 24h) */
    private List<QuizAttemptDTO> recentAttempts;
}
