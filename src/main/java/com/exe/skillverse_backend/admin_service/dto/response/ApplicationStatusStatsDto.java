package com.exe.skillverse_backend.admin_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Application statistics by status")
public class ApplicationStatusStatsDto {
    @Schema(description = "Number of pending applications")
    private Integer pending;

    @Schema(description = "Number of approved applications")
    private Integer approved;

    @Schema(description = "Number of rejected applications")
    private Integer rejected;

    @Schema(description = "Total applications")
    private Integer total;
}