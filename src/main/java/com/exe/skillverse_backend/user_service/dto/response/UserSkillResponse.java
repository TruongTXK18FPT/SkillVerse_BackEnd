package com.exe.skillverse_backend.user_service.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserSkillResponse {

    private Long userId;
    private Long skillId;
    private String skillName;
    private String skillCategory;
    private String skillDescription;
    private Integer proficiency; // 1-5 scale
    private String proficiencyLabel; // Beginner, Novice, Intermediate, Advanced, Expert
}