package com.exe.skillverse_backend.gamification_service.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for starting a mini-game session
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartGameSessionRequest {

    @NotNull(message = "Game key is required")
    private String gameKey;

    private String sessionMetadata; // Optional JSON metadata
}
