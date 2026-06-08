package com.exe.skillverse_backend.journey_service.node_mentoring.dto.request;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeAssignment.AssignmentSource;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.GradingCriterionDto;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Request to create or update the current assignment snapshot for a node.
 * Used by mentor_refined flow. SYSTEM_GENERATED snapshots are created implicitly
 * the first time a learner submits evidence.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpsertNodeAssignmentRequest {

    @Size(max = 255)
    private String title;

    private String description;

    private Long nodeSkillId;

    /** Defaults to MENTOR_REFINED when invoked by a mentor. */
    private AssignmentSource assignmentSource;

    private List<GradingCriterionDto> criteria;

    private String expectedOutput;

    private String rubric;
}
