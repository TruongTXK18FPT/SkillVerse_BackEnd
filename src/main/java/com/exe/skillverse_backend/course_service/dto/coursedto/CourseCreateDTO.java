package com.exe.skillverse_backend.course_service.dto.coursedto;

import jakarta.validation.constraints.NotBlank;
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
    private Long thumbnailMediaId; // Back to thumbnailMediaId
    private java.math.BigDecimal price;
    private String currency;
}
