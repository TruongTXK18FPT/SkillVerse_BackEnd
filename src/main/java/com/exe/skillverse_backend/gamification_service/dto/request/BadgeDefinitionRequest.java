package com.exe.skillverse_backend.gamification_service.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin request DTO for creating or updating badge definitions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BadgeDefinitionRequest {

    @NotBlank(message = "Badge key is required")
    private String badgeKey;

    @NotBlank(message = "Badge title is required")
    private String badgeTitle;

    private String badgeDescription;

    private String badgeIcon;

    @NotBlank(message = "Badge category is required")
    private String badgeCategory; // learning, community, events, coins

    @NotBlank(message = "Badge rarity is required")
    private String badgeRarity; // common, rare, epic, legendary

    private String criteriaDescription;

    private String criteriaConfig; // JSON

    @NotNull(message = "Coin reward is required")
    @Min(value = 0, message = "Coin reward must be non-negative")
    private Integer coinReward;

    @NotNull(message = "XP reward is required")
    @Min(value = 0, message = "XP reward must be non-negative")
    private Integer xpReward;

    private Boolean isActive = true;

    private Integer displayOrder;
}
