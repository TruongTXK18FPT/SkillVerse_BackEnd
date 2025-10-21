package com.exe.skillverse_backend.portfolio_service.dto;

import com.exe.skillverse_backend.portfolio_service.entity.PortfolioProject;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioProjectDTO {
    private Long id;
    private Long userId;
    private String title;
    private String description;
    private String clientName;
    private PortfolioProject.ProjectType projectType;
    private String duration;
    private LocalDate completionDate;
    private List<String> tools;
    private List<String> outcomes;
    private Integer rating;
    private String clientFeedback;
    private String projectUrl;
    private String githubUrl;
    private String thumbnailUrl;
    private List<ProjectAttachmentDTO> attachments;
    private Boolean isFeatured;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectAttachmentDTO {
        private String fileName;
        private String fileUrl;
        private String fileType;
    }
}
