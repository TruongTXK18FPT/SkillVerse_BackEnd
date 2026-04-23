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
public class RoadmapMentorOverviewUpdateRequest {
    private String purpose;
    private String audience;
    private String postRoadmapState;
    private List<RoadmapResponse.StructurePhase> structure;
    private List<String> thinkingProgression;
    private RoadmapResponse.NextSteps nextSteps;
}
