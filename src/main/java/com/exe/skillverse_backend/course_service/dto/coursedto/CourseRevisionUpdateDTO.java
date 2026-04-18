package com.exe.skillverse_backend.course_service.dto.coursedto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseRevisionUpdateDTO {
    private String title;
    private String description;
    private String level;
    private String category;
    private String shortDescription;
    private Integer estimatedDurationHours;
    private String language;
    private List<String> learningObjectives;
    private List<String> requirements;
    private List<String> courseSkills;
    private BigDecimal price;
    private String currency;
    private String contentSnapshotJson;
    /** Thumbnail media ID — upload via multipart on the controller, set ID here. */
    private Long thumbnailMediaId;
    /** Inline base64 thumbnail for small image updates (optional). */
    private String thumbnailBase64;
}
