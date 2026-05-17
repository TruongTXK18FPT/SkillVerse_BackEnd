package com.exe.skillverse_backend.roadmap_package_service.entity;

import jakarta.persistence.CascadeType;
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
@Table(name = "roadmap_template_skill_blocks", indexes = {
        @Index(columnList = "template_id"),
        @Index(columnList = "skill_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapTemplateSkillBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private RoadmapTemplate template;

    @Column(name = "template_id", insertable = false, updatable = false)
    private Long templateId;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "skill_name_snapshot", length = 255)
    private String skillNameSnapshot;

    @Column(name = "skill_canonical_key_snapshot", length = 255)
    private String skillCanonicalKeySnapshot;

    @Column(name = "weight_percent", nullable = false)
    private Double weightPercent;

    @Column(name = "min_nodes")
    private Integer minNodes;

    @Column(name = "max_nodes")
    private Integer maxNodes;

    @Column(name = "node_count_override")
    private Integer nodeCountOverride;

    @Column(name = "learning_goals", columnDefinition = "TEXT")
    private String learningGoals;

    @Column(name = "required_topics", columnDefinition = "TEXT")
    private String requiredTopics;

    @Column(name = "activity_instructions", columnDefinition = "TEXT")
    private String activityInstructions;

    @Column(name = "exercise_types", columnDefinition = "TEXT")
    private String exerciseTypes;

    @Column(name = "success_criteria", columnDefinition = "TEXT")
    private String successCriteria;

    @Column(name = "rag_query_hint", columnDefinition = "TEXT")
    private String ragQueryHint;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "course_link_policy", nullable = false, length = 30)
    private RoadmapTemplateCourseLinkPolicy courseLinkPolicy = RoadmapTemplateCourseLinkPolicy.AUTO_HYBRID;

    @Builder.Default
    @Column(name = "auto_course_limit", nullable = false)
    private Integer autoCourseLimit = 2;

    @Builder.Default
    @Column(name = "rag_enabled", nullable = false)
    private Boolean ragEnabled = true;

    @Builder.Default
    @OneToMany(mappedBy = "skillBlock", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<RoadmapTemplateActivity> activities = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
