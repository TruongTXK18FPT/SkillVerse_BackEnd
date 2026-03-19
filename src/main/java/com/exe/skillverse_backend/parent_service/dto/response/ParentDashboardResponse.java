package com.exe.skillverse_backend.parent_service.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParentDashboardResponse {
    private List<StudentOverviewDTO> students;
    private int totalStudents;
    // Add wallet info here later if needed
}
