package com.exe.skillverse_backend.journey_service.node_mentoring.dto.response;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeAssignment;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeAssignment.AssignmentSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodeAssignmentResponse {

    private Long id;
    private Long journeyId;
    private Long roadmapSessionId;
    private String nodeId;
    private Long nodeSkillId;
    private AssignmentSource assignmentSource;
    private String title;
    private String description;
    private Long createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public static NodeAssignmentResponse from(RoadmapNodeAssignment a) {
        return NodeAssignmentResponse.builder()
                .id(a.getId())
                .journeyId(a.getJourneyId())
                .roadmapSessionId(a.getRoadmapSessionId())
                .nodeId(a.getNodeId())
                .nodeSkillId(a.getNodeSkillId())
                .assignmentSource(a.getAssignmentSource())
                .title(a.getTitle())
                .description(a.getDescription())
                .createdBy(a.getCreatedBy())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
