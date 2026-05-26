package com.exe.skillverse_backend.roadmap_package_service.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "roadmap_template_node_groups", indexes = {
        @Index(columnList = "template_id"),
        @Index(columnList = "template_id, order_index")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapTemplateNodeGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private RoadmapTemplate template;

    @Column(name = "template_id", insertable = false, updatable = false)
    private Long templateId;

    @Column(name = "node_key", length = 120)
    private String nodeKey;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "learning_objectives", columnDefinition = "TEXT")
    private String learningObjectives;

    @Column(name = "lessons_json", columnDefinition = "TEXT")
    private String lessonsJson;

    @Column(name = "exercises_json", columnDefinition = "TEXT")
    private String exercisesJson;

    @Column(name = "completion_criteria", columnDefinition = "TEXT")
    private String completionCriteria;

    @Column(name = "expected_output", columnDefinition = "TEXT")
    private String expectedOutput;

    @Column(columnDefinition = "TEXT")
    private String rubric;

    @Column(length = 30)
    private String difficulty;

    @Column(name = "estimated_hours")
    private Double estimatedHours;

    @Column(name = "ai_prompt_hint", columnDefinition = "TEXT")
    private String aiPromptHint;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Builder.Default
    @OneToMany(mappedBy = "nodeGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<RoadmapTemplateNodeGroupSkill> skills = new ArrayList<>();

    @Column(name = "pinned_document_ids", columnDefinition = "TEXT")
    private String pinnedDocumentIds;

    @Column(name = "node_type", length = 30)
    private String nodeType;

    @Column(name = "parent_node_key", length = 120)
    private String parentNodeKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
