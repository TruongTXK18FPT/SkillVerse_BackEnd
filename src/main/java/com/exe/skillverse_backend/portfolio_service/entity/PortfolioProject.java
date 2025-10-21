package com.exe.skillverse_backend.portfolio_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "portfolio_projects")
public class PortfolioProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "client_name", length = 255)
    private String clientName;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false)
    private ProjectType projectType; // MICRO_JOB, FREELANCE, PERSONAL, ACADEMIC, etc.

    @Column(name = "duration", length = 100)
    private String duration; // e.g., "3 weeks", "2 months"

    @Column(name = "completion_date")
    private LocalDate completionDate;

    @ElementCollection
    @CollectionTable(name = "project_tools", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "tool")
    @Builder.Default
    private List<String> tools = new ArrayList<>(); // Technologies/tools used

    @ElementCollection
    @CollectionTable(name = "project_outcomes", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "outcome", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> outcomes = new ArrayList<>(); // Project results/achievements

    @Column(name = "rating")
    private Integer rating; // 1-5 stars from client

    @Column(name = "client_feedback", columnDefinition = "TEXT")
    private String clientFeedback;

    @Column(name = "project_url", length = 1000)
    private String projectUrl; // Live demo URL

    @Column(name = "github_url", length = 1000)
    private String githubUrl; // Source code URL

    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl; // Main project image

    @Column(name = "thumbnail_public_id", length = 500)
    private String thumbnailPublicId;

    @ElementCollection
    @CollectionTable(name = "project_attachments", joinColumns = @JoinColumn(name = "project_id"))
    @Builder.Default
    private List<ProjectAttachment> attachments = new ArrayList<>();

    @Column(name = "is_featured", nullable = false)
    @Builder.Default
    private Boolean isFeatured = false; // Highlight this project

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum ProjectType {
        MICRO_JOB,
        FREELANCE,
        PERSONAL,
        ACADEMIC,
        OPEN_SOURCE,
        INTERNSHIP,
        FULL_TIME
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectAttachment {
        @Column(name = "file_name", length = 500)
        private String fileName;

        @Column(name = "file_url", length = 1000)
        private String fileUrl;

        @Column(name = "file_public_id", length = 500)
        private String filePublicId;

        @Column(name = "file_type", length = 50)
        private String fileType; // pdf, image, video, etc.
    }
}
