package com.exe.skillverse_backend.business_service.dto.request;

import com.exe.skillverse_backend.business_service.entity.enums.RecruitmentJobContextType;
import com.exe.skillverse_backend.business_service.entity.enums.RecruitmentSessionSource;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO để tạo mới một recruitment session
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRecruitmentSessionRequest {

    @NotNull(message = "Candidate ID is required")
    private Long candidateId;

    /**
     * Job posting ID (optional - có thể chat không có job cụ thể)
     */
    private Long jobId;

    private RecruitmentJobContextType jobContextType;

    /**
     * Nguồn tạo session
     */
    @Builder.Default
    private RecruitmentSessionSource sourceType = RecruitmentSessionSource.MANUAL;

    /**
     * Match score từ AI search (nếu có)
     */
    private Integer matchScore;

    /**
     * Skill match percentage từ AI search (nếu có)
     */
    private Integer skillMatchPercent;

    /**
     * Tin nhắn đầu tiên (optional)
     */
    private String initialMessage;
}
