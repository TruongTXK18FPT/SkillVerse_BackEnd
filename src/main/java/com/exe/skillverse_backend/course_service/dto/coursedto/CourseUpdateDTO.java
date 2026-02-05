package com.exe.skillverse_backend.course_service.dto.coursedto;

import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;

import jakarta.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseUpdateDTO {
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
    private CourseStatus status;
    private BigDecimal price;
    private String currency;
}
