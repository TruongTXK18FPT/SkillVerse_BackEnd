package com.exe.skillverse_backend.mentor_matching_service.dto;

import com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType;
import com.exe.skillverse_backend.mentor_matching_service.enums.TeachingEligibilityStatus;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MentorTeachingEligibilityResponse {
    private Long mentorId;
    private Long roadmapSessionId;
    private Long journeyId;
    private TeachingEligibilityStatus summaryStatus;
    private Integer overallMatchPercent;
    private List<NodeEligibility> nodes;

    @Data
    @Builder
    public static class NodeEligibility {
        private String nodeId;
        private String title;
        private TeachingEligibilityStatus status;
        private Integer matchPercent;
        private List<SkillRequirement> matchedSkills;
        private List<SkillRequirement> missingRequiredSkills;
        private List<SkillRequirement> missingImportantSkills;
        private List<SkillRequirement> missingNiceToHaveSkills;
    }

    @Data
    @Builder
    public static class SkillRequirement {
        private Long skillId;
        private String skillName;
        private String canonicalKey;
        private RequirementType requirementType;
    }
}
