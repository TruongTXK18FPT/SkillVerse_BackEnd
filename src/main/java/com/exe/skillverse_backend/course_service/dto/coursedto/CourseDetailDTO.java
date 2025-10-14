package com.exe.skillverse_backend.course_service.dto.coursedto;
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
    private String level;
    private CourseStatus status;
    private UserDto author;
    private MediaDTO thumbnail;
    private List<ModuleSummaryDTO> modules;
    private java.math.BigDecimal price;
    private String currency;
    private String authorName;
    private String thumbnailUrl;
    private Integer enrollmentCount;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
    private java.time.LocalDateTime submittedDate;
    private java.time.LocalDateTime publishedDate;
    
}
