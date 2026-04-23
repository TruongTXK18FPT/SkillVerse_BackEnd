package com.exe.skillverse_backend.mentor_booking_service.dto.request;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapMentorNodeReorderRequest {
    private String parentId;
    private List<String> orderedNodeIds;
}
