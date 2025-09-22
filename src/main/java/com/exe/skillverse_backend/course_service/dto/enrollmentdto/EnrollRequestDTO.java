package com.exe.skillverse_backend.course_service.dto.enrollmentdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrollRequestDTO {
    private Long courseId;
    
    // Explicit getter for clarity
    public Long getCourseId() {
        return courseId;
    }
    
    // Explicit setter for clarity
    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }
}
