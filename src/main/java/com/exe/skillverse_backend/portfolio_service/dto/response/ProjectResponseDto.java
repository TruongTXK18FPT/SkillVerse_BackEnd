package com.exe.skillverse_backend.portfolio_service.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectResponseDto {

    private Long id;
    private Long userId;
    private String title;
    private String description;
    private String techStack;
    private String projectUrl;
    private Long mediaId;
    private String mediaUrl; // For convenience when returning media info
    private LocalDate completedDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}