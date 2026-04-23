package com.exe.skillverse_backend.mentor_booking_service.dto.response;

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
public class RoadmapMentorWorkspaceResponse {
    private BookingResponse booking;
    private Long journeyId;
    private Long roadmapSessionId;
    private RoadmapResponse roadmap;
    private List<RoadmapFollowUpMeetingDTO> followUpMeetings;
}
