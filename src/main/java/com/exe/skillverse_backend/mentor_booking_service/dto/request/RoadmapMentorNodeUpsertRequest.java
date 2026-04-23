package com.exe.skillverse_backend.mentor_booking_service.dto.request;

import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapMentorNodeUpsertRequest {
    private String nodeId;
    private String parentId;
    private String title;
    private String description;
    private Integer estimatedTimeMinutes;
    private RoadmapResponse.RoadmapNode.NodeType type;
    private String difficulty;
    private Boolean isCore;
    private List<String> learningObjectives;
    private List<String> keyConcepts;
    private List<String> practicalExercises;
    private List<String> suggestedResources;
    private List<String> successCriteria;
    private List<String> prerequisites;
    private List<String> suggestedCourseIds;
    private List<String> suggestedModuleIds;
}
