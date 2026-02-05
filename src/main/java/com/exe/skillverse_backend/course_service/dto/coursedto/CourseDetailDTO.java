package com.exe.skillverse_backend.course_service.dto.coursedto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.exe.skillverse_backend.auth_service.dto.response.UserDto;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleSummaryDTO;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.shared.dto.MediaDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseDetailDTO {
    //Long id, String title, String description, String level, CourseStatus status, UserDto author, MediaDto thumbnail, List<ModuleSummaryDTO> modules
    private Long id;
    private String title;
    private String description;
    private String shortDescription;
    private String level;
    private String category;
    private Integer estimatedDurationHours;
    private String language;
    private List<String> learningObjectives;
    private List<String> requirements;
    private CourseStatus status;
    private UserDto author;
    private MediaDTO thumbnail;
    private List<ModuleSummaryDTO> modules;
    private BigDecimal price;
    private String currency;
    private String authorName;
    private String thumbnailUrl;
    private Integer enrollmentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime submittedDate;
    private LocalDateTime publishedDate;
    
}
