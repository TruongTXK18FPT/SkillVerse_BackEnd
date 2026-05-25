package com.exe.skillverse_backend.journey_service.node_mentoring.dto.response;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeAssignment;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeAssignment.AssignmentSource;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.GradingCriterionDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodeAssignmentResponse {

    private static final ObjectMapper mapper = new ObjectMapper();

    private Long id;
    private Long journeyId;
    private Long roadmapSessionId;
    private String nodeId;
    private Long nodeSkillId;
    private AssignmentSource assignmentSource;
    private String title;
    private String description;
    private String expectedOutput;
    private String rubric;
    private Long createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private List<GradingCriterionDto> criteria;
    private String verificationStatus;

    public static NodeAssignmentResponse from(RoadmapNodeAssignment a) {
        List<GradingCriterionDto> criteriaList = null;
        if (a.getCriteriaJson() != null && !a.getCriteriaJson().isBlank()) {
            try {
                criteriaList = Arrays.asList(mapper.readValue(a.getCriteriaJson(), GradingCriterionDto[].class));
            } catch (Exception ex) {
                // Ignore parse errors
            }
        }

        return NodeAssignmentResponse.builder()
                .id(a.getId())
                .journeyId(a.getJourneyId())
                .roadmapSessionId(a.getRoadmapSessionId())
                .nodeId(a.getNodeId())
                .nodeSkillId(a.getNodeSkillId())
                .assignmentSource(a.getAssignmentSource())
                .title(a.getTitle())
                .description(a.getDescription())
                .expectedOutput(a.getExpectedOutput())
                .rubric(a.getRubric())
                .createdBy(a.getCreatedBy())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .criteria(criteriaList)
                .verificationStatus(a.getVerificationStatus())
                .build();
    }
}
