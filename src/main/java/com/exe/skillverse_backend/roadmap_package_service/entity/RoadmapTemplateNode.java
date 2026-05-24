package com.exe.skillverse_backend.roadmap_package_service.entity;

import com.exe.skillverse_backend.career_taxonomy_service.enums.ImportanceLevel;
import com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType;
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
@Table(name = "roadmap_template_nodes", indexes = {
        @Index(columnList = "template_id"),
        @Index(columnList = "skill_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapTemplateNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private RoadmapTemplate template;

    @Column(name = "template_id", insertable = false, updatable = false)
    private Long templateId;

    @Column(name = "parent_node_id")
    private Long parentNodeId;

    @Column(name = "node_key", length = 100)
    private String nodeKey;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(name = "skill_id")
    private Long skillId;

    @Column(name = "skill_name_snapshot", length = 255)
    private String skillNameSnapshot;

    @Column(name = "skill_canonical_key_snapshot", length = 255)
    private String skillCanonicalKeySnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "requirement_type", length = 30)
    private RequirementType requirementType;

    @Enumerated(EnumType.STRING)
    @Column(name = "importance_level", length = 30)
    private ImportanceLevel importanceLevel;

    @Column(length = 30)
    private String difficulty;

    @Column(name = "estimated_hours")
    private Double estimatedHours;

    @Column(name = "expected_output", columnDefinition = "TEXT")
    private String expectedOutput;

    @Column(columnDefinition = "TEXT")
    private String rubric;

    @Column(name = "pinned_document_ids", columnDefinition = "TEXT")
    private String pinnedDocumentIds;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
