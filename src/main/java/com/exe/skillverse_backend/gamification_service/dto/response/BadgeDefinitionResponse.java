package com.exe.skillverse_backend.gamification_service.dto.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for badge definitions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BadgeDefinitionResponse {

    private Long badgeDefId;
    private String badgeKey;
    private String badgeTitle;
    private String badgeDescription;
    private String badgeIcon;
    private String badgeCategory;
    private String badgeRarity;
    private String criteriaDescription;
    private Integer coinReward;
    private Integer xpReward;
    private Boolean isActive;
    private Integer displayOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
