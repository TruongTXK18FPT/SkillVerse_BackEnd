package com.exe.skillverse_backend.ai_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating quest/milestone progress in a roadmap
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProgressRequest {

    /**
     * Quest/node ID to update (format: "quest_1", "quest_2", etc.)
     */
    @NotBlank(message = "Quest ID is required")
    private String questId;

    /**
     * Completion status (true = completed, false = not completed)
     */
    @NotNull(message = "Completed status is required")
    private Boolean completed;
}
