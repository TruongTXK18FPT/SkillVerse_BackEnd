package com.exe.skillverse_backend.roadmap_package_service.entity;

import com.exe.skillverse_backend.journey_service.entity.Journey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "roadmap_template_activities", indexes = {
        @Index(columnList = "skill_block_id"),
        @Index(columnList = "template_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapTemplateActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private RoadmapTemplate template;

    @Column(name = "template_id", insertable = false, updatable = false)
    private Long templateId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_block_id", nullable = false)
    private RoadmapTemplateSkillBlock skillBlock;

    @Column(name = "skill_block_id", insertable = false, updatable = false)
    private Long skillBlockId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "exercise_type", length = 80)
    private String exerciseType;

    @Column(name = "expected_output", columnDefinition = "TEXT")
    private String expectedOutput;

    @Column(columnDefinition = "TEXT")
    private String rubric;

    @Column(length = 30)
    private String difficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "min_level", length = 20)
    private Journey.SkillLevel minLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "max_level", length = 20)
    private Journey.SkillLevel maxLevel;

    @Column(name = "estimated_hours")
    private Double estimatedHours;

    @Column(name = "prerequisite_hint", columnDefinition = "TEXT")
    private String prerequisiteHint;

    @Column(name = "ai_prompt_hint", columnDefinition = "TEXT")
    private String aiPromptHint;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
