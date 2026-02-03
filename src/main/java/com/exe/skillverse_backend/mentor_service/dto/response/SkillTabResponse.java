package com.exe.skillverse_backend.mentor_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillTabResponse {
    private int skillPoints;
    private int currentLevel;
    private String levelTitle;
    private int nextLevelPoints;
    private int sessionsCompleted;
    private int fiveStarCount;
    private int totalReviews;
    private int courseSales;
    private BigDecimal revenueVnd;
    private List<BadgeInfo> badges;
}
