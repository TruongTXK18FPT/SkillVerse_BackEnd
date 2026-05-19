package com.exe.skillverse_backend.roadmap_package_service.entity;

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
@Table(name = "roadmap_template_node_group_skills", indexes = {
        @Index(columnList = "node_group_id"),
        @Index(columnList = "skill_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapTemplateNodeGroupSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "node_group_id", nullable = false)
    private RoadmapTemplateNodeGroup nodeGroup;

    @Column(name = "node_group_id", insertable = false, updatable = false)
    private Long nodeGroupId;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "skill_name_snapshot", length = 255)
    private String skillNameSnapshot;

    @Column(name = "skill_canonical_key_snapshot", length = 255)
    private String skillCanonicalKeySnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "requirement_type", nullable = false, length = 30)
    private RequirementType requirementType;

    @Column(name = "weight_in_node")
    private Double weightInNode;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
