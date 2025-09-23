package com.exe.skillverse_backend.portfolio_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.shared.entity.Media;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "projects")
@NamedQueries({
        @NamedQuery(name = "Project.findByQuery", query = "SELECT p FROM Project p WHERE p.userId = :userId " +
                "AND (:search IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "     OR LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "     OR LOWER(p.techStack) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                "AND (:techStack IS NULL OR LOWER(p.techStack) LIKE LOWER(CONCAT('%', :techStack, '%'))) " +
                "AND (:startDate IS NULL OR p.completedDate >= :startDate) " +
                "AND (:endDate IS NULL OR p.completedDate <= :endDate) " +
                "ORDER BY " +
                "CASE WHEN :sortBy = 'title' AND :sortDirection = 'ASC' THEN p.title END ASC, " +
                "CASE WHEN :sortBy = 'title' AND :sortDirection = 'DESC' THEN p.title END DESC, " +
                "CASE WHEN :sortBy = 'completedDate' AND :sortDirection = 'ASC' THEN p.completedDate END ASC, " +
                "CASE WHEN :sortBy = 'completedDate' AND :sortDirection = 'DESC' THEN p.completedDate END DESC, " +
                "CASE WHEN (:sortBy = 'createdAt' OR :sortBy IS NULL) AND :sortDirection = 'ASC' THEN p.createdAt END ASC, "
                +
                "CASE WHEN (:sortBy = 'createdAt' OR :sortBy IS NULL) AND (:sortDirection = 'DESC' OR :sortDirection IS NULL) THEN p.createdAt END DESC"),
        @NamedQuery(name = "Project.countByQuery", query = "SELECT COUNT(p) FROM Project p WHERE p.userId = :userId " +
                "AND (:search IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "     OR LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "     OR LOWER(p.techStack) LIKE LOWER(CONCAT('%', :techStack, '%'))) " +
                "AND (:techStack IS NULL OR LOWER(p.techStack) LIKE LOWER(CONCAT('%', :techStack, '%'))) " +
                "AND (:startDate IS NULL OR p.completedDate >= :startDate) " +
                "AND (:endDate IS NULL OR p.completedDate <= :endDate)")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "tech_stack")
    private String techStack;

    @Column(name = "project_url")
    private String projectUrl;

    @Column(name = "media_id")
    private Long mediaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id", insertable = false, updatable = false)
    private Media media;

    @Column(name = "completed_date")
    private LocalDate completedDate;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
