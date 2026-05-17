package com.exe.skillverse_backend.question_bank_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "question_banks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionBank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String domain;

    @Column(name = "domain_id")
    private Long domainId;

    @Column(name = "job_position_id")
    private Long jobPositionId;

    /**
     * [V3] Skill cụ thể mà question bank phục vụ (e.g., "REACT", "JAVA_SPRING_BOOT").
     * Cho phép lookup question bank theo skill thay vì chỉ domain+jobRole.
     */
    @Column(name = "skill_name", length = 100)
    private String skillName;

    @Column(name = "skill_id")
    private Long skillId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "difficulty_distribution", columnDefinition = "TEXT")
    @Builder.Default
    private String difficultyDistribution = "{\"BEGINNER\":0.20,\"INTERMEDIATE\":0.35,\"ADVANCED\":0.30,\"EXPERT\":0.15}";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "questionBank", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<QuestionBankQuestion> questions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public int getActiveQuestionCount() {
        return (int) questions.stream().filter(q -> q.getIsActive()).count();
    }
}
