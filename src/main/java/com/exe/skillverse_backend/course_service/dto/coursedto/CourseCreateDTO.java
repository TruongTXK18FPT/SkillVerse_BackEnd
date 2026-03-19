package com.exe.skillverse_backend.course_service.dto.coursedto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseCreateDTO {
    @NotBlank
    private String title;
    private String description;
    private String level;
    private String category;
    private String shortDescription;
    private Integer estimatedDurationHours;
    private String language;
    private List<String> learningObjectives;
    private List<String> requirements;
    private Long thumbnailMediaId; // Back to thumbnailMediaId
    private BigDecimal price;
    private String currency;
}
